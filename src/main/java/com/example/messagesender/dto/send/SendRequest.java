package com.example.messagesender.dto.send;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
public class SendRequest {

  private Long messageSendResultId;
  private String title;
  private String content;

  public SendRequest(Long messageSendResultId, String content) {
    this.messageSendResultId = messageSendResultId;
    this.content = content;
  }
}
