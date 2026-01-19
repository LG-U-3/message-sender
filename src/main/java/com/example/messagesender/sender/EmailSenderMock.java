package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.send.SendRequest;
import com.example.messagesender.dto.send.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

// 1초 delay 는 Worker 에서 nextAttemptAt 으로 제어 예정
// 이 부분이 sender 가 아니라 service 에서 처리하는게 더 나을 것 같습니다
// 역할도 역할이지만 sender 를 병렬처리하는 것보다 service 를 병렬처리하는게 더 낫다고 생각합니다.
@Component
public class EmailSenderMock implements MessageSender {

  @Override
  public MessageChannel channel() {
    return MessageChannel.EMAIL;
  }

  @Override
  public SendResult mockSend(SendRequest request) {
    // TODO: nextAttemptAt 1초 후 예약(성범)
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
