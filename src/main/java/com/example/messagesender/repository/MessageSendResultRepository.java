package com.example.messagesender.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.messagesender.domain.message.MessageSendResult;

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

  // ✅ timeout 대상 id 목록 조회 (PROCESSING & requestedAt <= threshold)
  @Query("""
      select msr.id
      from MessageSendResult msr
      where msr.status.id = :processingStatusId
        and msr.requestedAt <= :threshold
      """)
  List<Long> findProcessingTimeoutIds(@Param("processingStatusId") Long processingStatusId,
      @Param("threshold") LocalDateTime threshold, Pageable pageable);

  // ✅ PROCESSING -> FAILED 벌크 업데이트
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update MessageSendResult msr
      set msr.status.id = :failedStatusId
      where msr.id in :ids
        and msr.status.id = :processingStatusId
      """)
  int markFailedByIds(@Param("ids") List<Long> ids,
      @Param("processingStatusId") Long processingStatusId,
      @Param("failedStatusId") Long failedStatusId);

}
