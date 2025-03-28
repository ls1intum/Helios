package de.tum.cit.aet.helios.common.http;

import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

/**
 * An OkHttp interceptor that extracts rate limit information from GitHub API responses and updates
 * the rate limit info holder.
 */
@Log4j2
@RequiredArgsConstructor
public class HttpClientRateLimitInterceptor implements Interceptor {

  private static final String GITHUB_API_HOST = "api.github.com";

  // https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2022-11-28#checking-the-status-of-your-rate-limit
  private static final String HEADER_RATE_LIMIT = "x-ratelimit-limit";
  private static final String HEADER_RATE_REMAINING = "x-ratelimit-remaining";
  private static final String HEADER_RATE_USED = "x-ratelimit-used";
  private static final String HEADER_RATE_RESET = "x-ratelimit-reset";

  private final RateLimitInfoHolder rateLimitInfoHolder;

  @NotNull
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    HttpUrl url = request.url();
    Response response = chain.proceed(request);

    if (url.host().equalsIgnoreCase(GITHUB_API_HOST)) {
      // This is a GitHub API request, extract GitHub-specific rate limit headers.
      final String limitHeader = response.header(HEADER_RATE_LIMIT);
      final String remainingHeader = response.header(HEADER_RATE_REMAINING);
      final String usedHeader = response.header(HEADER_RATE_USED);
      final String resetHeader = response.header(HEADER_RATE_RESET);

      if (limitHeader != null
          && remainingHeader != null
          && usedHeader != null
          && resetHeader != null) {

        // Update the rate limit info in the holder
        final RateLimitInfo info = new RateLimitInfo();

        try {
          info.setLimit(Integer.parseInt(limitHeader));
        } catch (NumberFormatException e) {
          log.warn("Unable to parse '{}' header value: {}", HEADER_RATE_LIMIT, limitHeader, e);
        }

        try {
          info.setRemaining(Integer.parseInt(remainingHeader));
        } catch (NumberFormatException e) {
          log.warn(
              "Unable to parse '{}' header value: {}", HEADER_RATE_REMAINING, remainingHeader, e);
        }

        try {
          info.setUsed(Integer.parseInt(usedHeader));
        } catch (NumberFormatException e) {
          log.warn("Unable to parse '{}' header value: {}", HEADER_RATE_USED, usedHeader, e);
        }

        try {
          // Convert the reset time (in seconds since epoch) to an Instant.
          long resetEpochSeconds = Long.parseLong(resetHeader);
          Instant resetInstant = Instant.ofEpochSecond(resetEpochSeconds);
          info.setReset(resetInstant);
        } catch (NumberFormatException e) {
          log.warn("Unable to parse '{}' header value: {}", HEADER_RATE_RESET, resetHeader, e);
        }

        rateLimitInfoHolder.setLatestRateLimitInfo(info);
        String rateLimitMessage = RateLimitUtil.formatRateLimitMessage("GitHub API", info);
        log.info(rateLimitMessage);
      } else {
        log.debug("GitHub rate limit headers not found for request: {}", request.url());
      }
    }

    return response;
  }
}
