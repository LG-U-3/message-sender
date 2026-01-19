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

@Service
@RequiredArgsConstructor
@Transactional
public class MessageProcessService {

  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageTemplateRepository messageTemplateRepository;
  private final MessageSenderFactory messageSenderFactory;

  private final CodeCache codeCache;

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

    MessageSendResult result =
        messageSendResultRepository.findById(dto.getMessageSendResultId())
            .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

    // WAITING or FAILED인 경우에만 PROCESSING으로 변경: updated > 0
    int updated =
        messageSendResultRepository.markProcessing(dto.getMessageSendResultId(),
            STATUS_PROCESSING,
            STATUS_WAITING,
            STATUS_FAILED
        );
    if (updated == 0) { // 처리 대상이 아닌경우 종료
      return;
    }

    // TODO: 메세지 템플릿 생성
    // MessageTemplate template = result.getTemplate();
    String content = "생성된 템플릿...";
    Long messageId = dto.getMessageSendResultId();

    MessageChannel channel =
        MessageChannel.valueOf(result.getChannel().getCode());
    MessageSender sender =
        messageSenderFactory.getSender(channel);

    SendRequest sendRequest;
    if (channel == MessageChannel.EMAIL) {
      sendRequest =
          new EmailSendRequest(
              messageId,
              "email@test.com",
              content
          );
    } else {
      sendRequest =
          new SmsSendRequest(
              messageId,
              "010-1234-1234",
              content
          );
    }

    SendResult sendResult = sender.mockSend(sendRequest);

    if (!sendResult.isSuccess()) {
      messageSendResultRepository.markCompleteStatus(dto.getMessageSendResultId(), STATUS_FAILED);
      System.out.println("MESSAGE SEND REQUEST FAILED: " + dto.getMessageSendResultId());
      return;
    }

    // 성공 처리
    messageSendResultRepository.markCompleteStatus(dto.getMessageSendResultId(), STATUS_SUCCESS);
    System.out.println("MESSAGE SEND REQUEST SUCCESS: " + dto.getMessageSendResultId());
  }
}
