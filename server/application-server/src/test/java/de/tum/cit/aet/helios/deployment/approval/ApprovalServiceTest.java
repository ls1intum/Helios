package de.tum.cit.aet.helios.deployment.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.environment.EnvironmentService.ReviewerResolution;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment.AutoApprovalDecision;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApprovalServiceTest {

  private static final long DEPLOYMENT_ID = 42L;
  private static final long HELIOS_DEPLOYMENT_ROW_ID = 1L;
  private static final long WORKFLOW_RUN_ID = 9999L;
  private static final long ENV_ID = 7L;
  private static final String ENV_NAME = "production";
  private static final String REPO = "ls1intum/Helios";
  private static final String DEPLOYER = "alice";

  @Test
  void autoApprovesWhenDeployerIsRequiredReviewer() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    f.reviewersAre(reviewers("alice", "bob")); // alice = deployer

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            eq(REPO),
            eq(WORKFLOW_RUN_ID),
            eq(ENV_ID),
            eq(DEPLOYER),
            anyString());
    assertEquals(AutoApprovalDecision.AUTO_APPROVED, f.heliosDeployment.getAutoApprovalDecision());

    ArgumentCaptor<DeploymentApprovalRequest> rowCaptor =
        ArgumentCaptor.forClass(DeploymentApprovalRequest.class);
    verify(f.approvalRequestRepository).save(rowCaptor.capture());
    DeploymentApprovalRequest row = rowCaptor.getValue();
    assertEquals(DeploymentApprovalRequest.State.APPROVED, row.getState());
    assertEquals(DeploymentApprovalRequest.Via.AUTO, row.getVia());
    assertEquals(DEPLOYER, row.getReviewerLogin());
  }

  @Test
  void defersWhenPreventSelfReviewBlocksDeployer() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    f.reviewersAre(
        new ReviewerResolution(true, Set.of("alice", "bob"), Set.of())); // self-review blocked

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    assertEquals(
        AutoApprovalDecision.DEFERRED_TO_REVIEWERS, f.heliosDeployment.getAutoApprovalDecision());
    // No AUTO audit row created when we never attempted to approve.
    verify(f.approvalRequestRepository, never()).save(any());
  }

  @Test
  void defersWhenDeployerIsNotARequiredReviewer() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    f.reviewersAre(reviewers("bob", "carol")); // alice not listed

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    assertEquals(
        AutoApprovalDecision.DEFERRED_TO_REVIEWERS, f.heliosDeployment.getAutoApprovalDecision());
  }

  @Test
  void noopsWhenEnvironmentHasNoRequiredReviewerRule() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    when(f.environmentService.resolveReviewers(ENV_ID)).thenReturn(Optional.empty());

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    assertEquals(
        AutoApprovalDecision.NOT_APPLICABLE, f.heliosDeployment.getAutoApprovalDecision());
  }

  @Test
  void teamReviewerFallsBackToLegacyImpersonation() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    // Team-only rule (no user logins) — Helios doesn't yet expand teams, so we attempt the
    // legacy "impersonate deployer" path and stamp TEAM_REVIEWER_FALLBACK.
    f.reviewersAre(new ReviewerResolution(false, Set.of(), Set.of("maintainers")));

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            eq(REPO), eq(WORKFLOW_RUN_ID), eq(ENV_ID), eq(DEPLOYER), anyString());
    assertEquals(
        AutoApprovalDecision.TEAM_REVIEWER_FALLBACK,
        f.heliosDeployment.getAutoApprovalDecision());
  }

  @Test
  void recordsFailureWhenGitHubRejectsAutoApproval() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    f.reviewersAre(reviewers("alice", "bob"));
    doThrow(new IOException("HTTP 403"))
        .when(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            anyString(), anyLong(), any(), anyString(), anyString());

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    // We attempted, GitHub rejected, the audit row carries FAILED_AT_GITHUB so the deployment
    // can still be resolved by a reviewer in Phase 2.
    ArgumentCaptor<DeploymentApprovalRequest> rowCaptor =
        ArgumentCaptor.forClass(DeploymentApprovalRequest.class);
    verify(f.approvalRequestRepository).save(rowCaptor.capture());
    assertEquals(
        DeploymentApprovalRequest.State.FAILED_AT_GITHUB, rowCaptor.getValue().getState());
    assertEquals(
        AutoApprovalDecision.DEFERRED_TO_REVIEWERS, f.heliosDeployment.getAutoApprovalDecision());
  }

  @Test
  void skipsNonHeliosDeploymentAfterRetries() throws IOException {
    Fixture f = new Fixture();
    when(f.heliosDeploymentRepository.findByDeploymentId(DEPLOYMENT_ID))
        .thenReturn(Optional.empty());

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
  }

  @Test
  void retryCatchesDeploymentThatWasPersistedJustBefore() throws IOException {
    Fixture f = new Fixture();
    // The first lookup misses (the deploy-side transaction hadn't committed yet), the next finds
    // the row.
    when(f.heliosDeploymentRepository.findByDeploymentId(DEPLOYMENT_ID))
        .thenReturn(Optional.empty(), Optional.of(f.heliosDeployment));
    f.reviewersAre(reviewers("alice"));

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            eq(REPO), eq(WORKFLOW_RUN_ID), eq(ENV_ID), eq(DEPLOYER), anyString());
    assertEquals(AutoApprovalDecision.AUTO_APPROVED, f.heliosDeployment.getAutoApprovalDecision());
  }

  @Test
  void skipsWhenWorkflowRunIdIsMissing() throws IOException {
    Fixture f = new Fixture();
    f.heliosDeployment.setWorkflowRunId(null);
    f.deploymentExists();

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    verify(f.environmentService, never()).resolveReviewers(any());
  }

  private static ReviewerResolution reviewers(String... logins) {
    return new ReviewerResolution(false, Set.of(logins), Set.of());
  }

  /** Wires the service's collaborators as bare mocks; each test stubs only what it needs. */
  private static final class Fixture {
    final GitHubService gitHubService = mock(GitHubService.class);
    final HeliosDeploymentRepository heliosDeploymentRepository =
        mock(HeliosDeploymentRepository.class);
    final EnvironmentService environmentService = mock(EnvironmentService.class);
    final DeploymentApprovalRequestRepository approvalRequestRepository =
        mock(DeploymentApprovalRequestRepository.class);

    final DeploymentSource source = mock(DeploymentSource.class);
    final GitRepository gitRepository = mock(GitRepository.class);
    final Environment environment = mock(Environment.class);
    final User user = mock(User.class);

    final User creator = creatorWithLogin(DEPLOYER);
    final HeliosDeployment heliosDeployment = newHeliosDeployment(creator);

    Fixture() {
      when(source.getId()).thenReturn(DEPLOYMENT_ID);
      when(gitRepository.getNameWithOwner()).thenReturn(REPO);
      when(environment.getId()).thenReturn(ENV_ID);
      when(environment.getName()).thenReturn(ENV_NAME);
      // approvalRequestRepository.save just returns its argument
      when(approvalRequestRepository.save(any(DeploymentApprovalRequest.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(heliosDeploymentRepository.save(any(HeliosDeployment.class)))
          .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Make the default findByDeploymentId return the test deployment. */
    void deploymentExists() {
      reset(heliosDeploymentRepository);
      when(heliosDeploymentRepository.findByDeploymentId(DEPLOYMENT_ID))
          .thenReturn(Optional.of(heliosDeployment));
      when(heliosDeploymentRepository.save(any(HeliosDeployment.class)))
          .thenAnswer(inv -> inv.getArgument(0));
    }

    void reviewersAre(ReviewerResolution resolution) {
      when(environmentService.resolveReviewers(ENV_ID)).thenReturn(Optional.of(resolution));
    }

    ApprovalService service() {
      return new ApprovalService(
          gitHubService, heliosDeploymentRepository, environmentService, approvalRequestRepository);
    }
  }

  private static User creatorWithLogin(String login) {
    User u = new User();
    u.setLogin(login);
    return u;
  }

  private static HeliosDeployment newHeliosDeployment(User creator) {
    HeliosDeployment d = new HeliosDeployment();
    d.setId(HELIOS_DEPLOYMENT_ROW_ID);
    d.setCreator(creator);
    d.setWorkflowRunId(WORKFLOW_RUN_ID);
    return d;
  }
}
