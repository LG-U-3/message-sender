package com.example.messagesender.common.code;

import com.example.messagesender.domain.code.Code;
import com.example.messagesender.repository.CodeRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CodeCache {

  private final CodeRepository codeRepository;

  private final Map<String, Code> codeByKey = new HashMap<>();

  // 애플리케이션 기동 시점에 맵에 저장
  @EventListener(ApplicationReadyEvent.class)
  @Transactional(readOnly = true)
  public void init() {
    List<Code> codes = codeRepository.findAll();
    for (Code code : codes) {
      // key 예: MESSAGE_CHANNEL:EMAIL
      String key = code.getCodeGroup().getCode() + ":" + code.getCode();
      codeByKey.put(key, code);
    }
  }

  public Code get(String group, Enum<?> code) {
    Code result = codeByKey.get(group + ":" + code.name());
    if (result == null) {
      throw new IllegalStateException(
          "Code not found: " + group + ":" + code.name()
      );
    }
    return result;
  }

  public Long getId(String group, Enum<?> code) {
    return get(group, code).getId();
  }
}
