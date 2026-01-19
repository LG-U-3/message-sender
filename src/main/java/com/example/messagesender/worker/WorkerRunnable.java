package com.example.messagesender.worker;

import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.service.MessageProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

public class WorkerRunnable implements Runnable {

  private final MessageProcessService messageProcessService;

  private static final Logger log = LoggerFactory.getLogger(WorkerRunnable.class);

  private final String streamKey;
  private final String group;
  private final RecordId messageId;
  private final MessageRequestDto request;
  private final StringRedisTemplate redisTemplate;

  public WorkerRunnable(
      MessageProcessService messageProcessService,
      String streamKey,
      String group,
      RecordId messageId,
      MessageRequestDto request,
      StringRedisTemplate redisTemplate) {
    this.messageProcessService = messageProcessService;
    this.streamKey = streamKey;
    this.group = group;
    this.messageId = messageId;
    this.request = request;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void run() {
    try {
      messageProcessService.process(request);

      ack();
    } catch (Exception e) { // 예외 발생 시 ACK 처리 하지 않고 PENDING 유지
      log.error("메시지 처리 실패. pending 유지", e);
    }
  }

  private Long ack() {
    Long acked = redisTemplate.opsForStream().acknowledge(
        streamKey,
        group,
        messageId
    );
    return acked;
  }
}
