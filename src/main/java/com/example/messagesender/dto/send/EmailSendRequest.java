package com.example.messagesender.dto.send;

import lombok.Getter;

@Getter
public class EmailSendRequest extends SendRequest {

  private final String email;

  public EmailSendRequest(Long messageSendResultId, String title, String content, String email) {
    super(messageSendResultId, title, content);
    this.email = email;
  }
}
