package de.tum.cit.aet.helios.ai.testfailure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class TestFailureAnalysisCleanupTaskTest {

  @Test
  void purgeDeletesOldRowsInDeleteMode() {
    TestFailureAnalysisRepository repository = Mockito.mock(TestFailureAnalysisRepository.class);
    when(repository.countByUpdatedAtBefore(any(OffsetDateTime.class))).thenReturn(4L);
    when(repository.deleteByUpdatedAtBefore(any(OffsetDateTime.class))).thenReturn(4L);

    TestFailureAnalysisCleanupTask task = createTask(repository, Duration.ofDays(14), false);
    OffsetDateTime lowerBound = OffsetDateTime.now().minusDays(14);

    task.purge();

    OffsetDateTime upperBound = OffsetDateTime.now().minusDays(14);
    ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

    verify(repository).countByUpdatedAtBefore(cutoffCaptor.capture());
    OffsetDateTime cutoff = cutoffCaptor.getValue();
    assertFalse(cutoff.isBefore(lowerBound));
    assertFalse(cutoff.isAfter(upperBound));
    verify(repository).deleteByUpdatedAtBefore(cutoff);
  }

  @Test
  void purgeOnlyCountsRowsInDryRunMode() {
    TestFailureAnalysisRepository repository = Mockito.mock(TestFailureAnalysisRepository.class);
    TestFailureAnalysisCleanupTask task = createTask(repository, Duration.ofDays(14), true);
    OffsetDateTime lowerBound = OffsetDateTime.now().minusDays(14);

    task.purge();

    OffsetDateTime upperBound = OffsetDateTime.now().minusDays(14);
    ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

    verify(repository).countByUpdatedAtBefore(cutoffCaptor.capture());
    OffsetDateTime cutoff = cutoffCaptor.getValue();
    assertFalse(cutoff.isBefore(lowerBound));
    assertFalse(cutoff.isAfter(upperBound));
    verifyNoMoreInteractions(repository);
  }

  private TestFailureAnalysisCleanupTask createTask(
      TestFailureAnalysisRepository repository, Duration maxAge, boolean dryRun) {
    TestFailureAnalysisCleanupTask task = new TestFailureAnalysisCleanupTask(repository);
    ReflectionTestUtils.setField(task, "maxAge", maxAge);
    ReflectionTestUtils.setField(task, "dryRun", dryRun);
    return task;
  }
}
