package de.tum.cit.aet.helios.status.listeners;

import de.tum.cit.aet.helios.HeliosClient;
import de.tum.cit.aet.helios.status.LifecycleState;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(name = "org.flywaydb.core.Flyway")
class FlywayCallback implements Callback {

  private final HeliosClient helios;

  FlywayCallback(HeliosClient helios) {
    this.helios = helios;
  }

  @Override
  public boolean supports(Event event, Context context) {
    // We handle three events only; ignore the others quickly.
    return switch (event) {
      case BEFORE_MIGRATE,
           AFTER_MIGRATE,
           AFTER_MIGRATE_ERROR -> true;
      default -> false;
    };
  }

  @Override
  public boolean canHandleInTransaction(Event event, Context context) {
    // Safe to run inside Flyway's transaction.
    return true;
  }

  @Override
  public void handle(Event event, Context context) {
    LifecycleState state = switch (event) {
      case BEFORE_MIGRATE -> LifecycleState.MIGRATING_DB;
      case AFTER_MIGRATE -> LifecycleState.MIGRATION_FINISHED;
      case AFTER_MIGRATE_ERROR -> LifecycleState.MIGRATION_FAILED;
      default -> null;
    };

    // fire‑and‑forget; if you need blocking behaviour, replace subscribe() with block().
    if (state != null) {
      helios.push(state).subscribe();
    }
  }

  @Override
  public String getCallbackName() {
    return "helios-status-flyway-callback";
  }
}
