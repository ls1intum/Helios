package de.tum.cit.aet.helios.environment.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LifecycleState {

  STARTING_UP,
  MIGRATING_DB,
  MIGRATION_FAILED,
  MIGRATION_FINISHED,
  RUNNING,
  DEGRADED,
  SHUTTING_DOWN,
  STOPPED,
  FAILED;

  @JsonCreator
  public static LifecycleState fromJson(String raw) {
    return switch (raw.toLowerCase()) {
      case "starting_up" -> STARTING_UP;
      case "migrating_db",
           "running_migration" -> MIGRATING_DB;
      case "migration_failed" -> MIGRATION_FAILED;
      case "migration_finished" -> MIGRATION_FINISHED;
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