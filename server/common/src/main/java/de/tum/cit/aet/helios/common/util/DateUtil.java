package de.tum.cit.aet.helios.common.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateUtil {

  public static OffsetDateTime convertToOffsetDateTime(Date date) {
    return date != null ? date.toInstant().atOffset(ZoneOffset.UTC) : null;
  }

  /**
   * Convert an Instant to OffsetDateTime using UTC by default.
   *
   * @param instant the Instant to convert
   * @return the converted OffsetDateTime
   */
  public static OffsetDateTime convertToOffsetDateTime(Instant instant) {
    return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
  }

  /**
   * Convert an Instant to OffsetDateTime using the specified ZoneId.
   *
   * @param instant the Instant to convert
   * @param zone the ZoneId to use
   * @return the converted OffsetDateTime
   */
  public static OffsetDateTime convertToOffsetDateTime(Instant instant, ZoneId zone) {
    return instant != null ? instant.atZone(zone).toOffsetDateTime() : null;
  }

  /**
   * Format an OffsetDateTime using the given pattern.
   *
   * @param offsetDateTime the OffsetDateTime to format
   * @param pattern the pattern to use
   * @return the formatted date string
   */
  public static String format(OffsetDateTime offsetDateTime, String pattern) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    return formatter.format(offsetDateTime);
  }
}
