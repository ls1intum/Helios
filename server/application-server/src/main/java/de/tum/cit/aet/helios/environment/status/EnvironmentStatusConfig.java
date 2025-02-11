package de.tum.cit.aet.helios.environment.status;

import java.time.Duration;
import java.util.Map;
import lombok.Data;
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
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplateBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .readTimeout(Duration.ofSeconds(5))
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
   * The core and maximum pool sizes are configured based on the available processors
   * to optimize performance but can be overridden in the application configuration.
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