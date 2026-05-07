package de.tum.cit.aet.helios.workflow.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentWorkflowJobTimingMeta;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubWorkflowJobTimingServiceTest {

  private static final Long DEPLOYMENT_ID = 5L;
  private static final Long WORKFLOW_RUN_ID = 23716064328L;
  private static final Long WORKFLOW_ID = 10L;
  private static final OffsetDateTime WORKFLOW_RUN_STARTED_AT =
      OffsetDateTime.parse("2026-03-29T18:31:40Z");
  private static final OffsetDateTime DEPLOYMENT_CREATED_AT =
      OffsetDateTime.parse("2026-03-29T18:31:49Z");

  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;

  @InjectMocks private GitHubWorkflowJobTimingService gitHubWorkflowJobTimingService;

  private HeliosDeployment heliosDeployment;

  @BeforeEach
  void setUp() {
    heliosDeployment = new HeliosDeployment();
    heliosDeployment.setId(DEPLOYMENT_ID);
    heliosDeployment.setWorkflowRunId(WORKFLOW_RUN_ID);
    heliosDeployment.setCreatedAt(DEPLOYMENT_CREATED_AT);
  }

  @Test
  void persistDurationsSkipsQueuedWithoutDeploymentBeforeDatabaseLookup() {
    GitHubWorkflowJobPayload payload = payloadWithoutDeployment("queued", "queued", "build");

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verifyNoInteractions(heliosDeploymentRepository);
  }

  @Test
  void persistDurationsCachesRunsWithoutDeployment() {
    final GitHubWorkflowJobPayload payload =
        payloadWithoutDeployment("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.empty());

    gitHubWorkflowJobTimingService.persistDurations(payload);
    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository).findWorkflowJobTimingMetaByWorkflowRunId(WORKFLOW_RUN_ID);
    verify(heliosDeploymentRepository, never()).findById(anyLong());
    verify(heliosDeploymentRepository, never()).save(any());
  }

  @Test
  void persistDurationsTrustsCachedNotDeploymentEvenWhenDeploymentPayloadArrives() {
    final GitHubWorkflowJobPayload initialPayload =
        payloadWithoutDeployment("completed", "completed", "deploy");
    final GitHubWorkflowJobPayload deploymentPayload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.empty());

    gitHubWorkflowJobTimingService.persistDurations(initialPayload);
    gitHubWorkflowJobTimingService.persistDurations(deploymentPayload);

    verify(heliosDeploymentRepository).findWorkflowJobTimingMetaByWorkflowRunId(WORKFLOW_RUN_ID);
    verify(heliosDeploymentRepository, never()).findById(anyLong());
    verify(heliosDeploymentRepository, never()).save(any());
  }

  @Test
  void persistDurationsRecordsFirstNonDeployInProgressThenShortCircuitsLaterNonDeployJobs() {
    final GitHubWorkflowJobPayload buildJobInProgress =
        payload("in_progress", "in_progress", "build");
    final GitHubWorkflowJobPayload buildJobCompleted =
        payload("completed", "completed", "build");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT)));
    when(heliosDeploymentRepository.findById(DEPLOYMENT_ID))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(buildJobInProgress);
    gitHubWorkflowJobTimingService.persistDurations(buildJobCompleted);
    gitHubWorkflowJobTimingService.persistDurations(buildJobInProgress);

    assertEquals(WORKFLOW_RUN_STARTED_AT, heliosDeployment.getWorkflowStartedAt());
    assertEquals(HeliosDeployment.Status.IN_PROGRESS, heliosDeployment.getStatus());
    verify(heliosDeploymentRepository, times(1))
        .findWorkflowJobTimingMetaByWorkflowRunId(WORKFLOW_RUN_ID);
    verify(heliosDeploymentRepository, times(1)).findById(DEPLOYMENT_ID);
    verify(heliosDeploymentRepository, times(1)).save(heliosDeployment);
  }

  @Test
  void persistDurationsDeployInProgressShortCircuitsAfterStartRecorded() {
    final GitHubWorkflowJobPayload payload = payload("in_progress", "in_progress", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT)));
    when(heliosDeploymentRepository.findById(DEPLOYMENT_ID))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(payload);
    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(
        OffsetDateTime.parse("2026-03-29T18:31:53Z"),
        heliosDeployment.getDeployJobStartedAt());
    assertEquals(WORKFLOW_RUN_STARTED_AT, heliosDeployment.getWorkflowStartedAt());
    assertEquals(HeliosDeployment.Status.IN_PROGRESS, heliosDeployment.getStatus());
    verify(heliosDeploymentRepository, times(1)).findById(DEPLOYMENT_ID);
    verify(heliosDeploymentRepository, times(1)).save(heliosDeployment);
  }

  @Test
  void persistDurationsCompletedDeployJobShortCircuitsWhenDurationsAlreadyRecorded() {
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(completedMeta()));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository, never()).findById(anyLong());
    verify(heliosDeploymentRepository, never()).save(any());
  }

  @Test
  void persistDurationsCompletedDeployJobLoadsAndSavesWhenDurationsAreMissing() {
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT)));
    when(heliosDeploymentRepository.findById(DEPLOYMENT_ID))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(Integer.valueOf(13), heliosDeployment.getPreDeployDurationSeconds());
    assertEquals(Integer.valueOf(13), heliosDeployment.getDeployDurationSeconds());
    assertEquals(Long.valueOf(4210842007L), heliosDeployment.getDeploymentId());
    verify(heliosDeploymentRepository).save(heliosDeployment);
  }

  @Test
  void persistDurationsUsesWorkflowRunStartWhenAvailable() {
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT)));
    when(heliosDeploymentRepository.findById(DEPLOYMENT_ID))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(Integer.valueOf(13), heliosDeployment.getPreDeployDurationSeconds());
    assertEquals(Integer.valueOf(13), heliosDeployment.getDeployDurationSeconds());
    verify(heliosDeploymentRepository).save(heliosDeployment);
  }

  @Test
  void persistDurationsFallsBackToJobStartWhenWorkflowRunMissing() {
    final GitHubWorkflowJobPayload payload = payload("in_progress", "in_progress", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(null)));
    when(heliosDeploymentRepository.findById(DEPLOYMENT_ID))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(
        OffsetDateTime.parse("2026-03-29T18:31:53Z"),
        heliosDeployment.getDeployJobStartedAt());
    assertEquals(
        OffsetDateTime.parse("2026-03-29T18:31:53Z"),
        heliosDeployment.getWorkflowStartedAt());
    assertEquals(HeliosDeployment.Status.IN_PROGRESS, heliosDeployment.getStatus());
    verify(heliosDeploymentRepository).save(heliosDeployment);
  }

  @Test
  void persistDurationsSplitsUsingPersistedDeployJobStartWhenAvailable() {
    heliosDeployment.setDeployJobStartedAt(OffsetDateTime.parse("2026-03-29T18:31:53Z"));
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT)));
    when(heliosDeploymentRepository.findById(DEPLOYMENT_ID))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(Integer.valueOf(13), heliosDeployment.getPreDeployDurationSeconds());
    assertEquals(Integer.valueOf(13), heliosDeployment.getDeployDurationSeconds());
    verify(heliosDeploymentRepository).save(heliosDeployment);
  }

  @Test
  void persistDurationsFallsBackToDeploymentCreatedAtAndBranchMatch() {
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(null)));
    when(heliosDeploymentRepository.findById(DEPLOYMENT_ID))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(Integer.valueOf(3), heliosDeployment.getPreDeployDurationSeconds());
    assertEquals(Integer.valueOf(13), heliosDeployment.getDeployDurationSeconds());
    verify(heliosDeploymentRepository).save(heliosDeployment);
  }

  @Test
  void persistDurationsSkipsWorkflowRunsOutsideConfiguredWorkflow() {
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT, WORKFLOW_ID, 11L, "deploy")));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository, never()).findById(anyLong());
    verify(heliosDeploymentRepository, never()).save(any());
  }

  @Test
  void persistDurationsSkipsWhenDeploymentWorkflowConfigIsMissing() {
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT, WORKFLOW_ID, WORKFLOW_ID, null)));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository, never()).findById(anyLong());
    verify(heliosDeploymentRepository, never()).save(any());
  }

  @Test
  void persistDurationsSkipsWhenEnvironmentHasNoDeploymentWorkflow() {
    final GitHubWorkflowJobPayload payload = payload("completed", "completed", "deploy");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT, null, WORKFLOW_ID, "deploy")));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository, never()).findById(anyLong());
    verify(heliosDeploymentRepository, never()).save(any());
  }

  @Test
  void persistDurationsSkipsNonConfiguredCompletedJobsWithoutEntityLoad() {
    GitHubWorkflowJobPayload payload = payload("completed", "completed", "build");

    when(heliosDeploymentRepository.findWorkflowJobTimingMetaByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(meta(WORKFLOW_RUN_STARTED_AT)));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository, never()).findById(anyLong());
    verify(heliosDeploymentRepository, never()).save(any());
  }

  private HeliosDeploymentWorkflowJobTimingMeta meta(OffsetDateTime runStartedAt) {
    return meta(runStartedAt, WORKFLOW_ID, WORKFLOW_ID, "deploy");
  }

  private HeliosDeploymentWorkflowJobTimingMeta meta(
      OffsetDateTime runStartedAt,
      Long configuredWorkflowId,
      Long workflowRunWorkflowId,
      String deployJobName) {
    return new HeliosDeploymentWorkflowJobTimingMeta(
        DEPLOYMENT_ID,
        heliosDeployment.getWorkflowStartedAt(),
        heliosDeployment.getStatus(),
        heliosDeployment.getDeployJobStartedAt(),
        heliosDeployment.getPreDeployDurationSeconds(),
        heliosDeployment.getDeployDurationSeconds(),
        heliosDeployment.getDeploymentId(),
        heliosDeployment.getCreatedAt(),
        configuredWorkflowId,
        deployJobName,
        runStartedAt,
        workflowRunWorkflowId);
  }

  private HeliosDeploymentWorkflowJobTimingMeta completedMeta() {
    return new HeliosDeploymentWorkflowJobTimingMeta(
        DEPLOYMENT_ID,
        WORKFLOW_RUN_STARTED_AT,
        HeliosDeployment.Status.IN_PROGRESS,
        OffsetDateTime.parse("2026-03-29T18:31:53Z"),
        13,
        13,
        4210842007L,
        DEPLOYMENT_CREATED_AT,
        WORKFLOW_ID,
        "deploy",
        WORKFLOW_RUN_STARTED_AT,
        WORKFLOW_ID);
  }

  private GitHubWorkflowJobPayload payload(
      String action, String workflowJobStatus, String jobName) {
    return payload(action, workflowJobStatus, jobName, deployment());
  }

  private GitHubWorkflowJobPayload payload(
      String action,
      String workflowJobStatus,
      String jobName,
      GitHubWorkflowJobPayload.Deployment deployment) {
    return new GitHubWorkflowJobPayload(
        action,
        new GitHubWorkflowJobPayload.WorkflowJob(
            69083291077L,
            WORKFLOW_RUN_ID,
            "New Deploy Test",
            "new-branch",
            "b49e899f86b523027348c31adce8d7b41ac0cb00",
            "https://github.com/mert-test-org/helios-test-repo/actions/runs/"
                + "23716064328/job/69083291077",
            workflowJobStatus,
            "success",
            OffsetDateTime.parse("2026-03-29T18:31:49Z"),
            OffsetDateTime.parse("2026-03-29T18:31:53Z"),
            OffsetDateTime.parse("2026-03-29T18:32:06Z"),
            jobName),
        deployment,
        new GitHubWorkflowJobPayload.Repository(
            1097747382L,
            "mert-test-org/helios-test-repo"));
  }

  private GitHubWorkflowJobPayload payloadWithoutDeployment(
      String action, String workflowJobStatus, String jobName) {
    return payload(action, workflowJobStatus, jobName, null);
  }

  private GitHubWorkflowJobPayload.Deployment deployment() {
    return new GitHubWorkflowJobPayload.Deployment(
        4210842007L,
        "test",
        "new-branch",
        "b49e899f86b523027348c31adce8d7b41ac0cb00",
        OffsetDateTime.parse("2026-03-29T18:31:50Z"));
  }
}
