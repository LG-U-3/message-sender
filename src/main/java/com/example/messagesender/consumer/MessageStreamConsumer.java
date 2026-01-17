package com.example.messagesender.consumer;

import com.example.messagesender.config.RedisStreamConfig;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.service.MessageProcessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageStreamConsumer {

  private final MessageProcessService messageProcessService;
  private final RedisTemplate<String, Object> redisTemplate;

  public void onMessage(MapRecord<String, String, String> message) {

    try { // 전송 완료 정상 처리 (SUCCESS or FAILED) ACK
      Long id = Long.valueOf(message.getValue().get("messageSendResultId"));
      String channel = message.getValue().get("channel");
      String purpose = message.getValue().get("purpose");

      MessageRequestDto requestDto = new MessageRequestDto(id, channel, purpose);
      messageProcessService.process(requestDto);

      redisTemplate.opsForStream().acknowledge(
          message.getStream(),
          RedisStreamConfig.GROUP,
          message.getId()
      );

      System.out.println(
          "메세지 전송 처리 완료 messageSendResultID: " + requestDto.getMessageSendResultId());
    } catch (Exception e) { // 예외 발생 시 ACK 처리 하지 않고 PENDING 유지
      throw e;
    }

  }

  public String getName() {
    return UUID.randomUUID().toString();
  }
}

