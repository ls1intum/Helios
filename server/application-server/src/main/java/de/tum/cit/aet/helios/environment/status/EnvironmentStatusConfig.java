package de.tum.cit.aet.helios.environment.status;

import java.time.Duration;
import java.util.Map;
import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(EnvironmentStatusConfig.StatusCheckTaskExecutorConfig.class)
public class EnvironmentStatusConfig {

  /**
   * The interval at which to check the status of environments
   * whose status has changed recently. Has to be lower or equal
   * to the stable interval. Defaults to 10 seconds.
   */
  @Getter
  @Value("${status-check.recent-interval:10s}")
  private Duration checkRecentInterval;

  /**
   * The interval at which to check the status of environments
   * that have been stable for a while. Has to be higher or equal
   * to the recent interval. Defaults to 120 seconds.
   */
  @Getter
  @Value("${status-check.stable-interval:120s}")
  private Duration checkStableInterval;

  /**
   * The threshold after which an environment is considered stable.
   * If the status of an environment has not changed for this duration,
   * it is considered stable and will be checked less frequently.
   * Defaults to 5 minutes.
   */
  @Getter
  @Value("${status-check.recent-threshold:5m}")
  private Duration checkRecentThreshold;

  // The recent interval will always be lower than the stable interval
  // so that will be our general check interval
  public Duration getCheckInterval() {
    if (checkRecentInterval.compareTo(checkStableInterval) > 0) {
      throw new IllegalArgumentException(
        "Recent interval must be lower or equal to stable interval"
      );
    }
    return checkRecentInterval;
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplateBuilder()
        // Make sure this is lower than our interval for the status check scheduler
        // in total, we want to wait at most 8 seconds for a response
        .connectTimeout(Duration.ofSeconds(3))
        .readTimeout(Duration.ofSeconds(3))
        .build();
  }

  @Bean
  public Map<StatusCheckType, StatusCheckStrategy> checkStrategies(
      HttpStatusCheck httpStatusCheck,
      ArtemisInfoCheck artemisInfoCheck) {
    return Map.of(
        StatusCheckType.HTTP_STATUS, httpStatusCheck,
        StatusCheckType.ARTEMIS_INFO, artemisInfoCheck);
  }

  /**
   * Creates a TaskExecutor bean named "statusCheckTaskExecutor".
   * This executor is used to manage and execute status check tasks concurrently.
   * The core and maximum pool sizes are configured based on the available
   * processors
   * to optimize performance but can be overridden in the application
   * configuration.
   *
   * @param config the configuration for the StatusCheckTaskExecutor
   * @return a configured ThreadPoolTaskExecutor instance
   */
  @Bean("statusCheckTaskExecutor")
  public TaskExecutor taskExecutor(
      StatusCheckTaskExecutorConfig config) {
    int availableProcessors = Runtime.getRuntime().availableProcessors();

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(config.getCorePoolSize(availableProcessors));
    executor.setMaxPoolSize(config.getMaxPoolSize(availableProcessors));
    executor.setThreadNamePrefix("status-check-");
    return executor;
  }

  @ConfigurationProperties(prefix = "status-check.executor")
  @Data
  public static class StatusCheckTaskExecutorConfig {
    private Integer corePoolSize;
    private Integer maxPoolSize;

    // Dynamic defaults based on CPU cores
    public int getCorePoolSize(int availableProcessors) {
      return corePoolSize != null ? corePoolSize : availableProcessors * 2;
    }

    public int getMaxPoolSize(int availableProcessors) {
      return maxPoolSize != null ? maxPoolSize : availableProcessors * 4;
    }
  }
}