package com.example.messagesender.dto.send;

import lombok.Getter;

@Getter
public class EmailSendRequest extends SendRequest {

  private final String email;

  public EmailSendRequest(Long messageSendResultId, String content, String email) {
    super(messageSendResultId, content);
    this.email = email;
  }
}
