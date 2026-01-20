package com.example.messagesender.service;

import com.example.messagesender.common.code.CodeCache;
import com.example.messagesender.common.code.enums.CodeGroups;
import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.common.code.enums.MessageSendStatus;
import com.example.messagesender.domain.message.MessageSendResult;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.send.EmailSendRequest;
import com.example.messagesender.dto.send.SendRequest;
import com.example.messagesender.dto.send.SendResult;
import com.example.messagesender.dto.send.SmsSendRequest;
import com.example.messagesender.repository.MessageSendResultRepository;
import com.example.messagesender.repository.MessageTemplateRepository;
import com.example.messagesender.sender.MessageSender;
import com.example.messagesender.service.sender.MessageSenderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageProcessService {

  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageTemplateRepository messageTemplateRepository;
  private final MessageSenderFactory messageSenderFactory;
  private final CodeCache codeCache;

  // Email 1초 뒤 확정용 타이머 풀 (ConsumerConfig에서 Bean으로 제공)
  private final ScheduledExecutorService emailDelayScheduler;

  // 비동기 스레드에서 DB 업데이트 트랜잭션 보장
  private final TransactionTemplate transactionTemplate;

  private Long STATUS_PROCESSING;
  private Long STATUS_WAITING;
  private Long STATUS_SUCCESS;
  private Long STATUS_FAILED;

  @EventListener(ApplicationReadyEvent.class)
  void initStatuses() {
    this.STATUS_PROCESSING =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.PROCESSING);
    this.STATUS_WAITING =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.WAITING);
    this.STATUS_SUCCESS =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.SUCCESS);
    this.STATUS_FAILED = codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.FAILED);
  }

  public void process(MessageRequestDto dto) {

    MessageSendResult result = messageSendResultRepository.findById(dto.getMessageSendResultId())
        .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

    // WAITING/FAILED인 경우에만 PROCESSING 선점(중복 방지)
    int updated = messageSendResultRepository.markProcessing(dto.getMessageSendResultId(),
        STATUS_PROCESSING, STATUS_WAITING, STATUS_FAILED);

    if (updated == 0) {
      return; // 이미 다른 워커가 처리중이거나 완료됨
    }

    // TODO: 템플릿 추후 구성 지금은 "1초 뒤 확정" 구조가 최우선
    String content = "생성된 템플릿...";
    Long messageId = dto.getMessageSendResultId();

    // 채널별 sender 선택
    MessageChannel channel = MessageChannel.valueOf(result.getChannel().getCode());
    MessageSender sender = messageSenderFactory.getSender(channel);

    // 채널별 요청 DTO
    SendRequest sendRequest;
    if (channel == MessageChannel.EMAIL) {
      sendRequest = new EmailSendRequest(messageId, "email@test.com", content);

      // 핵심: SUCCESS/FAILED 확정을 “반드시 1초 뒤”에 하도록 비동기 예약
      emailDelayScheduler.schedule(() -> finalizeAfterDelay(messageId, sender, sendRequest), 1,
          TimeUnit.SECONDS);

      return; // worker는 즉시 반환 (sleep 없이 ACK 진행)
    }

    // SMS 즉시 처리
    sendRequest = new SmsSendRequest(messageId, "010-1234-1234", content);
    SendResult sendResult;
    try {
      sendResult = sender.mockSend(sendRequest);
    } catch (Exception e) {
      sendResult = SendResult.fail("SENDER_EXCEPTION:" + e.getClass().getSimpleName());
    }
    finalizeNow(messageId, sendResult);
  }

  private void finalizeAfterDelay(Long messageId, MessageSender sender, SendRequest request) {
    SendResult sendResult;
    try {
      sendResult = sender.mockSend(request);
    } catch (Exception e) {
      sendResult = SendResult.fail("SENDER_EXCEPTION:" + e.getClass().getSimpleName());
    }

    // 람다에서 쓸 final 값
    final boolean success = sendResult.isSuccess();

    // 비동기 스레드에서 DB 업데이트는 트랜잭션으로 감싸서 처리
    transactionTemplate.execute(status -> {
      if (success) {
        messageSendResultRepository.markSuccess(messageId, STATUS_PROCESSING, STATUS_SUCCESS);
      } else {
        messageSendResultRepository.markFailed(messageId, STATUS_PROCESSING, STATUS_FAILED);
      }
      return null;
    });
  }

  private void finalizeNow(Long messageId, SendResult sendResult) {
    final boolean success = sendResult != null && sendResult.isSuccess();

    if (success) {
      messageSendResultRepository.markSuccess(messageId, STATUS_PROCESSING, STATUS_SUCCESS);
    } else {
      messageSendResultRepository.markFailed(messageId, STATUS_PROCESSING, STATUS_FAILED);
    }
  }
}
