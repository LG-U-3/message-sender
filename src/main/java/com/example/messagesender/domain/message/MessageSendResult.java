package com.example.messagesender.domain.message;

import com.example.messagesender.domain.code.Code;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "message_send_results",
    indexes = {
        @Index(
            name = "idx_msr_status_requested",
            columnList = "status_id, requested_at"
        ),
        @Index(
            name = "idx_msr_template",
            columnList = "template_id"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageSendResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reserved_send_id", nullable = false)
  private MessageReservation reservation;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "channel_id", nullable = false)
  private Code channel;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id", nullable = false)
  private MessageTemplate template;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "status_id", nullable = false)
  private Code status;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Builder
  private MessageSendResult(
      MessageReservation reservation,
      Long userId,
      Code channel,
      MessageTemplate template,
      Code status,
      LocalDateTime requestedAt,
      LocalDateTime processedAt,
      int retryCount
  ) {
    this.reservation = reservation;
    this.userId = userId;
    this.channel = channel;
    this.template = template;
    this.status = status;
    this.requestedAt = requestedAt;
    this.processedAt = processedAt;
    this.retryCount = retryCount;
  }

  public static MessageSendResult createFrom(
      MessageReservation reservation,
      Long userId,
      Code status
  ) {

    MessageSendResult result = new MessageSendResult();
    result.userId = userId;
    result.reservation = reservation;
    result.template = reservation.getTemplate();
    result.channel = reservation.getTemplate().getChannelType();
    result.status = status;
    result.retryCount = 0;
    result.requestedAt = LocalDateTime.now();
    return result;
  }
}
