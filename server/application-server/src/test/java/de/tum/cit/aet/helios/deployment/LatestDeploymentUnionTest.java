package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class LatestDeploymentUnionTest {

  private static final OffsetDateTime DEPLOY_JOB_STARTED_AT =
      OffsetDateTime.parse("2026-04-23T10:00:00Z");
  private static final OffsetDateTime WORKFLOW_STARTED_AT =
      OffsetDateTime.parse("2026-04-23T09:58:00Z");

  @Test
  void fromHeliosStatusPreservesQueuedState() {
    assertEquals(
        LatestDeploymentUnion.State.REQUESTED,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.WAITING));
    assertEquals(
        LatestDeploymentUnion.State.QUEUED,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.QUEUED));
    assertEquals(
        LatestDeploymentUnion.State.IN_PROGRESS,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.IN_PROGRESS));
  }

  @Test
  void fromHeliosStatusKeepsTerminalStatesUnchanged() {
    assertEquals(
        LatestDeploymentUnion.State.SUCCESS,
        LatestDeploymentUnion.State.fromHeliosStatus(
            HeliosDeployment.Status.DEPLOYMENT_SUCCESS));
    assertEquals(
        LatestDeploymentUnion.State.FAILURE,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.FAILED));
    assertEquals(
        LatestDeploymentUnion.State.CANCELLED,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.CANCELLED));
    assertEquals(
        LatestDeploymentUnion.State.UNKNOWN,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.UNKNOWN));
  }

  @Test
  void exposesPhaseTimestampsForHeliosDeployment() {
    LatestDeploymentUnion union = LatestDeploymentUnion.heliosDeployment(heliosDeployment(null));

    assertEquals(DEPLOY_JOB_STARTED_AT, union.getDeployJobStartedAt());
    assertEquals(WORKFLOW_STARTED_AT, union.getWorkflowStartedAt());
  }

  @Test
  void exposesPhaseTimestampsForMatchingRealDeploymentFallback() {
    LatestDeploymentUnion union =
        LatestDeploymentUnion.realDeployment(deployment(1L), heliosDeployment(1L));

    assertEquals(DEPLOY_JOB_STARTED_AT, union.getDeployJobStartedAt());
    assertEquals(WORKFLOW_STARTED_AT, union.getWorkflowStartedAt());
  }

  @Test
  void hidesPhaseTimestampsForUnmatchedRealDeploymentFallback() {
    LatestDeploymentUnion union =
        LatestDeploymentUnion.realDeployment(deployment(1L), heliosDeployment(2L));

    assertNull(union.getDeployJobStartedAt());
    assertNull(union.getWorkflowStartedAt());
  }

  @Test
  void exposesPhaseTimestampsForRealDeploymentFallbackWithoutDeploymentId() {
    LatestDeploymentUnion union =
        LatestDeploymentUnion.realDeployment(deployment(1L), heliosDeployment(null));

    assertEquals(DEPLOY_JOB_STARTED_AT, union.getDeployJobStartedAt());
    assertEquals(WORKFLOW_STARTED_AT, union.getWorkflowStartedAt());
  }

  @Test
  void realDeploymentFallbackWithoutDeploymentIdDoesNotOverrideSourceFields() {
    Deployment deployment = deployment(1L);
    deployment.setRef("staging");
    deployment.setSha("workflow-sha");

    HeliosDeployment heliosDeployment = heliosDeployment(null);
    heliosDeployment.setSourceBranchName("feature/source");
    heliosDeployment.setSha("source-sha");

    LatestDeploymentUnion union = LatestDeploymentUnion.realDeployment(deployment,
        heliosDeployment);

    assertEquals("staging", union.getRef());
    assertEquals("workflow-sha", union.getSha());
  }

  @Test
  void heliosDeploymentExposesSourceBranchAndSha() {
    HeliosDeployment heliosDeployment = heliosDeployment(null);
    heliosDeployment.setBranchName("staging");
    heliosDeployment.setSourceBranchName("feature/source");
    heliosDeployment.setSha("source-sha");

    LatestDeploymentUnion union = LatestDeploymentUnion.heliosDeployment(heliosDeployment);

    assertEquals("feature/source", union.getRef());
    assertEquals("source-sha", union.getSha());
  }

  @Test
  void realDeploymentWithMatchingHeliosFallbackExposesSourceBranchPrAndSha() {
    Deployment deployment = deployment(1L);
    deployment.setRef("staging");
    deployment.setSha("workflow-sha");

    HeliosDeployment heliosDeployment = heliosDeployment(1L);
    heliosDeployment.setBranchName("staging");
    heliosDeployment.setSourceBranchName("feature/source");
    heliosDeployment.setSha("source-sha");
    heliosDeployment.setPullRequest(pullRequest(42, "Source PR"));

    LatestDeploymentUnion union = LatestDeploymentUnion.realDeployment(deployment,
        heliosDeployment);

    assertEquals("feature/source", union.getRef());
    assertEquals("source-sha", union.getSha());
    assertEquals("Source PR", union.getPullRequestName());
    assertEquals(42, union.getPullRequestNumber());
  }

  @Test
  void realDeploymentWithoutHeliosFallbackExposesGitHubRefAndSha() {
    Deployment deployment = deployment(1L);
    deployment.setRef("staging");
    deployment.setSha("workflow-sha");

    LatestDeploymentUnion union = LatestDeploymentUnion.realDeployment(deployment);

    assertEquals("staging", union.getRef());
    assertEquals("workflow-sha", union.getSha());
  }

  private Deployment deployment(Long id) {
    Deployment deployment = new Deployment();
    deployment.setId(id);
    return deployment;
  }

  private PullRequest pullRequest(int number, String title) {
    PullRequest pullRequest = new PullRequest();
    pullRequest.setNumber(number);
    pullRequest.setTitle(title);
    return pullRequest;
  }

  private HeliosDeployment heliosDeployment(Long deploymentId) {
    HeliosDeployment heliosDeployment = new HeliosDeployment();
    heliosDeployment.setDeploymentId(deploymentId);
    heliosDeployment.setCreatedAt(OffsetDateTime.parse("2026-04-23T09:50:00Z"));
    heliosDeployment.setDeployJobStartedAt(DEPLOY_JOB_STARTED_AT);
    heliosDeployment.setWorkflowStartedAt(WORKFLOW_STARTED_AT);
    return heliosDeployment;
  }
}
