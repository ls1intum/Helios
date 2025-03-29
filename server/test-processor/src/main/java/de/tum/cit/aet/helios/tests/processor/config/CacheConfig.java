package de.tum.cit.aet.helios.tests.processor.config;

import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("testResults");
    cacheManager.setCaffeine(
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS));
    return cacheManager;
  }
}
