package com.example.messagesender.service.template;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageTemplateEngine {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

  public RenderedMessage render(String title, String body, Map<String, String> values) {
    return new RenderedMessage(replace(title, values), replace(body, values));
  }

  private String replace(String text, Map<String, String> values) {
    if (text == null)
      return null;

    Matcher m = PLACEHOLDER.matcher(text);
    StringBuffer sb = new StringBuffer();

    while (m.find()) {
      String key = m.group(1);
      String val = values.getOrDefault(key, "");
      m.appendReplacement(sb, Matcher.quoteReplacement(val));
    }

    m.appendTail(sb);
    return sb.toString();
  }
}
