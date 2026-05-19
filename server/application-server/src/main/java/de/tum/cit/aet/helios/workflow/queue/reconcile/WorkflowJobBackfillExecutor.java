package de.tum.cit.aet.helios.workflow.queue.reconcile;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper that exists solely to give Spring's {@code @Async} proxy somewhere external to
 * intercept. Without this, calling {@code @Async} from within {@code WorkflowJobBackfillService}
 * would self-invoke the bean and run synchronously on the request thread.
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class WorkflowJobBackfillExecutor {

  private final WorkflowJobBackfillService backfillService;

  @Async
  public void runAsync() {
    try {
      backfillService.runBackfill();
    } catch (Exception e) {
      log.error("Backfill failed", e);
    }
  }
}
