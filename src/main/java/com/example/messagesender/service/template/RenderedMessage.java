package com.example.messagesender.service.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RenderedMessage {
  private final String title; // EMAIL이면 사용, SMS면 null 가능
  private final String body; // NOT NULL
}
