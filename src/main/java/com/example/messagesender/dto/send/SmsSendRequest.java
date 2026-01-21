package com.example.messagesender.dto.send;

import lombok.Getter;

@Getter
public class SmsSendRequest extends SendRequest {

  private final String phone;

  public SmsSendRequest(Long messageSendResultId, String content, String phone) {
    super(messageSendResultId, content);
    this.phone = phone;
  }
}
