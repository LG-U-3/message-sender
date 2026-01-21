package com.example.messagesender.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.messagesender.domain.billing.BillingSettlement;

public interface BillingSettlementRepository extends JpaRepository<BillingSettlement, Long> {

    @Query("select b.detailJson from BillingSettlement b where b.id = :id")
    Optional<String> findDetailJsonById(@Param("id") Long id);
    Optional<BillingSettlement> findByUserIdAndTargetMonth(Long userId, String targetMonth);

}
