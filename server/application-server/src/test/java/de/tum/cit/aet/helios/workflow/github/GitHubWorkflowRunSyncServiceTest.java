package de.tum.cit.aet.helios.workflow.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHWorkflowRun;

class GitHubWorkflowRunSyncServiceTest {

  @Test
  void mapLiveWorkflowRunStatusPreservesPreviousNonCompletedStatusMapping() {
    GHWorkflowRun.Status[] unknownStatuses = {
      GHWorkflowRun.Status.ACTION_REQUIRED,
      GHWorkflowRun.Status.CANCELLED,
      GHWorkflowRun.Status.FAILURE,
      GHWorkflowRun.Status.NEUTRAL,
      GHWorkflowRun.Status.REQUESTED,
      GHWorkflowRun.Status.SKIPPED,
      GHWorkflowRun.Status.STALE,
      GHWorkflowRun.Status.SUCCESS,
      GHWorkflowRun.Status.TIMED_OUT,
      GHWorkflowRun.Status.UNKNOWN,
      GHWorkflowRun.Status.WAITING
    };

    for (GHWorkflowRun.Status status : unknownStatuses) {
      assertEquals(
          HeliosDeployment.Status.UNKNOWN,
          GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(status, null));
    }
  }

  @Test
  void mapLiveWorkflowRunStatusPreservesPreviousActiveStatusMapping() {
    assertEquals(
        HeliosDeployment.Status.WAITING,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(GHWorkflowRun.Status.PENDING, null));
    assertEquals(
        HeliosDeployment.Status.QUEUED,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(GHWorkflowRun.Status.QUEUED, null));
    assertEquals(
        HeliosDeployment.Status.IN_PROGRESS,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(
            GHWorkflowRun.Status.IN_PROGRESS, null));
  }

  @Test
  void mapLiveWorkflowRunStatusPreservesPreviousCompletedConclusionMapping() {
    assertEquals(
        HeliosDeployment.Status.DEPLOYMENT_SUCCESS,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(
            GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.SUCCESS));
    assertEquals(
        HeliosDeployment.Status.CANCELLED,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(
            GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.CANCELLED));
    assertEquals(
        HeliosDeployment.Status.FAILED,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(
            GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.FAILURE));
    assertEquals(
        HeliosDeployment.Status.FAILED,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(
            GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.STARTUP_FAILURE));
    assertEquals(
        HeliosDeployment.Status.FAILED,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(
            GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.TIMED_OUT));
    assertEquals(
        HeliosDeployment.Status.UNKNOWN,
        GitHubWorkflowRunSyncService.mapLiveWorkflowRunStatus(
            GHWorkflowRun.Status.COMPLETED, GHWorkflowRun.Conclusion.ACTION_REQUIRED));
  }
}
