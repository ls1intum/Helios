package de.tum.cit.aet.helios.common.http;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the rate limit information retrieved from GitHub API responses.
 *
 * <p>This class holds the rate limit details such as the limit, remaining requests, used requests,
 * and the reset time. It is used by the `HttpClientRateLimitInterceptor` to store the most recent
 * rate limit information.
 *
 * <p>Fields:
 *
 * <ul>
 *   <li>limit - The maximum number of requests allowed in the current rate limit window.
 *   <li>remaining - The number of requests remaining in the current rate limit window.
 *   <li>used - The number of requests used in the current rate limit window.
 *   <li>reset - The time at which the current rate limit window resets, represented as an
 *       `Instant`.
 * </ul>
 */
@Getter
@Setter
public class RateLimitInfo {
  private int limit;
  private int remaining;
  private int used;
  private Instant reset;
}
