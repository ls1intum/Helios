package de.tum.cit.aet.helios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

  /**
   * Creates a default TaskScheduler bean. This scheduler is used for general scheduling tasks
   * throughout the application.
   *
   * @return a configured ThreadPoolTaskScheduler instance
   */
  @Bean
  @Primary
  public TaskScheduler defaultTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(10);
    scheduler.setThreadNamePrefix("default-scheduler-");
    scheduler.initialize();
    return scheduler;
  }

  /**
   * Creates a TaskScheduler bean named "workflowRunTaskScheduler". This scheduler is used for
   * scheduling workflow run webhook events.
   *
   * @return a configured ThreadPoolTaskScheduler instance
   */
  @Bean("workflowRunTaskScheduler")
  public TaskScheduler workflowRunTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    // Set the pool size to 1 to ensure tasks are executed sequentially
    // This guarantees that webhook events are processed one at a time
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("workflow-run-scheduler-");
    scheduler.initialize();
    return scheduler;
  }
}
