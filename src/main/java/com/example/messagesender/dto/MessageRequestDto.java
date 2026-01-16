package com.example.messagesender.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageRequestDto {

  private Long messageSendResultId;
  private String channel;
  private String purpose;

  public MessageRequestDto(Long id, String channel, String purpose) {
    this.messageSendResultId = id;
    this.channel = channel;
    this.purpose = purpose;
  }

  @Override
  public String toString() {
    return this.messageSendResultId + ": " + "(" + this.channel + ")";
  }
}
