package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.send.EmailSendRequest;
import com.example.messagesender.dto.send.SendRequest;
import com.example.messagesender.dto.send.SendResult;
import com.example.messagesender.dto.send.SmsSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// SMS 발송 Mock - 100% 성공
@Slf4j
@Component
public class SmsSenderMock implements MessageSender {

  @Override
  public MessageChannel channel() {
    return MessageChannel.SMS;
  }

  @Override
  public SendResult mockSend(SendRequest request) {
    SmsSendRequest smsRequest = (SmsSendRequest) request;
    log.info(
        "[EMAIL] body='{}', to='{}'",
        smsRequest.getContent(),
        smsRequest.getPhone()
    );
    return this.send(request);
  }

  @Override
  public SendResult send(SendRequest request) {
    return SendResult.ok();
  }
}
