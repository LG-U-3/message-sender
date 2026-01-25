package com.example.messagesender.repository;

import com.example.messagesender.domain.message.MessageSendResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSendResultRepository extends JpaRepository<MessageSendResult, Long> {

  // 일반 선점: WAITING/FAILED(processedAt null) -> PROCESSING
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :processingStatusId
           where m.id = :id
             and m.processedAt is null
             and m.status.id in (:waitingStatusId, :failedStatusId)
      """)
  int markProcessing(@Param("id") Long id, @Param("processingStatusId") Long processingStatusId,
      @Param("waitingStatusId") Long waitingStatusId, @Param("failedStatusId") Long failedStatusId);

  /**
   * FAILED 재시도 선점 (EMAIL 재시도 전용) 정책: - retryCount 0/1/2 까지는 EMAIL 재시도 - retryCount=2에서 실패하면
   * EXCEEDED로 전환(별도 실패 확정 쿼리에서 처리) 여기서는 채널 변경 없음 (재시도 채널은 billing-batch에서 EMAIL 강제 publish)
   * BILLING 만 허용
   */
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id   = :processingStatusId,
                 m.retryCount  = m.retryCount + 1,
                 m.processedAt = null
           where m.id = :id
             and m.status.id = :failedStatusId
             and m.retryCount < :maxEmailRetryCount
             and exists (
                 select 1
                   from MessageSendResult x
                   join x.template t
                  where x.id = m.id
                    and t.purposeType.id = :billingPurposeTypeId
             )
      """)
  int markRetryProcessing(@Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("failedStatusId") Long failedStatusId,
      @Param("maxEmailRetryCount") int maxEmailRetryCount, // 2
      @Param("billingPurposeTypeId") Long billingPurposeTypeId // BILLING purpose_type_id
  );

  // EXCEEDED(SMS fallback) 선점 - retryCount는 그대로 유지 (2 유지) - channel을 SMS로 변경해서 DB에 기록 -
  // processedAt을 null로 되돌려 "처리중"으로 만들고, 이후 markSuccess에서 확정
  // BILLING 만 허용
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id   = :processingStatusId,
                 m.processedAt = null,
                 m.channel.id  = :smsChannelId
           where m.id = :id
             and m.status.id = :exceededStatusId
             and exists (
                 select 1
                   from MessageSendResult x
                   join x.template t
                  where x.id = m.id
                    and t.purposeType.id = :billingPurposeTypeId
             )
      """)
  int markExceededProcessing(@Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("exceededStatusId") Long exceededStatusId, @Param("smsChannelId") Long smsChannelId,
      @Param("billingPurposeTypeId") Long billingPurposeTypeId // BILLING purpose_type_id
  );

  // 성공 확정: PROCESSING -> SUCCESS
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :successStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :processingStatusId
             and m.processedAt is null
      """)
  int markSuccess(
      @Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("successStatusId") Long successStatusId);

  // 실패 확정: PROCESSING -> (FAILED or EXCEEDED)
  // - retryCount >= maxEmailRetryCount(=2) 인 상태에서 실패하면 EXCEEDED(11) - 그 외는 FAILED(9)
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = case
                                 when m.retryCount >= :maxEmailRetryCount then :exceededStatusId
                                 else :failedStatusId
                               end,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :processingStatusId
             and m.processedAt is null
      """)
  int markFailedOrExceeded(@Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("failedStatusId") Long failedStatusId,
      @Param("exceededStatusId") Long exceededStatusId,
      @Param("maxEmailRetryCount") int maxEmailRetryCount // 2
  );
}
