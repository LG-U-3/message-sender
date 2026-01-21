package com.example.messagesender.service;

import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.messagesender.common.code.CodeCache;
import com.example.messagesender.common.code.enums.CodeGroups;
import com.example.messagesender.common.code.enums.MessageSendStatus;
import com.example.messagesender.domain.message.MessageSendResult;
import com.example.messagesender.domain.message.MessageTemplate;
import com.example.messagesender.dto.MessageRequestDto;
import com.example.messagesender.repository.BillingSettlementRepository;
import com.example.messagesender.repository.ChargedHistoryRepository;
import com.example.messagesender.repository.MessageSendResultRepository;
import com.example.messagesender.repository.MessageTemplateRepository;
import com.example.messagesender.repository.UserRepository;
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
  // private final MessageSenderFactory messageSenderFactory;

  private final CodeCache codeCache;

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
    this.STATUS_FAILED = codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.FAILED);
  }

  public void process(MessageRequestDto dto) {
    System.out.println(">>> ENTER process, id=" + dto.getMessageSendResultId());
    MessageSendResult result = messageSendResultRepository.findById(dto.getMessageSendResultId())
        .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

    // WAITING or FAILED인 경우에만 PROCESSING으로 변경: updated > 0
    int updated = messageSendResultRepository.markProcessing(dto.getMessageSendResultId(),
        STATUS_PROCESSING, STATUS_WAITING, STATUS_FAILED);
    if (updated == 0) { // 처리 대상이 아닌경우 종료
      System.out.println(">>> SKIP process (updated=0). currentStatus not WAITING/FAILED. id="
          + dto.getMessageSendResultId());
      return;
    }

    // TODO: 메세지 템플릿 생성
    try {
      // 1) 템플릿 조회
      MessageTemplate template = messageTemplateRepository.findById(result.getTemplate().getId())
          .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

      // 2) values Map 자동 생성 (User + Settlement.detail_json)
      TemplateValueResolver resolver = new TemplateValueResolver(userRepository,
          billingSettlementRepository, objectMapper, chargedHistoryRepository // 추가
      );

      Map<String, String> values = resolver.resolve(result, template);

      // 3) 템플릿 렌더링 (치환)
      MessageTemplateEngine engine = new MessageTemplateEngine();
      RenderedMessage rendered = engine.render(template.getTitle(), template.getBody(), values);

      // TODO: sender로 rendered 넘기기
      System.out.println("TITLE=" + rendered.getTitle());
      System.out.println("BODY=" + rendered.getBody());
      System.out.println("VALUES=" + values);

      // TODO: Sender 선택 및 발송: EmailSenderMockService, SmsSenderMockService
      try {
        Thread.sleep(100); // 0.1초 지연
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      System.out.println("메시지 발송완료...! " + dto);
      // 채널에 맞게 sender를 호출하는 역할
      // MessageSender sender =
      // messageSenderFactory.getSender(result.getChannel());
      // sender.send(
      // new MessageContext(
      // result.getUserId(),
      // template.getTitle(),
      // content
      // )
      // );
      // TODO: 전송 실패 시 실패 처리후 종료

      // 성공 처리
      messageSendResultRepository.markSuccess(dto.getMessageSendResultId(), STATUS_SUCCESS);
    } finally {

    }
  }
}
