package com.example.messagesender.recovery;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.messagesender.common.code.CodeCache;
import com.example.messagesender.common.code.enums.CodeGroups;
import com.example.messagesender.common.code.enums.MessageSendStatus;
import com.example.messagesender.repository.MessageSendResultRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessingTimeoutScheduler {

  private static final Logger log = LoggerFactory.getLogger(ProcessingTimeoutScheduler.class);

  private final MessageSendResultRepository messageSendResultRepository;
  private final CodeCache codeCache;

  @Value("${message.recovery.timeout-minutes:60}")
  private long timeoutMinutes;

  @Value("${message.recovery.db-batch-size:500}")
  private int dbBatchSize;

  private Long STATUS_PROCESSING;
  private Long STATUS_FAILED;

  private volatile boolean initialized = false;

  private void initIfNeeded() {
    if (initialized)
      return;

    this.STATUS_PROCESSING =
        codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.PROCESSING);
    this.STATUS_FAILED = codeCache.getId(CodeGroups.MESSAGE_SEND_STATUS, MessageSendStatus.FAILED);

    initialized = true;
    log.info("[INIT] ProcessingTimeoutScheduler status ids initialized");
  }

  @Scheduled(fixedDelayString = "${message.recovery.fixed-delay-ms:300000}")
  @Transactional
  public void recoverProcessingTimeout() {
    log.info("[SCHEDULER] db-timeout tick");

    initIfNeeded();

    if (STATUS_PROCESSING == null || STATUS_FAILED == null) {
      log.warn("[TIMEOUT] status ids not initialized yet. skip.");
      return;
    }

    LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);

    List<Long> ids = messageSendResultRepository.findProcessingTimeoutIds(STATUS_PROCESSING,
        threshold, PageRequest.of(0, dbBatchSize));

    if (ids == null || ids.isEmpty()) {
      return;
    }

    int updated =
        messageSendResultRepository.markFailedByIds(ids, STATUS_PROCESSING, STATUS_FAILED);

    log.warn("[TIMEOUT] PROCESSING -> FAILED recovered. threshold={}, idsCount={}, updated={}",
        threshold, ids.size(), updated);
  }
}
