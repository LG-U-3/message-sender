package com.example.messagesender.domain.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "billing_settlements",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_billing_settlements_user_month",
            columnNames = {"user_id", "target_month"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingSettlement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "batch_run_id", nullable = false)
  private Long batchRunId;

  @Column(name = "target_month", nullable = false, length = 7)
  private String targetMonth;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "detail_json", nullable = false, columnDefinition = "json")
  private String detailJson;

  @Column(name = "final_amount", nullable = false)
  private Integer finalAmount;

  @Builder
  private BillingSettlement(
      Long batchRunId,
      String targetMonth,
      Long userId,
      String detailJson,
      Integer finalAmount
  ) {
    this.batchRunId = batchRunId;
    this.targetMonth = targetMonth;
    this.userId = userId;
    this.detailJson = detailJson;
    this.finalAmount = finalAmount;
  }
}
