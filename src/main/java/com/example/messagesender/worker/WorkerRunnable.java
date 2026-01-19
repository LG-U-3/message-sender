package com.example.messagesender.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

public class WorkerRunnable implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(WorkerRunnable.class);

  private final String streamKey;
  private final String group;
  private final String recordId;
  private final Map<String, String> body;
  private final StringRedisTemplate redisTemplate;

  public WorkerRunnable(String streamKey, String group, String recordId, Map<String, String> body,
      StringRedisTemplate redisTemplate) {
    this.streamKey = streamKey;
    this.group = group;
    this.recordId = recordId;
    this.body = body;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void run() {
    try {
      // 여기서 정책 처리
      // - message_send_result_id 조회(DB)
      // - 채널별 sender 호출(EMAIL/SMS)
      // - retry/fallback 판단
      // - 상태 업데이트 후 XACK
      //
      // 주의: 성공 건별 로그는 금지(집계 로거로 따로)
      // 실패만 상세 로그

      // 일단 골격: payload만 안전하게 읽고 처리 완료로 간주
      // String sendResultId = body.get("message_send_result_id");
      ack();

    } catch (Exception e) {
      // 실패 상세 로그(확정 정책)
      log.error("[SEND_FAIL] streamId={}, payload={}, error={}", recordId, body, e.toString(), e);

      // 여기서는 안전하게 ack 처리(무한 pending 방지)
      try {
        ack();
      } catch (Exception ackEx) {
        log.error("[ACK_FAIL] streamId={}, error={}", recordId, ackEx.toString(), ackEx);
      }
    }
  }

  private void ack() {
    // XACK <stream> <group> <id>
    redisTemplate.opsForStream().acknowledge(streamKey, group, RecordId.of(recordId));
  }
}
