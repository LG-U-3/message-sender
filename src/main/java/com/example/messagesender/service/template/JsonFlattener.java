package com.example.messagesender.service.template;

import java.util.Iterator;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

public class JsonFlattener {

  @SuppressWarnings("deprecation")
  public void flatten(JsonNode node, String prefix, Map<String, String> out) {
    if (node == null || node.isNull())
      return;

    if (node.isValueNode()) {
      if (!prefix.isEmpty())
        out.put(prefix, node.asText());
      return;
    }

    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> it = node.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        String next = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
        flatten(e.getValue(), next, out);
      }
      return;
    }

    if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        String next = prefix + "." + i;
        flatten(node.get(i), next, out);
      }
    }
  }
}
