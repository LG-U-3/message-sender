package com.example.messagesender.service;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(MessageProcessService.class);

  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageTemplateRepository messageTemplateRepository;
  private final MessageSenderFactory messageSenderFactory;

  private final CodeCache codeCache;
  private final ScheduledExecutorService emailDelayScheduler;
  private final TransactionTemplate transactionTemplate;

  private final UserRepository userRepository;
  private final BillingSettlementRepository billingSettlementRepository;
  private final ObjectMapper objectMapper;
  private final ChargedHistoryRepository chargedHistoryRepository;

  private Long STATUS_PROCESSING;
  private Long STATUS_WAITING;
  private Long STATUS_SUCCESS;
  private Long STATUS_FAILED;

  /**
   * 메시지 상태 코드 ID 초기화
   */
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

  /**
   * Redis Stream으로 전달된 메시지 처리 진입점
   */
  public void process(MessageRequestDto dto) {

    final Long messageId = dto.getMessageSendResultId();
    log.info("[STREAM] messageSendResultId={}", messageId);

    MessageSendResult result = messageSendResultRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

    // 처리 대상 선점 (중복 처리 방지)
    int updated = messageSendResultRepository.markProcessing(messageId, STATUS_PROCESSING,
        STATUS_WAITING, STATUS_FAILED);

    if (updated == 0) {
      log.info("[SKIP] already processed or not eligible. id={}", messageId);
      return;
    }

    // 템플릿 치환
    RenderedMessage rendered;
    try {
      MessageTemplate template = messageTemplateRepository.findById(result.getTemplate().getId())
          .orElseThrow(() -> new IllegalArgumentException("템플릿 없음"));

      TemplateValueResolver resolver = new TemplateValueResolver(userRepository,
          billingSettlementRepository, objectMapper, chargedHistoryRepository);

      Map<String, String> values = resolver.resolve(result, template);
      // values 전체 로
      log.info("[VALUES] messageId={} templateId={}", messageId, template.getId());
      values.forEach((k, v) -> log.info("  - {} = {}", k, v));
      
      MessageTemplateEngine engine = new MessageTemplateEngine();
      rendered = engine.render(template.getTitle(), template.getBody(), values);

      log.info("[RENDERED] title='{}'", rendered.getTitle());
      log.info("[RENDERED] body='{}'", rendered.getBody());

    } catch (Exception e) {
      messageSendResultRepository.markFailed(messageId, STATUS_PROCESSING, STATUS_FAILED);
      log.error("[FAIL] template rendering failed. id={}", messageId, e);
      return;
    }

    // 채널별 발송 처리
    MessageChannel channel = MessageChannel.valueOf(result.getChannel().getCode());
    MessageSender sender = messageSenderFactory.getSender(channel);

    if (channel == MessageChannel.EMAIL) {
      sendEmailWithDelay(messageId, sender, rendered.getBody());
      return;
    }

    sendSmsImmediately(messageId, sender, rendered.getBody());
  }

  /**
   * EMAIL 발송 (지연 후 성공/실패 확정)
   */
  private void sendEmailWithDelay(Long messageId, MessageSender sender, String content) {
    SendRequest request = new EmailSendRequest(messageId, "email@test.com", content);

    emailDelayScheduler.schedule(() -> finalizeAfterDelay(messageId, sender, request), 1,
        TimeUnit.SECONDS);
  }

  /**
   * SMS 즉시 발송 및 결과 확정
   */
  private void sendSmsImmediately(Long messageId, MessageSender sender, String content) {
    SendRequest request = new SmsSendRequest(messageId, "010-1234-1234", content);

    SendResult sendResult;
    try {
      sendResult = sender.mockSend(request);
    } catch (Exception e) {
      sendResult = SendResult.fail("SENDER_EXCEPTION");
    }

    finalizeNow(messageId, sendResult);
  }

  /**
   * EMAIL 지연 발송 후 상태 확정
   */
  private void finalizeAfterDelay(Long messageId, MessageSender sender, SendRequest request) {
    SendResult sendResult;
    try {
      sendResult = sender.mockSend(request);
    } catch (Exception e) {
      sendResult = SendResult.fail("SENDER_EXCEPTION");
    }

    boolean success = sendResult != null && sendResult.isSuccess();

    transactionTemplate.execute(status -> {
      if (success) {
        messageSendResultRepository.markSuccess(messageId, STATUS_PROCESSING, STATUS_SUCCESS);
        log.info("[FINAL] EMAIL SUCCESS id={}", messageId);
      } else {
        messageSendResultRepository.markFailed(messageId, STATUS_PROCESSING, STATUS_FAILED);
        log.info("[FINAL] EMAIL FAILED id={}", messageId);
      }
      return null;
    });
  }

  /**
   * SMS 발송 결과 즉시 확정
   */
  private void finalizeNow(Long messageId, SendResult sendResult) {
    boolean success = sendResult != null && sendResult.isSuccess();

    if (success) {
      messageSendResultRepository.markSuccess(messageId, STATUS_PROCESSING, STATUS_SUCCESS);
      log.info("[FINAL] SMS SUCCESS id={}", messageId);
    } else {
      messageSendResultRepository.markFailed(messageId, STATUS_PROCESSING, STATUS_FAILED);
      log.info("[FINAL] SMS FAILED id={}", messageId);
    }
  }
}
