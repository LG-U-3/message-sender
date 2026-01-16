package com.example.messagesender.consumer;

import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.service.MessageProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageStreamConsumer {

  private final MessageProcessService messageProcessService;

  @StreamListener("message-stream")
  public void onMessage(MessageRequestDto dto) {
    messageProcessService.process(dto);
  }
}
