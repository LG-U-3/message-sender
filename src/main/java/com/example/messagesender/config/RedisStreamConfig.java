package com.example.messagesender.config;

import com.example.messagesender.consumer.MessageStreamConsumer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

  public static final String STREAM = "message-stream";
  public static final String GROUP = "message-group";

  @Bean
  public RedisTemplate<String, Object> redisTemplate(
      RedisConnectionFactory connectionFactory
  ) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    return template;
  }

  @Bean
  public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
  messageListenerContainer(
      RedisConnectionFactory factory,
      MessageStreamConsumer consumer
  ) {

    StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
        StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofSeconds(5))
            .build();

    StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
        StreamMessageListenerContainer.create(factory, options);

    container.receive(
        Consumer.from(GROUP, consumer.getName()),
        StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
        consumer::onMessage
    );

    container.start();
    return container;
  }
}
