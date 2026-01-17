package com.example.messagesender.consumer;

import com.example.messagesender.config.RedisStreamConfig;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.service.MessageProcessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStreamConsumer {

  private final MessageProcessService messageProcessService;
  private final StringRedisTemplate stringRedisTemplate;

  @Value("${redis.stream.message.key}")
  private String streamKey;

  @Value("${redis.stream.message.group}")
  private String group;

  @Value("${redis.stream.message.consumer}")
  private String consumerName;

  public void onMessage(MapRecord<String, String, String> message) {

    try { // 전송 완료 정상 처리 (SUCCESS or FAILED) ACK
      Long id = Long.valueOf(message.getValue().get("messageSendResultId"));
      String channel = message.getValue().get("channel");
      String purpose = message.getValue().get("purpose");

      MessageRequestDto requestDto = new MessageRequestDto(id, channel, purpose);
      messageProcessService.process(requestDto);

      Long acked = stringRedisTemplate.opsForStream().acknowledge(
          streamKey,
          group,
          message.getId()
      );
      log.info("ACK result = {}, messageId={}", acked, message.getId());

      System.out.println(
          "메세지 전송 처리 완료 messageSendResultID: " + requestDto.getMessageSendResultId());
    } catch (Exception e) { // 예외 발생 시 ACK 처리 하지 않고 PENDING 유지
      log.error("메시지 처리 실패. pending 유지", e);
    }
  }
}

