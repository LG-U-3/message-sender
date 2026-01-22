package com.example.messagesender.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.service.MessageProcessService;

public class WorkerRunnable implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(WorkerRunnable.class);

  private final MessageProcessService messageProcessService;
  private final String streamKey;
  private final String group;
  private final RecordId messageId;
  private final MessageRequestDto request;
  private final StringRedisTemplate redisTemplate;

  public WorkerRunnable(MessageProcessService messageProcessService, String streamKey, String group,
      RecordId messageId, MessageRequestDto request, StringRedisTemplate redisTemplate) {

    this.messageProcessService = messageProcessService;
    this.streamKey = streamKey;
    this.group = group;
    this.messageId = messageId;
    this.request = request;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void run() {

    Long msrId = request.getMessageSendResultId();

    try {
      messageProcessService.process(request);

      // → pending 유지
      // Long acked = ack();
      // log.info("메시지 처리 완료 -> ACK. messageSendResultId={}, acked={}", msrId, acked);

      log.info("메시지 처리 완료(ACK 주석처리 -> pending 유지). messageSendResultId={}", msrId);

    } catch (Exception e) {

      try {
        messageProcessService.markFailed(msrId, e);

        // → pending 유지
        // Long acked = ack();
        // log.error("메시지 처리 중 예외(최후 처리) -> FAILED 업데이트 후 ACK. messageSendResultId={}, acked={}",
        // msrId, acked, e);

        log.error("메시지 처리 중 예외 -> FAILED 업데이트(ACK 주석처리 -> pending 유지). messageSendResultId={}",
            msrId, e);

      } catch (Exception fatal) {
        // 여기서 실패하면 ACK 미수행 → pending 유지(추후 recovery에서 회수 대상)
        log.error("FAIL-HANDLER 실패(ACK 미수행, pending 유지). messageSendResultId={}", msrId, fatal);
      }
    }
  }
}
