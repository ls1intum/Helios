package de.tum.cit.aet.helios.workflow.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataIntegrityViolationException;

@Timeout(30)
class WorkflowRunProcessingGuardTest {

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String NOT_NULL_VIOLATION = "23502";

  private WorkflowRunProcessingGuard guard;
  private ExecutorService pool;

  @BeforeEach
  void setUp() {
    guard = new WorkflowRunProcessingGuard();
    pool = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
  }

  @Test
  void attemptsForTheSameRunDoNotOverlap() throws Exception {
    var firstInside = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var secondEntered = new AtomicBoolean();

    Future<String> first =
        pool.submit(
            () ->
                guard.execute(
                    42L,
                    () -> {
                      firstInside.countDown();
                      awaitUnchecked(releaseFirst);
                      return "first";
                    }));
    assertTrue(firstInside.await(10, TimeUnit.SECONDS));

    Future<String> second =
        pool.submit(
            () ->
                guard.execute(
                    42L,
                    () -> {
                      secondEntered.set(true);
                      return "second";
                    }));

    // The second attempt must stay blocked for as long as the first one is still running.
    assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
    assertFalse(secondEntered.get());

    releaseFirst.countDown();
    assertEquals("first", first.get(10, TimeUnit.SECONDS));
    assertEquals("second", second.get(10, TimeUnit.SECONDS));
    assertTrue(secondEntered.get());
  }

  @Test
  void attemptsForDifferentRunsRunConcurrently() throws Exception {
    var firstInside = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);

    Future<String> first =
        pool.submit(
            () ->
                guard.execute(
                    1L,
                    () -> {
                      firstInside.countDown();
                      awaitUnchecked(releaseFirst);
                      return "first";
                    }));
    assertTrue(firstInside.await(10, TimeUnit.SECONDS));

    // Completes while the first attempt is still holding its run's lock.
    Future<String> second = pool.submit(() -> guard.execute(2L, () -> "second"));
    assertEquals("second", second.get(10, TimeUnit.SECONDS));
    assertFalse(first.isDone());

    releaseFirst.countDown();
    assertEquals("first", first.get(10, TimeUnit.SECONDS));
  }

  @Test
  void retriesOnceWhenTheAttemptLosesADuplicateKeyRace() {
    var attempts = new AtomicInteger();

    String result =
        guard.execute(
            7L,
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw uniqueViolation();
              }
              return "stored";
            });

    assertEquals("stored", result);
    assertEquals(2, attempts.get());
  }

  @Test
  void givesUpAfterTheSecondDuplicateKeyFailure() {
    var attempts = new AtomicInteger();
    var failure = uniqueViolation();

    var thrown =
        assertThrows(
            DataIntegrityViolationException.class,
            () ->
                guard.execute(
                    7L,
                    () -> {
                      attempts.incrementAndGet();
                      throw failure;
                    }));

    assertSame(failure, thrown);
    assertEquals(2, attempts.get());
  }

  @Test
  void doesNotRetryOtherIntegrityViolations() {
    var attempts = new AtomicInteger();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            guard.execute(
                7L,
                () -> {
                  attempts.incrementAndGet();
                  throw new DataIntegrityViolationException(
                      "null value",
                      new ConstraintViolationException(
                          "null value", new SQLException("null value", NOT_NULL_VIOLATION), "c"));
                }));

    assertEquals(1, attempts.get());
  }

  @Test
  void doesNotRetryUnrelatedFailures() {
    var attempts = new AtomicInteger();

    assertThrows(
        IllegalStateException.class,
        () ->
            guard.execute(
                7L,
                () -> {
                  attempts.incrementAndGet();
                  throw new IllegalStateException("boom");
                }));

    assertEquals(1, attempts.get());
  }

  @Test
  void releasesTheLockWhenAnAttemptFails() throws Exception {
    assertThrows(
        IllegalStateException.class,
        () ->
            guard.execute(
                7L,
                () -> {
                  throw new IllegalStateException("boom");
                }));

    // A later attempt for the same run must not be blocked by the failed one.
    Future<String> next = pool.submit(() -> guard.execute(7L, () -> "ok"));
    assertEquals("ok", next.get(10, TimeUnit.SECONDS));
  }

  @Test
  void recognizesUniqueViolationsAnywhereInTheCauseChain() {
    assertTrue(WorkflowRunProcessingGuard.isUniqueViolation(uniqueViolation()));
    assertTrue(
        WorkflowRunProcessingGuard.isUniqueViolation(
            new RuntimeException("outer", new SQLException("dup", UNIQUE_VIOLATION))));
    assertFalse(
        WorkflowRunProcessingGuard.isUniqueViolation(
            new SQLException("null value", NOT_NULL_VIOLATION)));
    assertFalse(WorkflowRunProcessingGuard.isUniqueViolation(new RuntimeException("plain")));
  }

  /** Mirrors the real chain: Spring exception -> Hibernate exception -> JDBC driver exception. */
  private static DataIntegrityViolationException uniqueViolation() {
    return new DataIntegrityViolationException(
        "could not execute statement",
        new ConstraintViolationException(
            "could not execute statement",
            new SQLException("duplicate key value violates unique constraint", UNIQUE_VIOLATION),
            "workflow_run_pkey"));
  }

  private static void awaitUnchecked(CountDownLatch latch) {
    try {
      latch.await(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
