package com.example.messagesender.repository;

import com.example.messagesender.domain.message.MessageSendResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSendResultRepository extends JpaRepository<MessageSendResult, Long> {

  // 초기 발송 성공
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :successStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :waitingStatusId
             and m.processedAt is null
      """)
  int markSuccessFromWaiting(
      @Param("id") Long id,
      @Param("waitingStatusId") Long waitingStatusId,
      @Param("successStatusId") Long successStatusId
  );

  // 초기 발송 실패 (retry 증가 없음)
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :failedStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :waitingStatusId
             and m.processedAt is null
      """)
  int markFailedFromWaiting(
      @Param("id") Long id,
      @Param("waitingStatusId") Long waitingStatusId,
      @Param("failedStatusId") Long failedStatusId
  );

  // 재시도 성공
  @Modifying
  @Query("""
          update MessageSendResult m
            set m.retryCount  = m.retryCount + 1,
                 m.status.id   = :successStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :processingStatusId
             and m.processedAt is null
      """)
  int markSuccessFromProcessing(
      @Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("successStatusId") Long successStatusId
  );

  // 재시도 실패 (retry +1 후 FAILED / EXCEEDED 결정)
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.retryCount = m.retryCount + 1,
                 m.status.id = case
                   when (m.retryCount + 1) > :maxRetry
                        and m.template.purposeType.code = 'BILLING'
                     then :exceededStatusId
                   else :failedStatusId
                 end,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :processingStatusId
             and m.processedAt is null
      """)
  int markFailedOrExceededFromProcessing(
      @Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("failedStatusId") Long failedStatusId,
      @Param("exceededStatusId") Long exceededStatusId,
      @Param("maxRetry") int maxRetry
  );
}
