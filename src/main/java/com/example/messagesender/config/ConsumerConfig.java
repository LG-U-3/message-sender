package com.example.messagesender.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ConsumerConfig {

  @Value("${worker.threads:20}")
  private int workerThreads;

  @Value("${worker.queue-capacity:2000}")
  private int queueCapacity;

  @Bean(destroyMethod = "shutdown")
  public ExecutorService workerExecutorService() {
    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueCapacity);

    ThreadFactory threadFactory = r -> {
      Thread t = new Thread(r);
      t.setName("msg-worker-" + t.getId());
      t.setDaemon(true);
      return t;
    };

    RejectedExecutionHandler rejectHandler = (r, executor) -> {
      // 큐가 꽉 찼을 때: 호출자 스레드에서 실행 (백프레셔)
      if (!executor.isShutdown())
        r.run();
    };

    return new ThreadPoolExecutor(workerThreads, workerThreads, 0L, TimeUnit.MILLISECONDS, queue,
        threadFactory, rejectHandler);
  }

  @Bean(destroyMethod = "shutdown")
  public ScheduledExecutorService emailDelayScheduler() {
    ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(4,
        r -> {
          Thread t = new Thread(r);
          t.setName("email-delay-" + t.getId());
          t.setDaemon(true);
          return t;
        });
    exec.setRemoveOnCancelPolicy(true);
    return exec;
  }

}
