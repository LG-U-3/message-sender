package com.example.messagesender.service.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.domain.code.Code;
import com.example.messagesender.sender.MessageSender;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MessageSenderFactory {

  private final Map<MessageChannel, MessageSender> senderMap;

  public MessageSenderFactory(List<MessageSender> senders) {
    this.senderMap = senders.stream()
        .collect(Collectors.toMap(
            MessageSender::channel,
            Function.identity()
        ));
  }

  public MessageSender getSender(MessageChannel channel) {
    MessageSender sender = senderMap.get(channel);
    if (sender == null) {
      throw new IllegalArgumentException("Unsupported channel: " + channel);
    }
    return sender;
  }
}
