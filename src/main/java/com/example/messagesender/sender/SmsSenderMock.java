package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.send.SendRequest;
import com.example.messagesender.dto.send.SendResult;
import org.springframework.stereotype.Component;

// SMS 발송 Mock - 100% 성공
@Component
public class SmsSenderMock implements MessageSender {

  @Override
  public MessageChannel channel() {
    return MessageChannel.SMS;
  }

  @Override
  public SendResult mockSend(SendRequest request) {
    // TODO: nextAttemptAt 1초 후 예약(성범)
    return this.send(request);
  }

  @Override
  public SendResult send(SendRequest request) {
    return SendResult.ok();
  }
}
