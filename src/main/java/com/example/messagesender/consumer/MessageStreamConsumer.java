package com.example.messagesender.consumer;

import com.example.messagesender.worker.WorkerRunnable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Component
@RequiredArgsConstructor
public class MessageStreamConsumer
    implements StreamListener<String, MapRecord<String, String, String>> {

  @Value("${message.stream:message-stream}")
  private String streamKey;

  @Value("${message.group:message-group}")
  private String group;

  private final ExecutorService workerExecutorService;
  private final StringRedisTemplate redisTemplate;

  // 인스턴스별 유니크한 consumer name (스케일아웃 필수)
  public String consumerName() {
    String host = "unknown";
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception ignored) {
    }
    return "message-sender-" + host + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Override
  public void onMessage(MapRecord<String, String, String> message) {
    String recordId = message.getId().getValue();
    Map<String, String> body = message.getValue();

    workerExecutorService
        .submit(new WorkerRunnable(streamKey, group, recordId, body, redisTemplate));
                // 이 부분만 서비스 쪽으로 넘기면 될 것 같습니다.
  }
}
