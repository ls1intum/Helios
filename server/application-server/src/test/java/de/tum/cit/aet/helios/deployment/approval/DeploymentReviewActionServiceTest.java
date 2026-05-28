package de.tum.cit.aet.helios.deployment.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.environment.EnvironmentService.ReviewerResolution;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.user.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DeploymentReviewActionServiceTest {

  private static final long DEPLOYMENT_ID = 1L;
  private static final long WORKFLOW_RUN_ID = 9999L;
  private static final long ENV_ID = 7L;
  private static final String REPO = "ls1intum/Helios";
  private static final String REVIEWER = "alice";
  private static final String CREATOR = "bob";

  @Test
  void approvesAndStampsRowAndCallsGitHubWithSourceTaggedComment() throws IOException {
    Fixture f = new Fixture();
    f.reviewersAre("alice", "bob");

    DeploymentApprovalRequest row =
        f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER));

    assertEquals(DeploymentApprovalRequest.State.APPROVED, row.getState());
    assertEquals(DeploymentApprovalRequest.Via.IN_APP, row.getVia());
    verify(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            eq(REPO),
            eq(WORKFLOW_RUN_ID),
            eq(ENV_ID),
            eq(REVIEWER),
            contains("via Helios (in-app)"));
  }

  @Test
  void declineCallsRejectAndAppendsUserComment() throws IOException {
    Fixture f = new Fixture();
    f.reviewersAre("alice", "bob");

    f.service().declineAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER), "wrong branch");

    ArgumentCaptor<String> commentCaptor = ArgumentCaptor.forClass(String.class);
    verify(f.gitHubService)
        .rejectDeploymentOnBehalfOfUser(
            eq(REPO), eq(WORKFLOW_RUN_ID), eq(ENV_ID), eq(REVIEWER), commentCaptor.capture());
    assertEquals(true, commentCaptor.getValue().contains("Declined by @alice"));
    assertEquals(true, commentCaptor.getValue().contains("wrong branch"));
  }

  @Test
  void rejectsCallWhenUserIsNotARequiredReviewer() throws IOException {
    Fixture f = new Fixture();
    f.reviewersAre("bob", "carol"); // alice not listed

    ResponseStatusException e =
        assertThrows(
            ResponseStatusException.class,
            () -> f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER)));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
  }

  @Test
  void rejectsApprovalWhenPreventSelfReviewBlocksCreator() throws IOException {
    Fixture f = new Fixture();
    // Alice is both creator and reviewer, and preventSelfReview is on.
    f.heliosDeployment.setCreator(f.userWithLogin(REVIEWER));
    when(f.environmentService.resolveReviewers(ENV_ID))
        .thenReturn(Optional.of(new ReviewerResolution(true, Set.of(REVIEWER, "bob"), Set.of())));

    ResponseStatusException e =
        assertThrows(
            ResponseStatusException.class,
            () -> f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER)));
    assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    verify(f.gitHubService, never())
        .approveDeploymentOnBehalfOfUser(any(), anyLong(), any(), any(), any());
  }

  @Test
  void conflictsWhenDeploymentAlreadyResolved() throws IOException {
    Fixture f = new Fixture();
    f.reviewersAre("alice", "bob");
    DeploymentApprovalRequest already = new DeploymentApprovalRequest();
    already.setState(DeploymentApprovalRequest.State.APPROVED);
    when(f.approvalRequestRepository.findByHeliosDeployment(f.heliosDeployment))
        .thenReturn(List.of(already));

    ResponseStatusException e =
        assertThrows(
            ResponseStatusException.class,
            () -> f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER)));
    assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
  }

  @Test
  void conflictsWhenDeploymentHasNoWorkflowRunId() {
    Fixture f = new Fixture();
    f.heliosDeployment.setWorkflowRunId(null);

    ResponseStatusException e =
        assertThrows(
            ResponseStatusException.class,
            () -> f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER)));
    assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
  }

  @Test
  void conflictsWhenEnvironmentHasNoReviewerRule() {
    Fixture f = new Fixture();
    when(f.environmentService.resolveReviewers(ENV_ID)).thenReturn(Optional.empty());

    ResponseStatusException e =
        assertThrows(
            ResponseStatusException.class,
            () -> f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER)));
    assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
  }

  @Test
  void marksFailedAtGitHubAndReturnsBadGatewayWhenGitHubCallThrows() throws IOException {
    Fixture f = new Fixture();
    f.reviewersAre("alice", "bob");
    doThrow(new IOException("HTTP 500"))
        .when(f.gitHubService)
        .approveDeploymentOnBehalfOfUser(
            anyString(), anyLong(), any(), anyString(), anyString());

    ResponseStatusException e =
        assertThrows(
            ResponseStatusException.class,
            () -> f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER)));
    assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());

    ArgumentCaptor<DeploymentApprovalRequest> rowCaptor =
        ArgumentCaptor.forClass(DeploymentApprovalRequest.class);
    verify(f.approvalRequestRepository).save(rowCaptor.capture());
    assertEquals(
        DeploymentApprovalRequest.State.FAILED_AT_GITHUB, rowCaptor.getValue().getState());
  }

  @Test
  void consumesSiblingPendingRowsAfterSuccessfulApproval() throws IOException {
    Fixture f = new Fixture();
    f.reviewersAre("alice", "bob");
    DeploymentApprovalRequest sibling = new DeploymentApprovalRequest();
    sibling.setId(99L);
    sibling.setReviewerLogin("bob");
    sibling.setState(DeploymentApprovalRequest.State.PENDING);
    List<DeploymentApprovalRequest> existing = new ArrayList<>();
    existing.add(sibling);
    when(f.approvalRequestRepository.findByHeliosDeployment(f.heliosDeployment))
        .thenReturn(existing);

    f.service().approveAsCurrentUser(DEPLOYMENT_ID, f.userWithLogin(REVIEWER));

    // The sibling row should now be CONSUMED_BY_OTHER.
    assertEquals(DeploymentApprovalRequest.State.CONSUMED_BY_OTHER, sibling.getState());
  }

  /** Wires the service's collaborators as bare mocks; each test stubs only what it needs. */
  private static final class Fixture {
    final HeliosDeploymentRepository heliosDeploymentRepository =
        mock(HeliosDeploymentRepository.class);
    final DeploymentApprovalRequestRepository approvalRequestRepository =
        mock(DeploymentApprovalRequestRepository.class);
    final EnvironmentService environmentService = mock(EnvironmentService.class);
    final GitHubService gitHubService = mock(GitHubService.class);

    final HeliosDeployment heliosDeployment = newHeliosDeployment();

    Fixture() {
      when(heliosDeploymentRepository.findByIdForUpdate(DEPLOYMENT_ID))
          .thenReturn(Optional.of(heliosDeployment));
      when(heliosDeploymentRepository.save(any(HeliosDeployment.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(approvalRequestRepository.findByHeliosDeployment(heliosDeployment))
          .thenReturn(List.of());
      when(approvalRequestRepository.lockByDeploymentAndReviewerLogin(eq(DEPLOYMENT_ID), any()))
          .thenReturn(Optional.empty());
      when(approvalRequestRepository.save(any(DeploymentApprovalRequest.class)))
          .thenAnswer(inv -> inv.getArgument(0));
    }

    DeploymentReviewActionService service() {
      return new DeploymentReviewActionService(
          heliosDeploymentRepository, approvalRequestRepository, environmentService, gitHubService);
    }

    void reviewersAre(String... logins) {
      when(environmentService.resolveReviewers(ENV_ID))
          .thenReturn(Optional.of(new ReviewerResolution(false, Set.of(logins), Set.of())));
    }

    User userWithLogin(String login) {
      User u = new User();
      u.setId(login.hashCode() & 0x7fffffffL);
      u.setLogin(login);
      return u;
    }

    private HeliosDeployment newHeliosDeployment() {
      HeliosDeployment d = new HeliosDeployment();
      d.setId(DEPLOYMENT_ID);
      d.setWorkflowRunId(WORKFLOW_RUN_ID);
      User creator = new User();
      creator.setLogin(CREATOR);
      d.setCreator(creator);
      GitRepository repo = mock(GitRepository.class);
      when(repo.getNameWithOwner()).thenReturn(REPO);
      when(repo.getRepositoryId()).thenReturn(880304517L);
      Environment env = mock(Environment.class);
      when(env.getId()).thenReturn(ENV_ID);
      when(env.getName()).thenReturn("production");
      when(env.getRepository()).thenReturn(repo);
      d.setEnvironment(env);
      return d;
    }
  }
}
