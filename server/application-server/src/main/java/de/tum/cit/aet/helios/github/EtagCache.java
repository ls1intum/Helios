package de.tum.cit.aet.helios.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Conditional-GET cache for GitHub REST. Maps request URL → last seen ETag + parsed body.
 *
 * <p>Callers should send {@code If-None-Match} with {@link #getEtag(String)} and, on {@code 304},
 * reuse {@link #getBody(String)}.
 */
@Component
public class EtagCache {

  private final Cache<String, Entry<?>> entries =
      Caffeine.newBuilder()
          .expireAfterWrite(Duration.ofHours(6))
          .maximumSize(5_000)
          .build();

  public Optional<String> getEtag(String url) {
    Entry<?> e = entries.getIfPresent(url);
    return e == null ? Optional.empty() : Optional.ofNullable(e.etag());
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> getBody(String url, Class<T> type) {
    Entry<?> e = entries.getIfPresent(url);
    if (e == null || e.body() == null) {
      return Optional.empty();
    }
    if (!type.isInstance(e.body())) {
      return Optional.empty();
    }
    return Optional.of((T) e.body());
  }

  public <T> void put(String url, String etag, T body) {
    entries.put(url, new Entry<>(etag, body));
  }

  public void invalidate(String url) {
    entries.invalidate(url);
  }

  private record Entry<T>(String etag, T body) {}
}
