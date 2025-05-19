package de.tum.cit.aet.helios.environment.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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

  // Keep enum constant names when Spring/Jackson serialises to JSON
  @JsonValue
  public String toJson() {
    return name();
  }
}