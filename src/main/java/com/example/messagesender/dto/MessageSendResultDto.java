package com.example.messagesender.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageSendResultDto {

  private boolean success;
  private String errorMessage;

  public MessageSendResultDto(boolean success) {
    this.success = success;
  }

  public MessageSendResultDto(boolean success, String errorMessage) {
    this.success = success;
    this.errorMessage = errorMessage;
  }
}
