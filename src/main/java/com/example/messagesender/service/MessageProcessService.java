package com.example.messagesender.service;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.messagesender.common.code.CodeCache;
import com.example.messagesender.common.code.enums.CodeGroups;
import com.example.messagesender.common.code.enums.MessageChannel;
import com.example.messagesender.common.code.enums.MessageSendStatus;
import com.example.messagesender.domain.message.MessageSendResult;
import com.example.messagesender.domain.message.MessageTemplate;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.send.EmailSendRequest;
import com.example.messagesender.dto.send.SendRequest;
import com.example.messagesender.dto.send.SendResult;
import com.example.messagesender.dto.send.SmsSendRequest;
import com.example.messagesender.repository.BillingSettlementRepository;
import com.example.messagesender.repository.ChargedHistoryRepository;
import com.example.messagesender.repository.MessageSendResultRepository;
import com.example.messagesender.repository.MessageTemplateRepository;
import com.example.messagesender.repository.UserRepository;
import com.example.messagesender.sender.MessageSender;
import com.example.messagesender.service.sender.MessageSenderFactory;
import com.example.messagesender.service.template.MessageTemplateEngine;
import com.example.messagesender.service.template.RenderedMessage;
import com.example.messagesender.service.template.TemplateValueResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageProcessService {

  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageTemplateRepository messageTemplateRepository;
  private final MessageSenderFactory messageSenderFactory;

  private final CodeCache codeCache;
  private final ScheduledExecutorService emailDelayScheduler;

  // 비동기 스레드에서 DB 업데이트 트랜잭션 보장
  private final TransactionTemplate transactionTemplate;

  // 템플릿 치환에 필요한 데이터 소스들 (네 담당 영역)
  private final UserRepository userRepository;
  private final BillingSettlementRepository billingSettlementRepository;
  private final ObjectMapper objectMapper;
  private final ChargedHistoryRepository chargedHistoryRepository;

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
    this.STATUS_FAILED =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.FAILED);
  }

  public void process(MessageRequestDto dto) {

    MessageSendResult result = messageSendResultRepository.findById(dto.getMessageSendResultId())
        .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

    // 중복 방지 선점: WAITING/FAILED -> PROCESSING
    int updated = messageSendResultRepository.markProcessing(
        dto.getMessageSendResultId(),
        STATUS_PROCESSING,
        STATUS_WAITING,
        STATUS_FAILED
    );
    if (updated == 0) {
      return; // 이미 다른 워커가 처리 중이거나 처리 대상 아님
    }

    final Long messageId = dto.getMessageSendResultId();

    // 1) 템플릿 치환(네 담당 로직)
    RenderedMessage rendered;
    try {
      MessageTemplate template = messageTemplateRepository.findById(result.getTemplate().getId())
          .orElseThrow(() -> new IllegalArgumentException("템플릿 없음"));

      TemplateValueResolver resolver = new TemplateValueResolver(
          userRepository,
          billingSettlementRepository,
          objectMapper,
          chargedHistoryRepository
      );

      Map<String, String> values = resolver.resolve(result, template);

      MessageTemplateEngine engine = new MessageTemplateEngine();
      rendered = engine.render(template.getTitle(), template.getBody(), values);

    } catch (Exception e) {
      // 템플릿 구성/치환 단계에서 실패하면 즉시 FAILED 확정
      messageSendResultRepository.markFailed(messageId, STATUS_PROCESSING, STATUS_FAILED);
      return;
    }

    // 템플릿으로부터 최종 내용 구성
    final String content = rendered.getBody();


    MessageChannel channel = MessageChannel.valueOf(result.getChannel().getCode());
    MessageSender sender = messageSenderFactory.getSender(channel);

    SendRequest sendRequest;
    if (channel == MessageChannel.EMAIL) {
      // TODO: 수신자 이메일은 추후 result/user 기반으로 채우기
      sendRequest = new EmailSendRequest(messageId, "email@test.com", content);

      // Email은 “SUCCESS/FAILED 확정”을 반드시 1초 뒤에
      emailDelayScheduler.schedule(
          () -> finalizeAfterDelay(messageId, sender, sendRequest),
          1,
          TimeUnit.SECONDS
      );
      return; // worker 즉시 반환 (sleep 없음)
    }

    // SMS는 즉시 처리/확정
    // TODO: 수신자 번호는 추후 result/user 기반으로 채우기
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

    final boolean success = sendResult != null && sendResult.isSuccess();

    // 비동기 스레드 트랜잭션
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
