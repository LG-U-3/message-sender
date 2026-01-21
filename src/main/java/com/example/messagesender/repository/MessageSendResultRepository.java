package com.example.messagesender.repository;

import com.example.messagesender.domain.message.MessageSendResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSendResultRepository extends JpaRepository<MessageSendResult, Long> {

  // 선점만: processedAt은 여기서 X
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

  // 성공 확정: PROCESSING에서만 + processed_at 여기서 O
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :successStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :processingStatusId
             and m.processedAt is null
      """)
  int markSuccess(@Param("id") Long id, @Param("processingStatusId") Long processingStatusId,
      @Param("successStatusId") Long successStatusId);

  // 실패 확정: PROCESSING에서만 + processed_at 여기서 O
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :failedStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id = :processingStatusId
             and m.processedAt is null
      """)
  int markFailed(@Param("id") Long id, @Param("processingStatusId") Long processingStatusId,
      @Param("failedStatusId") Long failedStatusId);
}
