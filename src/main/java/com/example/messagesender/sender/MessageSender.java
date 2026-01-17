package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;

// 채널별 발송 구현체의 공통 인터페이스 - Consumer/Worker 가 채널에 맞는 Sender 를 선택해서 호출한다.
// 성공/실패 결과만 반환하고, 로그/DB 업데이트/재시도 판단은 Worker 에서 처리한다.
public interface MessageSender {

  MessageChannel channel();

  SendResult send(SendRequest request);

  // 발송 요청 - 추후 추가 가능
  record SendRequest(Long messageSendResultId, String to, String content) {
  }

  // 발송 결과
  record SendResult(boolean success, String errorMessage) {
    public static SendResult ok() {
      return new SendResult(true, null);
    }

    public static SendResult fail(String errorMessage) {
      return new SendResult(false, errorMessage);
    }
  }
}
