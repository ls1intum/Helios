package de.tum.cit.aet.notification.nats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NatsMessageHandlerRegistry {
  private final Map<String, NatsMessageHandler<?>> handlerMap = new HashMap<>();

  public NatsMessageHandlerRegistry(NatsMessageHandler<?>[] handlers) {
    if (handlers != null) {
      for (NatsMessageHandler<?> handler : handlers) {
        handlerMap.put(handler.getSubjectPattern(), handler);
      }
    }
  }

  public NatsMessageHandler<?> findHandlerForSubject(String subject) {
    return handlerMap.entrySet().stream()
        .filter(entry -> matchesSubject(entry.getKey(), subject))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  /**
   * Checks if a subject matches a given pattern according to NATS subject pattern rules. The
   * pattern can include wildcards (*) which match any single token in the subject. For example: -
   * Pattern "foo.*.baz" matches "foo.bar.baz" - Pattern "foo.bar" only matches "foo.bar"
   *
   * @param pattern The pattern to match against, using dot notation with optional wildcards
   * @param subject The actual subject string to check
   * @return true if the subject matches the pattern, false otherwise
   */
  private boolean matchesSubject(String pattern, String subject) {
    final String[] patternParts = pattern.split("\\.");
    final String[] subjectParts = subject.split("\\.");

    int minLength = Math.min(patternParts.length, subjectParts.length);

    for (int i = 0; i < minLength; i++) {
      String patternPart = patternParts[i];
      String subjectPart = subjectParts[i];

      if (patternPart.equals("*")) {
        continue;
      }

      if (!patternPart.equals(subjectPart)) {
        return false;
      }
    }

    return patternParts.length == subjectParts.length;
  }

  public List<String> getSupportedSubjects() {
    return new ArrayList<>(handlerMap.keySet());
  }
}
