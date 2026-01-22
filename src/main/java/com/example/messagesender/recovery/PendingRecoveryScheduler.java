package com.example.messagesender.recovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.messagesender.domain.message.MessageSendResult;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.repository.MessageSendResultRepository;
import com.example.messagesender.service.MessageProcessService;
import lombok.RequiredArgsConstructor;

/*
 * Redis Pending 복구 - consumer 장애/예외로 ACK 누락되어 PENDING에 남은 메시지를 회수 - XPENDING으로 오래된 pending(idle >=
 * timeout)을 찾고 - XCLAIM으로 recovery consumer가 소유권 회수 후 - DB 상태 기준으로 처리/ACK
 */
@Component
@RequiredArgsConstructor
public class PendingRecoveryScheduler {

  private static final Logger log = LoggerFactory.getLogger(PendingRecoveryScheduler.class);

  private final StringRedisTemplate redisTemplate;
  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageProcessService messageProcessService;

  @Value("${message.stream:message-stream}")
  private String streamKey;

  @Value("${message.group:message-group}")
  private String group;

  @Value("${message.recovery.timeout-minutes:60}")
  private long timeoutMinutes;

  @Value("${message.recovery.claim-count:100}")
  private int claimCount;

  // recovery용 consumer는 suffix로 구분
  @Value("${redis.stream.message.consumer:message-sender}")
  private String baseConsumer;

  private String recoveryConsumer() {
    return baseConsumer + "-recovery";
  }

  @Scheduled(fixedDelayString = "${message.recovery.fixed-delay-ms:300000}")
  public void recoverPending() {
    log.info("[SCHEDULER] pending recovery tick");
    Duration minIdle = Duration.ofMinutes(timeoutMinutes);

    // 1) pending 요약(없으면 빠르게 리턴)
    PendingMessagesSummary summary = redisTemplate.opsForStream().pending(streamKey, group);
    if (summary == null || summary.getTotalPendingMessages() == 0) {
      return;
    }


    // 2) pending 상세 일부 조회 (전체 범위에서 앞쪽 claimCount개)
    PendingMessages candidates =
        redisTemplate.opsForStream().pending(streamKey, group, Range.unbounded(), claimCount);

    if (candidates == null || candidates.size() == 0) {
      return;
    }

    // 3) idle이 timeout 이상인 것만 claim 대상 선정
    List<RecordId> toClaim = new ArrayList<>();
    for (PendingMessage pm : candidates) {
      try {
        if (pm.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0) {
          toClaim.add(pm.getId());
        }
      } catch (Exception ignore) {
        // Spring Data Redis 버전/드라이버 차이로 elapsedTime 접근이 흔들릴 수 있어 안전하게 스킵
      }
    }

    if (toClaim.isEmpty()) {
      return;
    }

    // 4) XCLAIM: recovery consumer로 소유권 회수
    List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(streamKey,
        group, recoveryConsumer(), minIdle, toClaim.toArray(new RecordId[0]));

    if (claimed == null || claimed.isEmpty()) {
      return;
    }

    log.warn("[RECOVERY] claimed pending. count={}, consumer={}", claimed.size(),
        recoveryConsumer());

    // 5) 회수 메시지 처리/ACK
    for (MapRecord<String, Object, Object> record : claimed) {
      RecordId recordId = record.getId();
      Map<Object, Object> payload = record.getValue();

      try {
        Long msrId = parseLong(payload.get("messageSendResultId"));
        String channel = toStr(payload.get("channel"));
        String purpose = toStr(payload.get("purpose"));

        if (msrId == null) {
          // payload가 깨졌으면 ACK해서 스트림 정리
          ack(recordId);
          log.warn("[RECOVERY] invalid payload(no msrId) -> ACK. recordId={}", recordId.getValue());
          continue;
        }

        MessageSendResult msr = messageSendResultRepository.findById(msrId).orElse(null);
        if (msr == null) {
          // DB에 없으면 유령 메시지 → ACK로 제거
          ack(recordId);
          log.warn("[RECOVERY] msr not found -> ACK only. msrId={}, recordId={}", msrId,
              recordId.getValue());
          continue;
        }

        String statusCode = msr.getStatus().getCode(); // codes.code 값

        // (1) 이미 확정 상태면 ACK만
        if (isFinalStatus(statusCode)) {
          ack(recordId);
          log.info("[RECOVERY] already finalized({}) -> ACK. msrId={}, recordId={}", statusCode,
              msrId, recordId.getValue());
          continue;
        }

        // (2) WAITING이면 재처리 후 ACK
        if ("WAITING".equals(statusCode)) {
          MessageRequestDto dto = new MessageRequestDto(msrId, channel, purpose);
          messageProcessService.process(dto);

          // A안 구조상 보통 예외는 내부에서 처리됨
          ack(recordId);
          log.info("[RECOVERY] WAITING -> processed + ACK. msrId={}, recordId={}", msrId,
              recordId.getValue());
          continue;
        }

        // (3) PROCESSING이면 박제/중간장애 가능성 → FAILED 확정 후 ACK
        if ("PROCESSING".equals(statusCode)) {
          messageProcessService.markFailed(msrId,
              new IllegalStateException("RECOVERY_TIMEOUT_PROCESSING"));

          ack(recordId);
          log.warn("[RECOVERY] PROCESSING -> FAILED + ACK. msrId={}, recordId={}", msrId,
              recordId.getValue());
          continue;
        }

        // (4)우선 ACK만
        ack(recordId);
        log.warn("[RECOVERY] unknown status({}) -> ACK only. msrId={}, recordId={}", statusCode,
            msrId, recordId.getValue());

      } catch (Exception e) {
        // 여기서 예외면 ACK 안 함 → 다음 recovery 주기에 재시도 가능
        log.error("[RECOVERY] failed to handle claimed record. recordId={}", recordId.getValue(),
            e);
      }
    }
  }

  private boolean isFinalStatus(String statusCode) {
    return "SUCCESS".equals(statusCode) || "FAILED".equals(statusCode)
        || "EXCEEDED".equals(statusCode);
  }

  private void ack(RecordId id) {
    redisTemplate.opsForStream().acknowledge(streamKey, group, id);
  }

  private static Long parseLong(Object v) {
    if (v == null)
      return null;
    try {
      return Long.valueOf(String.valueOf(v));
    } catch (Exception e) {
      return null;
    }
  }

  private static String toStr(Object v) {
    return v == null ? null : String.valueOf(v);
  }
}
