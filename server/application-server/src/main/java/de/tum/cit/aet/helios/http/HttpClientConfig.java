package de.tum.cit.aet.helios.http;

import java.io.File;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class HttpClientConfig {

  @Value("${http.cache.enabled:false}")
  private boolean cacheEnabled;

  @Value("${http.cache.ttl:500}")
  private int cacheTtl;

  @Value("${http.cache.size:50}")
  private int cacheSize;

  private final Environment environment;
  private final RateLimitInfoHolder rateLimitInfoHolder;

  /**
   * Builds an OkHttpClient.Builder with cache, logging interceptor, rate limit interceptor, and
   * timeouts.
   *
   * @return the OkHttpClient.Builder
   */
  private OkHttpClient.Builder buildOkHttpClientBuilder() {
    // Set up a logging interceptor for debugging
    HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
    if (environment.acceptsProfiles("debug")) {
      log.warn("Requests to GitHub will be logged with full body. Use only for debugging!");
      loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
    } else {
      log.info("Requests to GitHub will be logged with basic information.");
      loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
    }

    OkHttpClient.Builder builder = new OkHttpClient.Builder();

    // Add the rate limit interceptor
    builder.addInterceptor(new HttpClientRateLimitInterceptor(rateLimitInfoHolder));

    if (cacheEnabled) {
      File cacheDir = new File("./build/github-cache");
      Cache cache = new Cache(cacheDir, cacheSize * 1024L * 1024L);
      builder.cache(cache);
      log.info("Cache enabled with TTL {} seconds and size {} MB", cacheTtl, cacheSize);
    } else {
      log.info("Cache is disabled");
    }

    // Configure timeouts and add the logging interceptor
    builder
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor);

    return builder;
  }

  /**
   * Creates an OkHttpClient bean.
   *
   * @return the OkHttpClient
   */
  @Bean
  public OkHttpClient okHttpClient() {
    return buildOkHttpClientBuilder().build();
  }
}
