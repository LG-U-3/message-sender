package com.example.messagesender.repository;

import com.example.messagesender.domain.message.MessageSendResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSendResultRepository extends JpaRepository<MessageSendResult, Long> {

  /**
   * 선점 + (FAILED 인 경우) retry_count + 1
   *
   * - WAITING/FAILED 상태에서만 PROCESSING으로 선점 - processed_at 이 null 인 경우에만 (이미 확정된 건 재처리 방지) -
   * "retry_count 증가"는 오직 FAILED -> PROCESSING 진입할 때만 발생
   */
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.retryCount = case
                                when m.status.id = :failedStatusId then m.retryCount + 1
                                else m.retryCount
                               end,
                 m.status.id = :processingStatusId
           where m.id = :id
             and m.processedAt is null
             and m.status.id in (:waitingStatusId, :failedStatusId)
      """)
  int markProcessingWithRetryIncrement(@Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("waitingStatusId") Long waitingStatusId, @Param("failedStatusId") Long failedStatusId);

  /**
   * 3번째 시도(= retry_count=2)에서만 SMS로 강제 전환 + DB에도 기록
   *
   * - 반드시 PROCESSING 상태에서만 - 반드시 retry_count=2 인 경우에만 (조건으로 강제) - processed_at null 인 경우에만
   */
  @Modifying
  @Query("""
          update MessageSendResult m
             set m.channel.id = :smsChannelId
           where m.id = :id
             and m.status.id = :processingStatusId
             and m.retryCount = :requiredRetryCount
             and m.processedAt is null
      """)
  int switchChannelToSmsOnThirdAttempt(@Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("smsChannelId") Long smsChannelId,
      @Param("requiredRetryCount") int requiredRetryCount);

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
