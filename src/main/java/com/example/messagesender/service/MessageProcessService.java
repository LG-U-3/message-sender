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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MessageProcessService {

  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageTemplateRepository messageTemplateRepository; // 현재 미사용
  private final MessageSenderFactory messageSenderFactory;
  private final CodeCache codeCache;
  private final ScheduledExecutorService emailDelayScheduler;
  private final TransactionTemplate transactionTemplate;

  private Long STATUS_PROCESSING;
  private Long STATUS_WAITING;
  private Long STATUS_SUCCESS;
  private Long STATUS_FAILED;
  private Long STATUS_EXCEEDED;

  private Long CHANNEL_SMS_ID;

  // EMAIL은 retry_count 0/1/2 까지 (최초 + 재시도1 + 재시도2)
  private static final int MAX_EMAIL_RETRY_COUNT = 2;

  @EventListener(ApplicationReadyEvent.class)
  void initStatuses() {
    this.STATUS_PROCESSING =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.PROCESSING);
    this.STATUS_WAITING =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.WAITING);
    this.STATUS_SUCCESS =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.SUCCESS);
    this.STATUS_FAILED = codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.FAILED);
    this.STATUS_EXCEEDED =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.EXCEEDED);

    this.CHANNEL_SMS_ID = codeCache.getId(CodeGroups.MESSAGE_CHANNEL, MessageChannel.SMS);
  }

  public void process(MessageRequestDto dto) {

    Long messageId = dto.getMessageSendResultId();

    // WAITING/FAILED(processedAt null) 선점
    int updated = messageSendResultRepository.markProcessing(messageId, STATUS_PROCESSING,
        STATUS_WAITING, STATUS_FAILED);

    // FAILED 재시도 선점 (retryCount + 1, processedAt=null)
    if (updated == 0) {
      updated = messageSendResultRepository.markRetryProcessing(messageId, STATUS_PROCESSING,
          STATUS_FAILED, MAX_EMAIL_RETRY_COUNT);
    }

    // EXCEEDED SMS fallback 선점 (retryCount 그대로, channel=SMS로 기록)
    if (updated == 0) {
      updated = messageSendResultRepository.markExceededProcessing(messageId, STATUS_PROCESSING,
          STATUS_EXCEEDED, CHANNEL_SMS_ID);
    }

    if (updated == 0) {
      return;
    }

    MessageSendResult result = messageSendResultRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("메시지 없음: id=" + messageId));

    // TODO 실제 템플릿/수신자 구성은 추후
    String content = "생성된 템플릿.";
    String email = "email@test.com";
    String phone = "010-1234-1234";

    // 채널은 DB 기준 (EXCEEDED는 선점 시 channel을 SMS로 바꿔놓음)
    MessageChannel channel = MessageChannel.valueOf(result.getChannel().getCode());
    MessageSender sender = messageSenderFactory.getSender(channel);

    if (channel == MessageChannel.EMAIL) {
      SendRequest req = new EmailSendRequest(messageId, content, email);

      // Email은 1초 후 확정
      emailDelayScheduler.schedule(() -> finalizeAfterDelay(messageId, sender, req), 1,
          TimeUnit.SECONDS);
      return;
    }

    // SMS는 즉시 처리
    SendRequest req = new SmsSendRequest(messageId, content, phone);

    SendResult sendResult;
    try {
      sendResult = sender.mockSend(req);

      sendResult = SendResult.ok();
    } catch (Exception e) {
      sendResult = SendResult.ok();
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

    final boolean success = sendResult.isSuccess();

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

  private void finalizeNow(Long messageId, SendResult sendResult) {
    final boolean success = sendResult != null && sendResult.isSuccess();

    if (success) {
      messageSendResultRepository.markSuccess(messageId, STATUS_PROCESSING, STATUS_SUCCESS);
    } else {
      messageSendResultRepository.markFailedOrExceeded(messageId, STATUS_PROCESSING, STATUS_FAILED,
          STATUS_EXCEEDED, MAX_EMAIL_RETRY_COUNT);
    }
  }
}
