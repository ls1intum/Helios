package de.tum.cit.aet.helios.http;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * Holds the latest rate limit information retrieved from GitHub API responses.
 *
 * <p>This class is used by the `HttpClientRateLimitInterceptor` to store the most recent rate limit
 * information, such as the limit, remaining requests, used requests, and reset time.
 * The interceptor updates this information every time a GitHub API request is made,
 * ensuring that the application always has the latest rate limit status.
 *
 * <p>Potential usage of this class includes:
 * <ul>
 *   <li>Monitoring the current rate limit status from anywhere in the application by accessing the
 *   `latestRateLimitInfo` field.</li>
 *   <li>Logging or displaying the current rate limit status for debugging
 *   or informational purposes.</li>
 *   <li>Implementing custom logic based on the rate limit status, such as delaying requests or
 *   notifying users when the rate limit is close to being reached.</li>
 * </ul>
 */
@Getter
@Setter
@Component
public class RateLimitInfoHolder {
  private volatile RateLimitInfo latestRateLimitInfo;
}
