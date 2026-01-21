package com.example.messagesender.dto.send;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SendRequest {

  private final Long messageSendResultId;

  private final String content;
}
