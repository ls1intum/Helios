package de.tum.cit.aet.helios.workflow.github;

import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Makes concurrent processing of the same GitHub workflow run safe.
 *
 * <p>GitHub sends several events for one run within moments (requested, in_progress, completed),
 * and the periodic data sync touches the same runs. Without protection, two threads can both see
 * that a run does not exist yet and both insert it, so one of them fails with a duplicate key on
 * {@code workflow_run_pkey}. Because the work also has side effects (deployment status updates and
 * failure notifications), simply discarding the failed attempt is not enough.
 *
 * <p>This guard therefore does two things:
 *
 * <ol>
 *   <li><b>Serializes attempts per run id.</b> A second attempt for the same run waits until the
 *       first one has committed, so it finds the row and updates it instead of inserting, and it
 *       sees the committed deployment state instead of notifying a second time.
 *   <li><b>Retries once on a unique violation.</b> If another application instance (or a code path
 *       that does not use this guard) inserted the same row, the retry runs in a fresh transaction
 *       and takes the update path.
 * </ol>
 *
 * <p>The supplied attempt must run in its <em>own</em> transaction that is committed before it
 * returns; otherwise the lock is released before the commit and neither mechanism works. Locks are
 * striped by run id, so unrelated runs rarely wait for each other and memory use stays bounded.
 */
@Log4j2
final class WorkflowRunProcessingGuard {

  /** PostgreSQL / SQL standard SQLSTATE for {@code unique_violation}. */
  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

  private static final int STRIPE_COUNT = 256;

  private static final int MAX_CAUSE_DEPTH = 16;

  private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];

  WorkflowRunProcessingGuard() {
    for (int i = 0; i < stripes.length; i++) {
      stripes[i] = new ReentrantLock();
    }
  }

  /**
   * Runs the given attempt exclusively for the run id, retrying once if it loses a duplicate-key
   * race.
   *
   * @param runId the GitHub workflow run id
   * @param attempt a transactional unit of work that is committed when it returns
   * @param <T> the result type
   * @return the result of the successful attempt
   */
  <T> T execute(long runId, Supplier<T> attempt) {
    ReentrantLock lock = stripes[stripeIndex(runId)];
    lock.lock();
    try {
      try {
        return attempt.get();
      } catch (DataIntegrityViolationException e) {
        if (!isUniqueViolation(e)) {
          throw e;
        }
        log.info(
            "Workflow run {} was inserted concurrently by another writer, retrying once", runId);
        return attempt.get();
      }
    } finally {
      lock.unlock();
    }
  }

  private static int stripeIndex(long runId) {
    return Long.hashCode(runId) & (STRIPE_COUNT - 1);
  }

  static boolean isUniqueViolation(Throwable throwable) {
    Throwable current = throwable;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof SQLException sqlException
          && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
        return true;
      }
      if (current.getCause() == current) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }
}
