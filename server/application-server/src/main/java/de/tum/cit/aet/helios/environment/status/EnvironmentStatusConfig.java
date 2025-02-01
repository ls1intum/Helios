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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableAsync
@EnableScheduling
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