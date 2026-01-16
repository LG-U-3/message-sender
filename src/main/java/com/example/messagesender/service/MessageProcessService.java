package com.example.messagesender.service;

import com.example.messagesender.dto.MessageRequestDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageProcessService {

  private final MessageSendResultRepository messageSendResultRepository;
  private final MessageTemplateRepository messageTemplateRepository;
  private final MessageSenderFactory messageSenderFactory;

  public void process(MessageRequestDto dto) {

    MessageSendResult result =
        messageSendResultRepository.findById(dto.getMessageSendResultId())
            .orElseThrow(() -> new IllegalArgumentException("메시지 없음"));

    // 1. 상태 체크
    if (!result.isSendable()) {
      return;
    }

    // 2. PROCESSING
    result.markProcessing();

    // 3. 템플릿 조회
    MessageTemplate template = result.getTemplate();

    // 4. 변수 조합
    Map<String, String> variables =
        MessageVariableAssembler.assemble(result);

    // 5. 메시지 생성
    String content =
        MessageTemplateRenderer.render(template.getBody(), variables);

    // 6. Sender 선택
    MessageSender sender =
        messageSenderFactory.getSender(result.getChannel());

    // 7. 발송 (Mock)
    sender.send(
        new MessageContext(
            result.getUserId(),
            template.getTitle(),
            content
        )
    );

    // 8. 성공 처리
    result.markSuccess();
  }
}
