package com.example.messagesender.sender;

import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.MessageSendResultDto;

// 채널별 발송 구현체의 공통 인터페이스 - Consumer/Worker 가 채널에 맞는 Sender 를 선택해서 호출한다.
// 성공/실패 결과만 반환하고, 로그/DB 업데이트/재시도 판단은 Worker 에서 처리한다.
public interface MessageSender {

  MessageChannel channel();

  MessageSendResultDto mockSend(MessageRequestDto request);

  MessageSendResultDto send(MessageRequestDto request);
}
