package de.tum.cit.aet.helios.util;

import de.tum.cit.aet.helios.http.RateLimitInfo;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class RateLimitUtil {

  private static final String DATE_PATTERN = "dd/MM/yyyy HH:mm:ss";

  /**
   * Builds a formatted rate limit message.
   *
   * @param label a label to prefix the message
   * @param info  the RateLimitInfo (maybe null)
   * @return a formatted string describing the rate limit
   */
  public static String formatRateLimitMessage(String label, RateLimitInfo info) {
    if (info == null || info.getReset() == null) {
      return String.format("%s Rate Limit: Not available", label);
    }
    // Convert the reset Instant to OffsetDateTime for UTC and CET
    OffsetDateTime resetUtc = DateUtil.convertToOffsetDateTime(info.getReset());
    OffsetDateTime resetCet = DateUtil.convertToOffsetDateTime(info.getReset(), ZoneId.of("CET"));
    // Format both dates using the shared DateUtil
    String formattedUtc = DateUtil.format(resetUtc, DATE_PATTERN);
    String formattedCet = DateUtil.format(resetCet, DATE_PATTERN);
    return String.format(
        "%s Rate Limit: limit=%d, remaining=%d, used=%d, resets at %s (UTC), %s (CET)",
        label, info.getLimit(), info.getRemaining(), info.getUsed(), formattedUtc, formattedCet);
  }

  /**
   * Returns a formatted summary comparing the rate limit before and after an operation.
   * The token usage is calculated based on the difference in remaining tokens.
   *
   * @param before the RateLimitInfo snapshot before the operation.
   * @param after  the RateLimitInfo snapshot after the operation.
   * @return a formatted string summarizing the changes.
   */
  public static String formatRateLimitSummary(RateLimitInfo before, RateLimitInfo after) {
    if (before == null || after == null) {
      return "Rate limit summary: Not available";
    }

    // Calculate tokens used as the difference in remaining tokens
    int tokensUsed = before.getRemaining() - after.getRemaining();
    return String.format(
        "Rate Limit Summary: Before Sync: [%s] | After Sync: [%s] | Total tokens used: %d",
        formatRateLimitMessage("Before Sync", before),
        formatRateLimitMessage("After Sync", after),
        tokensUsed);
  }
}