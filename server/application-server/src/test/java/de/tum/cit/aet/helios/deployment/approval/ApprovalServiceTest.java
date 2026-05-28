package de.tum.cit.aet.helios.deployment.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.time.OffsetDateTime;
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
    f.reviewersAre(reviewers("alice", "bob"));

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            eq(REPO), eq(WORKFLOW_RUN_ID), eq(ENV_ID), eq(DEPLOYER), anyString());

    // Audit row + stamp written atomically via the writer service.
    ArgumentCaptor<DeploymentApprovalRequest> rowCaptor =
        ArgumentCaptor.forClass(DeploymentApprovalRequest.class);
    verify(f.decisionWriter)
        .recordSuccess(eq(f.heliosDeployment), rowCaptor.capture(),
            eq(AutoApprovalDecision.AUTO_APPROVED));
    DeploymentApprovalRequest row = rowCaptor.getValue();
    org.junit.jupiter.api.Assertions.assertEquals(DEPLOYER, row.getReviewerLogin());
    org.junit.jupiter.api.Assertions.assertEquals(
        DeploymentApprovalRequest.Via.AUTO, row.getVia());
  }

  @Test
  void defersWhenPreventSelfReviewBlocksDeployer() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    f.reviewersAre(new ReviewerResolution(true, Set.of("alice", "bob"), Set.of()));

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    verify(f.decisionWriter)
        .recordDecisionOnly(f.heliosDeployment, AutoApprovalDecision.DEFERRED_TO_REVIEWERS);
  }

  @Test
  void defersWhenDeployerIsNotARequiredReviewer() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    f.reviewersAre(reviewers("bob", "carol"));

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    verify(f.decisionWriter)
        .recordDecisionOnly(f.heliosDeployment, AutoApprovalDecision.DEFERRED_TO_REVIEWERS);
  }

  @Test
  void noopsWhenEnvironmentHasNoRequiredReviewerRule() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    when(f.environmentService.resolveReviewers(ENV_ID)).thenReturn(Optional.empty());

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    verify(f.decisionWriter)
        .recordDecisionOnly(f.heliosDeployment, AutoApprovalDecision.NOT_APPLICABLE);
  }

  @Test
  void teamReviewerFallsBackToLegacyImpersonation() throws IOException {
    Fixture f = new Fixture();
    f.deploymentExists();
    f.reviewersAre(new ReviewerResolution(false, Set.of(), Set.of("maintainers")));

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            eq(REPO), eq(WORKFLOW_RUN_ID), eq(ENV_ID), eq(DEPLOYER), anyString());
    verify(f.decisionWriter)
        .recordSuccess(eq(f.heliosDeployment), any(),
            eq(AutoApprovalDecision.TEAM_REVIEWER_FALLBACK));
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

    // The writer's recordFailure captures both the audit-row and the deployment-stamp in one
    // transaction; we verify the fallback decision is DEFERRED_TO_REVIEWERS (the user can still
    // be served via Phase-2 in-app approval).
    verify(f.decisionWriter)
        .recordFailure(
            eq(f.heliosDeployment),
            any(),
            eq(AutoApprovalDecision.DEFERRED_TO_REVIEWERS),
            eq("HTTP 403"));
    verify(f.decisionWriter, never()).recordSuccess(any(), any(), any());
  }

  @Test
  void skipsWhenDeploymentAlreadyDecided() throws IOException {
    // Idempotency: if a webhook is redelivered after we already decided (e.g. AUTO_APPROVED on
    // run 1), run 2 must not call GitHub again or stamp a different decision.
    Fixture f = new Fixture();
    f.heliosDeployment.setAutoApprovalDecision(AutoApprovalDecision.AUTO_APPROVED);
    f.heliosDeployment.setAutoApprovalAt(OffsetDateTime.now());
    f.deploymentExists();

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    verifyNoInteractions(f.decisionWriter);
    verify(f.environmentService, never()).resolveReviewers(any());
  }

  @Test
  void skipsNonHeliosDeploymentAfterRetries() throws IOException {
    Fixture f = new Fixture();
    when(f.heliosDeploymentRepository.findByDeploymentId(DEPLOYMENT_ID))
        .thenReturn(Optional.empty());

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
    verifyNoInteractions(f.decisionWriter);
  }

  @Test
  void retryCatchesDeploymentThatWasPersistedJustBefore() throws IOException {
    Fixture f = new Fixture();
    when(f.heliosDeploymentRepository.findByDeploymentId(DEPLOYMENT_ID))
        .thenReturn(Optional.empty(), Optional.of(f.heliosDeployment));
    f.reviewersAre(reviewers("alice"));

    f.service().reviewDeployment(f.source, f.gitRepository, f.environment, f.user);

    verify(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            eq(REPO), eq(WORKFLOW_RUN_ID), eq(ENV_ID), eq(DEPLOYER), anyString());
    verify(f.decisionWriter)
        .recordSuccess(eq(f.heliosDeployment), any(), eq(AutoApprovalDecision.AUTO_APPROVED));
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
    verifyNoInteractions(f.decisionWriter);
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
    final ApprovalDecisionWriter decisionWriter = mock(ApprovalDecisionWriter.class);

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
    }

    void deploymentExists() {
      reset(heliosDeploymentRepository);
      when(heliosDeploymentRepository.findByDeploymentId(DEPLOYMENT_ID))
          .thenReturn(Optional.of(heliosDeployment));
    }

    void reviewersAre(ReviewerResolution resolution) {
      when(environmentService.resolveReviewers(ENV_ID)).thenReturn(Optional.of(resolution));
    }

    ApprovalService service() {
      return new ApprovalService(
          gitHubService, heliosDeploymentRepository, environmentService, decisionWriter);
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
