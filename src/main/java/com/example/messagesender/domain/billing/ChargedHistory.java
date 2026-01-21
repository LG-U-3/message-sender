package com.example.messagesender.domain.billing;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "charged_histories")
@Getter
@NoArgsConstructor
public class ChargedHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "service_id", nullable = false)
  private Long serviceId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "charged_price", nullable = false)
  private Integer chargedPrice;

  @Column(name = "contract_discount_price", nullable = false)
  private Integer contractDiscountPrice;

  @Column(name = "bundled_discount_price", nullable = false)
  private Integer bundledDiscountPrice;

  @Column(name = "premier_discount_price", nullable = false)
  private Integer premierDiscountPrice;
}
