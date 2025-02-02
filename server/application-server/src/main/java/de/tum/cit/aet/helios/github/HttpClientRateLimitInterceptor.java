package de.tum.cit.aet.helios.github;

import java.io.IOException;
import java.time.Instant;
import lombok.extern.log4j.Log4j2;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class HttpClientRateLimitInterceptor implements Interceptor {

  private static final String GITHUB_API_HOST = "api.github.com";

  // https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2022-11-28#checking-the-status-of-your-rate-limit
  private static final String HEADER_RATE_LIMIT = "x-ratelimit-limit";
  private static final String HEADER_RATE_REMAINING = "x-ratelimit-remaining";
  private static final String HEADER_RATE_USED = "x-ratelimit-used";
  private static final String HEADER_RATE_RESET = "x-ratelimit-reset";

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

      if (limitHeader != null && remainingHeader != null) {
        StringBuilder logMessage = new StringBuilder()
            .append("GitHub API Rate Limit: ")
            .append(limitHeader)
            .append(" | Remaining: ")
            .append(remainingHeader)
            .append(" | Used: ")
            .append(usedHeader);

        if (resetHeader != null) {
          try {
            // Convert the reset time (in seconds since epoch) to an Instant object.
            long resetEpochSeconds = Long.parseLong(resetHeader);
            Instant resetInstant = Instant.ofEpochSecond(resetEpochSeconds);
            logMessage.append(" | Resets at: ").append(resetInstant);
          } catch (NumberFormatException e) {
            log.warn("Unable to parse '{}' header value: {}", HEADER_RATE_RESET, resetHeader, e);
          }
        }
        log.info(logMessage.toString());
      } else {
        log.debug("GitHub rate limit headers not found for request: {}", request.url());
      }
    }

    return response;
  }
}