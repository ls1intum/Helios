package de.tum.cit.aet.helios.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum of all possible lifecycle states that a monitored Spring Boot service
 * can report to Helios.
 *
 * <p>This enum is serialized/deserialized as lowercase, kebab-case values.
 * Unknown inputs will throw an {@link IllegalArgumentException}.</p>
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
   * Maps a raw string from JSON to the corresponding enum constant.
   *
   * @param raw lowercase or kebab-case lifecycle state from incoming payload
   * @return corresponding {@code LifecycleState}
   * @throws IllegalArgumentException if the input does not map to any known state
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
   * Serializes the enum to its name in JSON format.
   *
   * @return the string representation of the enum
   */
  @JsonValue
  public String toJson() {
    return name();
  }
}