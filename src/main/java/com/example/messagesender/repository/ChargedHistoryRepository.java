package com.example.messagesender.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.messagesender.domain.billing.ChargedHistory;

public interface ChargedHistoryRepository extends JpaRepository<ChargedHistory, Long> {

  interface DiscountSum {
    Long getContractSum();
    Long getBundledSum();
    Long getPremierSum();
  }

  @Query(value = """
      SELECT
        COALESCE(SUM(contract_discount_price), 0) AS contractSum,
        COALESCE(SUM(bundled_discount_price), 0) AS bundledSum,
        COALESCE(SUM(premier_discount_price), 0) AS premierSum
      FROM charged_histories
      WHERE user_id = :userId
        AND DATE_FORMAT(created_at, '%Y-%m') = :targetMonth
      """, nativeQuery = true)
  DiscountSum sumDiscountsByUserAndMonth(
      @Param("userId") Long userId,
      @Param("targetMonth") String targetMonth
  );
}
