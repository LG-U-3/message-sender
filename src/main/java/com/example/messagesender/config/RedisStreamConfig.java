package com.example.messagesender.config;

import com.example.messagesender.consumer.MessageStreamConsumer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

  @Value("${redis.stream.message.key}")
  private String streamKey;

  @Value("${redis.stream.message.group}")
  private String group;

  @Value("${redis.stream.message.consumer}")
  private String consumerName;

  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }

  @Bean
  public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
  messageListenerContainer(
      RedisConnectionFactory factory,
      MessageStreamConsumer consumer
  ) {

    StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
        StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofMillis(50))
            .batchSize(10)
            .build();

    StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
        StreamMessageListenerContainer.create(factory, options);

    container.receive(
        Consumer.from(group, consumerName),
        StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
        consumer::onMessage
    );

    container.start();
    return container;
  }
}
