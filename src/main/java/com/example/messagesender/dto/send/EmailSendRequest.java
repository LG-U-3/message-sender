package com.example.messagesender.dto.send;

import lombok.Getter;

@Getter
public class EmailSendRequest extends SendRequest {

  private final String title;
  private final String email;

  public EmailSendRequest(Long messageSendResultId, String title, String content, String email) {
    super(messageSendResultId, content);
    this.title = title;
    this.email = email;
  }
}
