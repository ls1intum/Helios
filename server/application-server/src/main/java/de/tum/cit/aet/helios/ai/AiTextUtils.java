package de.tum.cit.aet.helios.ai;

import java.util.List;

public final class AiTextUtils {

  private AiTextUtils() {}

  public static String truncate(String value, int maxChars) {
    if (value == null) {
      return null;
    }
    if (value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars) + "\n...[truncated]";
  }

  public static String nullableText(String value) {
    if (value == null || value.isBlank()) {
      return "N/A";
    }
    return value;
  }

  public static List<String> limitList(List<String> value, int limit) {
    if (value == null || value.isEmpty()) {
      return List.of();
    }
    return value.stream().limit(Math.max(1, limit)).toList();
  }
}
