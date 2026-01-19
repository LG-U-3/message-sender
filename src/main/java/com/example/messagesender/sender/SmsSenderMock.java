package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.MessageSendResultDto;
import org.springframework.stereotype.Component;

// SMS 발송 Mock - 100% 성공
@Component
public class SmsSenderMock implements MessageSender {

  @Override
  public MessageChannel channel() {
    return MessageChannel.SMS;
  }

  @Override
  public MessageSendResultDto mockSend(MessageRequestDto request) {
    // TODO: nextAttemptAt 1초 후 예약(성범)
    return this.send(request);
  }

  @Override
  public MessageSendResultDto send(MessageRequestDto request) {
    return new MessageSendResultDto(true);
  }
}
