package com.example.messagesender.service.template;

import java.util.Map;
import java.util.Set;
import com.example.messagesender.service.template.TemplateVariablesParser.VariableSpec;
import com.example.messagesender.service.template.TemplateVariablesParser.VariableType;

public class TemplateValuesValidator {

  public void validateAndFilter(Map<String, VariableSpec> specMap, Map<String, String> values,
      TemplateValueResolverOptions options) {
    if (specMap == null || specMap.isEmpty()) {
      return;
    }

    // 1) 허용되지 않은 키 제거
    if (options.isFilterByVariablesJson()) {
      Set<String> allowed = specMap.keySet();
      values.keySet().retainAll(allowed);
    }

    // 2) 필수값 체크
    if (options.isStrict()) {
      for (Map.Entry<String, VariableSpec> e : specMap.entrySet()) {
        String key = e.getKey();
        VariableSpec spec = e.getValue();

        if (spec.isRequired()) {
          String v = values.get(key);
          if ((v == null || v.isBlank()) && !options.isAllowMissing()) {
            throw new IllegalArgumentException("필수 변수 누락: " + key);
          }
        }
      }
    }

    // 3) 타입 체크 (간단 버전: NUMBER면 숫자 포맷만 검사)
    if (options.isStrictTypeCheck()) {
      for (Map.Entry<String, VariableSpec> e : specMap.entrySet()) {
        String key = e.getKey();
        VariableType type = e.getValue().getType();
        String v = values.get(key);

        if (v == null || v.isBlank())
          continue;

        if (type == VariableType.NUMBER) {
          // "47,000" 같은 금액도 허용하려면 콤마 제거 후 검사
          String normalized = v.replace(",", "").trim();
          if (!normalized.matches("^-?\\d+(\\.\\d+)?$")) {
            throw new IllegalArgumentException("변수 타입 불일치(NUMBER): " + key + "=" + v);
          }
        }
      }
    }
  }
}
