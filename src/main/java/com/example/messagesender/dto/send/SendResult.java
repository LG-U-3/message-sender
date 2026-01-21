package com.example.messagesender.dto.send;

import lombok.Getter;

@Getter
public class SendResult {

  private final boolean success;
  private final String errorMessage;

  private SendResult(boolean success, String errorMessage) {
    this.success = success;
    this.errorMessage = errorMessage;
  }

  public static SendResult ok() {
    return new SendResult(true, null);
  }

  public static SendResult fail(String errorMessage) {
    return new SendResult(false, errorMessage);
  }
}
