package com.example.messagesender.service.template;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TemplateVariablesParser {

  private final ObjectMapper objectMapper;

  public TemplateVariablesParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @SuppressWarnings("deprecation")
  public Map<String, VariableSpec> parse(String variablesJson) {
    Map<String, VariableSpec> specMap = new HashMap<>();
    if (variablesJson == null || variablesJson.isBlank()) {
      return specMap;
    }

    try {
      JsonNode root = objectMapper.readTree(variablesJson);

      // 케이스1) {"userName":{"type":"STRING","required":true}, ...} 형태
      if (root.isObject()) {
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
          Map.Entry<String, JsonNode> e = it.next();
          String key = e.getKey();
          JsonNode specNode = e.getValue();

          VariableType type = VariableType.STRING;
          boolean required = false;

          if (specNode != null && specNode.isObject()) {
            String typeStr = specNode.path("type").asText("STRING");
            type = VariableType.from(typeStr);
            required = specNode.path("required").asBoolean(false);
          }
          specMap.put(key, new VariableSpec(type, required));
        }
      }

      return specMap;
    } catch (Exception e) {
      throw new IllegalStateException("variables_json 파싱 실패", e);
    }
  }

  public enum VariableType {
    STRING, NUMBER;

    public static VariableType from(String raw) {
      if (raw == null)
        return STRING;
      String v = raw.trim().toUpperCase();
      if ("LONG".equals(v) || "INT".equals(v) || "INTEGER".equals(v) || "DOUBLE".equals(v)
          || "NUMBER".equals(v)) {
        return NUMBER;
      }
      return STRING;
    }
  }

  public static class VariableSpec {
    private final VariableType type;
    private final boolean required;

    public VariableSpec(VariableType type, boolean required) {
      this.type = type;
      this.required = required;
    }

    public VariableType getType() {
      return type;
    }

    public boolean isRequired() {
      return required;
    }
  }
}
