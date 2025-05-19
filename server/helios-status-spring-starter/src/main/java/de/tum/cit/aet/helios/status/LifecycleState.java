package de.tum.cit.aet.helios.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle markers that a Spring-Boot service can expose.
 *
 * <p>The enum comes with a strict text deserializer – any unknown value
 * throws an {@link IllegalArgumentException} – and preserves its exact
 * constant name when serialised to JSON.</p>
 */
public enum LifecycleState {

  STARTING_UP,
  DB_MIGRATION_STARTED,
  DB_MIGRATION_FAILED,
  DB_MIGRATION_FINISHED,
  RUNNING,
  DEGRADED,
  SHUTTING_DOWN,
  STOPPED,
  FAILED;

  /**
   * Case-insensitive mapping from payload string → enum constant.
   */
  @JsonCreator
  public static LifecycleState fromJson(String raw) {
    return switch (raw.toLowerCase()) {
      case "starting_up" -> STARTING_UP;
      case "db_migration_started",
           "running_migration" -> DB_MIGRATION_STARTED;
      case "db_migration_failed" -> DB_MIGRATION_FAILED;
      case "db_migration_finished" -> DB_MIGRATION_FINISHED;
      case "running" -> RUNNING;
      case "degraded" -> DEGRADED;
      case "shutting_down" -> SHUTTING_DOWN;
      case "stopped" -> STOPPED;
      case "failed",
           "failure" -> FAILED;
      default -> throw new IllegalArgumentException("Unknown lifecycle state: " + raw);
    };
  }

  /**
   * Preserve enum constant names in outbound JSON.
   */
  @JsonValue
  public String toJson() {
    return name();
  }
}