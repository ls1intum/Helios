package de.tum.cit.aet.helios.tests;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class TestsConfig {
  /**
   * Creates a TaskExecutor bean named "testResultProcessorExecutor". This executor is used to
   * process test results asynchronously.
   *
   * @return a configured ThreadPoolTaskExecutor instance
   */
  @Bean("testResultProcessorExecutor")
  public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("test-result-processor-");
    return executor;
  }
}
