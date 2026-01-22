package com.example.messagesender.consumer;

import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.service.MessageProcessService;
import com.example.messagesender.worker.WorkerRunnable;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
public class MessageStreamConsumer
    implements StreamListener<String, MapRecord<String, String, String>> {

  private final ExecutorService workerExecutorService;
  private final MessageProcessService messageProcessService;
  private final StringRedisTemplate redisTemplate;

  @Value("${redis.stream.message.key:message-stream}")
  private String streamKey;

  @Value("${redis.stream.message.group:message-group}")
  private String group;

  @Value("${redis.stream.message.consumer}")
  private String consumerName;

  @Override
  public void onMessage(MapRecord<String, String, String> message) {
    Long id = Long.valueOf(message.getValue().get("messageSendResultId"));
    String channel = message.getValue().get("channel");
    String purpose = message.getValue().get("purpose");
    MessageRequestDto requestDto = new MessageRequestDto(id, channel, purpose);

    RecordId messageId = message.getId();

    workerExecutorService.submit(new WorkerRunnable(messageProcessService, streamKey, group,
        messageId, requestDto, redisTemplate));
  }

  public String consumerName() {
    return consumerName;
  }
}
