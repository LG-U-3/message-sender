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
import com.example.messagesender.common.code.enums.MessagePurpose;
import com.example.messagesender.common.code.enums.MessageSendStatus;
import com.example.messagesender.domain.message.MessageSendResult;
import com.example.messagesender.domain.message.MessageTemplate;
import com.example.messagesender.domain.user.User;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.dto.send.EmailSendRequest;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MessageProcessService {

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
  private Long STATUS_EXCEEDED;

  private Long CHANNEL_SMS_ID;
  private Long PURPOSE_BILLING_ID;

  // EMAIL은 retry_count 0/1/2 까지 (최초 + 재시도1 + 재시도2)
  private static final int MAX_EMAIL_RETRY_COUNT = 2;

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
    this.STATUS_FAILED =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.FAILED);
    this.STATUS_EXCEEDED =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.EXCEEDED);

    this.CHANNEL_SMS_ID = codeCache.getId(CodeGroups.MESSAGE_CHANNEL, MessageChannel.SMS);
    this.PURPOSE_BILLING_ID = codeCache.getId(CodeGroups.MESSAGE_PURPOSE, MessagePurpose.BILLING);
  }

  /**
   * Redis Stream으로 전달된 메시지 처리 진입점
   */
  public void process(MessageRequestDto dto) {

    Long messageId = dto.getMessageSendResultId();

    MessageSendResult result = messageSendResultRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("메시지 없음: id=" + messageId));

    if (result.getStatus().getId().equals(STATUS_WAITING)) {
      // WAITING선점
      int updated = messageSendResultRepository.markProcessing(messageId, STATUS_PROCESSING,
          STATUS_WAITING);
      if (updated == 0) {
        return;
      }
    } else if (result.getStatus().getId().equals(STATUS_FAILED)) {
      // FAILED 재시도 선점 (retryCount + 1, processedAt=null)
      // 예약발송 템플릿 PURPOSE가 BILLING이 아닌 경우 제외
      int updated = messageSendResultRepository.markRetryProcessing(messageId, STATUS_PROCESSING,
          STATUS_FAILED, MAX_EMAIL_RETRY_COUNT, PURPOSE_BILLING_ID);
      if (updated == 0) {
        return;
      }
    } else if (result.getStatus().getId().equals(STATUS_EXCEEDED)) {
      // EXCEEDED SMS fallback 선점 (retryCount 그대로, channel=SMS로 기록)
      // 예약발송 템플릿 PURPOSE가 BILLING이 아닌 경우 제외
      int updated = messageSendResultRepository.markExceededProcessing(messageId, STATUS_PROCESSING,
          STATUS_EXCEEDED, CHANNEL_SMS_ID, PURPOSE_BILLING_ID);
      if (updated == 0) {
        return;
      }
    } else {
      return;
    }

    User user = userRepository.findById(result.getUserId())
        .orElseThrow(
            () -> new IllegalArgumentException("사용자 정보 없음: user_id=" + result.getUserId()));

    // 템플릿 치환 TODO: 템플릿 PURPOSE_TYPE=BILLING인 경우 정산서 처리, 아닌 경우 일반 템플릿 처리
    RenderedMessage rendered;
    try {
      MessageTemplate template = messageTemplateRepository.findById(result.getTemplate().getId())
          .orElseThrow(() -> new IllegalArgumentException("템플릿 없음"));

      String purposeCode = template.getPurposeType().getCode(); // 예: BILLING / NOTICE
      MessagePurpose purpose = MessagePurpose.valueOf(purposeCode); // enum 필요

      TemplateValueResolver resolver = new TemplateValueResolver(
          userRepository, billingSettlementRepository, objectMapper, chargedHistoryRepository
      );

      Map<String, String> values = resolver.resolve(result, template, purpose);

      MessageTemplateEngine engine = new MessageTemplateEngine();
      rendered = engine.render(template.getTitle(), template.getBody(), values);
    } catch (Exception e) {
      log.error("[FAIL] template rendering failed. id={}", messageId, e);
      throw e;
    }

    // 채널별 발송 처리
    MessageChannel channel = MessageChannel.valueOf(result.getChannel().getCode());
    MessageSender sender = messageSenderFactory.getSender(channel);

    if (channel == MessageChannel.EMAIL) {
      String title = rendered.getTitle();
      String content = rendered.getBody();
      String email = user.getEmail();
      EmailSendRequest req = new EmailSendRequest(messageId, title, content, email);

      // Email은 1초 후 확정
      emailDelayScheduler.schedule(() -> finalizeAfterDelay(messageId, sender, req), 1,
          TimeUnit.SECONDS);
      return;
    }

    // SMS는 즉시 처리
    String content = rendered.getBody();
    String phone = user.getPhone();
    SmsSendRequest req = new SmsSendRequest(messageId, content, phone);

    finalizeNow(messageId, sender, req);
  }

  /**
   * EMAIL 지연 발송 후 상태 확정
   */
  private void finalizeAfterDelay(Long messageId, MessageSender sender, EmailSendRequest request) {
    SendResult sendResult;
    try {
      sendResult = sender.mockSend(request);
    } catch (Exception e) {
      sendResult = SendResult.fail("SENDER_EXCEPTION");
    }

    boolean success = sendResult != null && sendResult.isSuccess();

    transactionTemplate.execute(tx -> {
      if (success) {
        messageSendResultRepository.markSuccess(messageId, STATUS_PROCESSING, STATUS_SUCCESS);
      } else {
        messageSendResultRepository.markFailedOrExceeded(messageId, STATUS_PROCESSING,
            STATUS_FAILED, STATUS_EXCEEDED, MAX_EMAIL_RETRY_COUNT);
      }
      return null;
    });
  }

  /**
   * SMS 발송 결과 즉시 확정
   */
  private void finalizeNow(Long messageId, MessageSender sender, SmsSendRequest req) {
    SendResult sendResult;
    try {
      sendResult = sender.mockSend(req);
    } catch (Exception e) {
      sendResult = SendResult.ok();
    }
    boolean success = sendResult != null && sendResult.isSuccess();

    if (success) {
      messageSendResultRepository.markSuccess(messageId, STATUS_PROCESSING, STATUS_SUCCESS);
    } else {
      messageSendResultRepository.markFailedOrExceeded(messageId, STATUS_PROCESSING, STATUS_FAILED,
          STATUS_EXCEEDED, MAX_EMAIL_RETRY_COUNT);
    }
  }
}
