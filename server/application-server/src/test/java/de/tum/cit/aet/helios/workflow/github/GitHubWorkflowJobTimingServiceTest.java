package de.tum.cit.aet.helios.workflow.github;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfig;
import de.tum.cit.aet.helios.deployment.DeploymentWorkflowConfigRepository;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.class)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubWorkflowJobTimingServiceTest {

  @Mock private DeploymentWorkflowConfigRepository deploymentWorkflowConfigRepository;
  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock private WorkflowRunRepository workflowRunRepository;

  @InjectMocks private GitHubWorkflowJobTimingService gitHubWorkflowJobTimingService;

  private Environment environment;
  private Workflow deploymentWorkflow;
  private DeploymentWorkflowConfig config;
  private HeliosDeployment heliosDeployment;

  @BeforeEach
  void setUp() {
    deploymentWorkflow = new Workflow();
    deploymentWorkflow.setId(10L);

    environment = new Environment();
    environment.setId(1L);
    environment.setName("test");
    environment.setDeploymentWorkflow(deploymentWorkflow);

    config = new DeploymentWorkflowConfig();
    config.setDeployJobName("deploy");

    heliosDeployment = new HeliosDeployment();
    heliosDeployment.setId(5L);
    heliosDeployment.setEnvironment(environment);
    heliosDeployment.setWorkflowRunId(23716064328L);
    heliosDeployment.setCreatedAt(OffsetDateTime.parse("2026-03-29T18:31:49Z"));
  }

  @Test
  void persistDurationsUsesWorkflowRunStartWhenAvailable() {
    GitHubWorkflowJobPayload payload = payload("deploy");
    WorkflowRun workflowRun = new WorkflowRun();
    workflowRun.setId(23716064328L);
    workflowRun.setWorkflow(deploymentWorkflow);
    workflowRun.setRunStartedAt(OffsetDateTime.parse("2026-03-29T18:31:40Z"));

    when(deploymentWorkflowConfigRepository.findByWorkflow(any(Workflow.class)))
        .thenReturn(Optional.of(config));
    when(heliosDeploymentRepository.findByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(heliosDeployment));
    when(workflowRunRepository.findById(any(Long.class))).thenReturn(Optional.of(workflowRun));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(Integer.valueOf(13), heliosDeployment.getBuildDurationSeconds());
    assertEquals(Integer.valueOf(13), heliosDeployment.getDeployDurationSeconds());
    assertEquals(Long.valueOf(4210842007L), heliosDeployment.getDeploymentId());
    verify(heliosDeploymentRepository).save(heliosDeployment);
  }

  @Test
  void persistDurationsFallsBackToDeploymentCreatedAtAndBranchMatch() {
    GitHubWorkflowJobPayload payload = payload("deploy");
    WorkflowRun workflowRun = new WorkflowRun();
    workflowRun.setId(23716064328L);
    workflowRun.setWorkflow(deploymentWorkflow);

    when(deploymentWorkflowConfigRepository.findByWorkflow(any(Workflow.class)))
        .thenReturn(Optional.of(config));
    when(heliosDeploymentRepository.findByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(heliosDeployment));
    when(workflowRunRepository.findById(any(Long.class))).thenReturn(Optional.of(workflowRun));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    assertEquals(Integer.valueOf(3), heliosDeployment.getBuildDurationSeconds());
    assertEquals(Integer.valueOf(13), heliosDeployment.getDeployDurationSeconds());
    verify(heliosDeploymentRepository).save(heliosDeployment);
  }

  @Test
  void persistDurationsSkipsWorkflowRunsOutsideConfiguredWorkflow() {
    GitHubWorkflowJobPayload payload = payload("deploy");
    Workflow otherWorkflow = new Workflow();
    otherWorkflow.setId(11L);
    WorkflowRun workflowRun = new WorkflowRun();
    workflowRun.setId(23716064328L);
    workflowRun.setWorkflow(otherWorkflow);

    when(heliosDeploymentRepository.findByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(heliosDeployment));
    when(workflowRunRepository.findById(any(Long.class))).thenReturn(Optional.of(workflowRun));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository, never()).save(any());
  }

  @Test
  void persistDurationsSkipsNonConfiguredJobs() {
    GitHubWorkflowJobPayload payload = payload("build");

    when(deploymentWorkflowConfigRepository.findByWorkflow(any(Workflow.class)))
        .thenReturn(Optional.of(config));
    when(heliosDeploymentRepository.findByWorkflowRunId(anyLong()))
        .thenReturn(Optional.of(heliosDeployment));

    gitHubWorkflowJobTimingService.persistDurations(payload);

    verify(heliosDeploymentRepository, never()).save(any());
  }

  private GitHubWorkflowJobPayload payload(String jobName) {
    return new GitHubWorkflowJobPayload(
        "completed",
        new GitHubWorkflowJobPayload.WorkflowJob(
            69083291077L,
            23716064328L,
            "New Deploy Test",
            "new-branch",
            "b49e899f86b523027348c31adce8d7b41ac0cb00",
            "https://github.com/mert-test-org/helios-test-repo/actions/runs/23716064328/job/69083291077",
            "completed",
            "success",
            OffsetDateTime.parse("2026-03-29T18:31:49Z"),
            OffsetDateTime.parse("2026-03-29T18:31:53Z"),
            OffsetDateTime.parse("2026-03-29T18:32:06Z"),
            jobName),
        new GitHubWorkflowJobPayload.Deployment(
            4210842007L,
            "test",
            "new-branch",
            "b49e899f86b523027348c31adce8d7b41ac0cb00",
            OffsetDateTime.parse("2026-03-29T18:31:50Z")),
        new GitHubWorkflowJobPayload.Repository(
            1097747382L,
            "mert-test-org/helios-test-repo"));
  }
}
