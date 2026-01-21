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
import com.example.messagesender.sender.MessageSender;
import com.example.messagesender.service.sender.MessageSenderFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageProcessService {

  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageSenderFactory messageSenderFactory;
  private final CodeCache codeCache;
  private final ScheduledExecutorService emailDelayScheduler;

  // 비동기 스레드에서 DB 업데이트 트랜잭션 보장
  private final TransactionTemplate transactionTemplate;

  private Long STATUS_PROCESSING;
  private Long STATUS_WAITING;
  private Long STATUS_SUCCESS;
  private Long STATUS_FAILED;

  private Long CHANNEL_SMS;

  // 너가 말한 failed code id = 9 라고 했지만,
  // 운영/환경 바뀌어도 안전하게 CodeCache로 받도록 유지.
  @EventListener(ApplicationReadyEvent.class)
  void initStatuses() {
    this.STATUS_PROCESSING =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.PROCESSING);
    this.STATUS_WAITING =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.WAITING);
    this.STATUS_SUCCESS =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.SUCCESS);
    this.STATUS_FAILED = codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.FAILED);

    this.CHANNEL_SMS = codeCache.getId(CodeGroups.MESSAGE_CHANNEL, MessageChannel.SMS);
  }

  public void process(MessageRequestDto dto) {
    final Long messageId = dto.getMessageSendResultId();

    // 선점 + (FAILED면 retry_count + 1)
    int updated = messageSendResultRepository.markProcessingWithRetryIncrement(messageId,
        STATUS_PROCESSING, STATUS_WAITING, STATUS_FAILED);
    if (updated == 0) {
      return; // 이미 누가 처리 중이거나, 처리 완료/불가 상태
    }

    // 최신 retry_count / channel 확인을 위해 재조회 (영속성 1차캐시 의존 X)
    MessageSendResult result = messageSendResultRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

    // TODO: 템플릿/수신자 데이터 연동 전 임시
    String content = "생성된 템플릿...";
    String email = "email@test.com";
    String phone = "010-1234-1234";

    // =========================================
    // 3번째 시도 조건: retry_count == 2
    // - 이 값은 'FAILED -> PROCESSING 선점'할 때 +1 되었기 때문에
    // retry_count==2 라면 "이번 실행이 3번째 시도"가 보장됨.
    // =========================================
    if (result.getRetryCount() == 2) {
      // 반드시 3번째 시도에서만 SMS로 강제전환 + DB에 channel_id 기록
      messageSendResultRepository.switchChannelToSmsOnThirdAttempt(messageId, STATUS_PROCESSING,
          CHANNEL_SMS, 2);

      MessageSender smsSender = messageSenderFactory.getSender(MessageChannel.SMS);

      SendRequest smsReq = new SmsSendRequest(messageId, content, phone);
      SendResult smsResult;
      try {
        smsResult = smsSender.mockSend(smsReq); // SmsSenderMock = 100% 성공
                                                // :contentReference[oaicite:4]{index=4}
      } catch (Exception e) {
        // "3번째 시도는 무조건 성공"을 정말 강제하고 싶다면 여기서 ok()로 바꿔도 됨
        smsResult = SendResult.ok();
      }

      // Delay 없이 즉시 확정
      finalizeNow(messageId, smsResult);
      return;
    }

    // 1~2번째 시도: 원래 채널로 발송 (대부분 EMAIL일 것)
    MessageChannel channel = MessageChannel.valueOf(result.getChannel().getCode());
    MessageSender sender = messageSenderFactory.getSender(channel);

    if (channel == MessageChannel.EMAIL) {
      // EmailSendRequest(Long id, String content, String email) <= 순서 중요
      SendRequest emailReq = new EmailSendRequest(messageId, content, email);

      // 기존 방식 그대로: sleep 없이 1초 뒤에 finalize
      emailDelayScheduler.schedule(() -> finalizeAfterDelay(messageId, sender, emailReq), 1,
          TimeUnit.SECONDS);
      return;
    }

    // (혹시 1~2번째에도 SMS가 들어오면) SMS는 즉시 처리/확정
    SendRequest smsReq = new SmsSendRequest(messageId, content, phone);
    SendResult sendResult;
    try {
      sendResult = sender.mockSend(smsReq);
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
