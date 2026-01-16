package com.example.messagesender.repository;

import com.example.messagesender.domain.message.MessageSendResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSendResultRepository extends JpaRepository<MessageSendResult, Long> {


  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :processingStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
             and m.status.id in (:waitingStatusId, :failedStatusId)
      """)
  int markProcessing(
      @Param("id") Long id,
      @Param("processingStatusId") Long processingStatusId,
      @Param("waitingStatusId") Long waitingStatusId,
      @Param("failedStatusId") Long failedStatusId
  );

  @Modifying
  @Query("""
          update MessageSendResult m
             set m.status.id = :successStatusId,
                 m.processedAt = CURRENT_TIMESTAMP
           where m.id = :id
      """)
  void markSuccess(
      @Param("id") Long id,
      @Param("successStatusId") Long successStatusId
  );
}