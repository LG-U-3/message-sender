package com.example.messagesender.domain.message;

import com.example.messagesender.domain.code.Code;
import com.example.messagesender.domain.user.UserGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "message_reservations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "scheduled_at", nullable = false)
  private LocalDateTime scheduledAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "status_id", nullable = false)
  private Code status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "channel_type_id", nullable = false)
  private Code channelType;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id", nullable = false)
  private MessageTemplate template;

  @Column(name = "template_type_id", nullable = false, length = 10)
  private String templateTypeId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_group_id", nullable = false)
  private UserGroup userGroup;

  @Builder
  private MessageReservation(
      LocalDateTime scheduledAt,
      Code status,
      Code channelType,
      MessageTemplate template,
      String templateTypeId,
      UserGroup userGroup
  ) {
    this.scheduledAt = scheduledAt;
    this.status = status;
    this.channelType = channelType;
    this.template = template;
    this.templateTypeId = templateTypeId;
    this.userGroup = userGroup;
  }
}
