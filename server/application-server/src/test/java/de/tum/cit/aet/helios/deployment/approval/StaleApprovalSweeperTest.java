package de.tum.cit.aet.helios.deployment.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

class StaleApprovalSweeperTest {

  private final DeploymentApprovalRequestRepository repository =
      mock(DeploymentApprovalRequestRepository.class);
  private final Environment environment = mock(Environment.class);
  private final StaleApprovalSweeper sweeper = new StaleApprovalSweeper(repository, environment);

  @Test
  void archivesOpenRequestsOlderThanGitHubsApprovalWindow() {
    sweeper.sweep();

    ArgumentCaptor<OffsetDateTime> cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<DeploymentApprovalRequest.State>> states =
        ArgumentCaptor.forClass(Collection.class);
    verify(repository)
        .expireOpenRequestsCreatedBefore(
            cutoff.capture(),
            any(OffsetDateTime.class),
            eq(StaleApprovalSweeper.EXPIRY_REASON),
            eq(DeploymentApprovalRequest.State.EXPIRED),
            states.capture());

    Duration age = Duration.between(cutoff.getValue(), OffsetDateTime.now());
    assertTrue(age.minus(DeploymentApprovalRequest.GITHUB_APPROVAL_WINDOW).abs().toSeconds() < 5);
    assertEquals(
        java.util.Set.of(
            DeploymentApprovalRequest.State.PENDING,
            DeploymentApprovalRequest.State.FAILED_AT_GITHUB),
        java.util.Set.copyOf(states.getValue()));
  }

  @Test
  void doesNothingUnderTheOpenApiProfile() {
    when(environment.matchesProfiles("openapi")).thenReturn(true);

    sweeper.sweep();

    verify(repository, never())
        .expireOpenRequestsCreatedBefore(
            any(), any(), anyString(), any(), anyCollection());
  }
}
