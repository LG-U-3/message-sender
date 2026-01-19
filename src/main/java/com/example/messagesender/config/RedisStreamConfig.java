package com.example.messagesender.config;

import com.example.messagesender.consumer.MessageStreamConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

@Configuration
public class RedisStreamConfig {

  @Value("${redis.stream.message.key:message-stream}")
  private String streamKey;

  @Value("${redis.stream.message.group:message-group}")
  private String group;

  @Value("${redis.stream.message.poll-timeout-ms:500}")
  private long pollTimeoutMs;

  @Value("${redis.stream.message.batch-size:10}")
  private int batchSize;

  @Bean
  public StreamGroupInitializer streamGroupInitializer(StringRedisTemplate redisTemplate) {
    return new StreamGroupInitializer(redisTemplate, streamKey, group);
  }

  @Bean(destroyMethod = "stop")
  public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
      RedisConnectionFactory connectionFactory, MessageStreamConsumer consumer) {
    StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
        StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofMillis(pollTimeoutMs))
            .batchSize(batchSize)
            .build();

    StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
        StreamMessageListenerContainer.create(connectionFactory, options);
    String consumerName = consumer.consumerName();

    container.receive(Consumer.from(group, consumerName),
        StreamOffset.create(streamKey, ReadOffset.lastConsumed()), consumer);

    container.start();
    return container;
  }

  public static class StreamGroupInitializer {

    public StreamGroupInitializer(StringRedisTemplate redisTemplate, String streamKey,
        String group) {
      try {
        // group 생성(없으면 생성, 있으면 예외 발생 가능)
        redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), group);
      } catch (Exception ignored) {
        // 이미 존재하는 경우 등은 무시(멱등)
      }
    }
  }
}
