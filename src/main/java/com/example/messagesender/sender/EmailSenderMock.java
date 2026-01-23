package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.send.EmailSendRequest;
import com.example.messagesender.dto.send.SendRequest;
import com.example.messagesender.dto.send.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class EmailSenderMock implements MessageSender {

  @Override
  public MessageChannel channel() {
    return MessageChannel.EMAIL;
  }

  @Override
  public SendResult mockSend(SendRequest request) {
    EmailSendRequest emailRequest = (EmailSendRequest) request;
    log.info(
        "[EMAIL] title='{}', body='{}', to='{}'",
        emailRequest.getTitle(),
        emailRequest.getContent(),
        emailRequest.getEmail()
    );
    return this.send(request);
  }

  @Override
  public SendResult send(SendRequest request) {
    // 0~99 중 0~98 성공(99%), 99 실패(1%)
    int r = ThreadLocalRandom.current().nextInt(100);

    if (r < 99) {
      return SendResult.ok();
    }
    return SendResult.fail("EMAIL_SEND_FAILED");
  }
}
