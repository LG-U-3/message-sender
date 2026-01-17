package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import org.springframework.stereotype.Component;

// SMS 발송 Mock - 100% 성공
@Component
public class SmsSenderMock implements MessageSender {

  @Override
  public MessageChannel channel() {
    return MessageChannel.SMS;
  }

  @Override
  public SendResult send(SendRequest request) {
    return SendResult.ok();
  }
}
