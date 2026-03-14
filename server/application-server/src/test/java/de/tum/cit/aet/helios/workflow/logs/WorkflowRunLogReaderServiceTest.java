package de.tum.cit.aet.helios.workflow.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.deployment.DeploymentService;
import de.tum.cit.aet.helios.deployment.WorkflowJobDto;
import de.tum.cit.aet.helios.deployment.WorkflowJobsResponse;
import de.tum.cit.aet.helios.deployment.WorkflowStepDto;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.logs.storage.WorkflowRunLogCacheResult;
import de.tum.cit.aet.helios.workflow.logs.storage.WorkflowRunLogManifest;
import de.tum.cit.aet.helios.workflow.logs.storage.WorkflowRunLogStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowRunLogReaderServiceTest {

  @Mock private WorkflowRunLogStorageService workflowRunLogStorageService;
  @Mock private DeploymentService deploymentService;
  private final WorkflowRunLogFileResolver workflowRunLogFileResolver =
      new WorkflowRunLogFileResolver();

  @TempDir Path tempDir;

  @Test
  void getLogsGroupsFilesByTopLevelDirectory() throws Exception {
    Path runDirectory = tempDir.resolve("repositories/99/workflow-runs/7");
    Files.createDirectories(runDirectory.resolve("build"));
    Files.writeString(runDirectory.resolve("build/1_build.txt"), "build log");
    Files.writeString(runDirectory.resolve("summary.txt"), "summary log");
    WorkflowRunLogManifest manifest =
        new WorkflowRunLogManifest(7L, 99L, OffsetDateTime.parse("2026-03-12T10:15:30Z"), 2);
    WorkflowRun workflowRun = createWorkflowRun(7L, "deploy", "Deploy preview");

    when(workflowRunLogStorageService.ensureLogsCached(7L))
        .thenReturn(new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, false));
    when(deploymentService.getWorkflowJobStatus(7L)).thenReturn(new WorkflowJobsResponse());

    WorkflowRunLogReaderService service =
        new WorkflowRunLogReaderService(
            workflowRunLogStorageService, deploymentService, workflowRunLogFileResolver);

    WorkflowRunLogsResponse response = service.getLogs(7L);

    assertEquals(7L, response.workflowRunId());
    assertEquals("deploy", response.workflowName());
    assertEquals("Deploy preview", response.displayTitle());
    assertEquals(WorkflowRun.Conclusion.SUCCESS, response.conclusion());
    assertFalse(response.cacheHit());
    assertEquals(2, response.totalFileCount());
    assertEquals(2, response.groups().size());

    WorkflowRunLogGroupDto buildGroup = response.groups().get(0);
    assertEquals("build", buildGroup.name());
    assertEquals(
        List.of("build/1_build.txt"),
        buildGroup.files().stream().map(WorkflowRunLogFileDto::path).toList());
    assertEquals(
        List.of("build"),
        buildGroup.files().stream().map(WorkflowRunLogFileDto::displayName).toList());
    assertEquals(
        List.of("build log"),
        buildGroup.files().stream().map(WorkflowRunLogFileDto::content).toList());

    WorkflowRunLogGroupDto rootGroup = response.groups().get(1);
    assertEquals("Workflow run", rootGroup.name());
    assertEquals(
        List.of("summary.txt"),
        rootGroup.files().stream().map(WorkflowRunLogFileDto::path).toList());

    verify(workflowRunLogStorageService).ensureLogsCached(7L);
  }

  @Test
  void getLogsAssignsRootJobFilesToMatchingJobGroups() throws Exception {
    Path runDirectory = tempDir.resolve("repositories/99/workflow-runs/7");
    Files.createDirectories(runDirectory.resolve("deploy"));
    Files.createDirectories(runDirectory.resolve("test"));
    Files.writeString(runDirectory.resolve("0_deploy.txt"), "deploy step log");
    Files.writeString(runDirectory.resolve("1_test.txt"), "test step log");
    Files.writeString(runDirectory.resolve("deploy/system.txt"), "deploy system log");
    Files.writeString(runDirectory.resolve("test/system.txt"), "test system log");
    WorkflowRunLogManifest manifest =
        new WorkflowRunLogManifest(7L, 99L, OffsetDateTime.parse("2026-03-12T10:15:30Z"), 4);
    WorkflowRun workflowRun = createWorkflowRun(7L, "deploy", "Deploy preview");

    when(workflowRunLogStorageService.ensureLogsCached(7L))
        .thenReturn(new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, false));
    when(deploymentService.getWorkflowJobStatus(7L)).thenReturn(new WorkflowJobsResponse());

    WorkflowRunLogReaderService service =
        new WorkflowRunLogReaderService(
            workflowRunLogStorageService, deploymentService, workflowRunLogFileResolver);

    WorkflowRunLogsResponse response = service.getLogs(7L);

    assertEquals(2, response.groups().size());

    WorkflowRunLogGroupDto deployGroup = response.groups().get(0);
    assertEquals("deploy", deployGroup.name());
    assertEquals(
        List.of("deploy/system.txt", "0_deploy.txt"),
        deployGroup.files().stream().map(WorkflowRunLogFileDto::path).toList());
    assertEquals("Full job log", deployGroup.files().get(1).displayName());
    assertEquals(null, deployGroup.files().get(1).stepNumber());

    WorkflowRunLogGroupDto testGroup = response.groups().get(1);
    assertEquals("test", testGroup.name());
    assertEquals(
        List.of("test/system.txt", "1_test.txt"),
        testGroup.files().stream().map(WorkflowRunLogFileDto::path).toList());
    assertEquals("Full job log", testGroup.files().get(1).displayName());
    assertEquals(null, testGroup.files().get(1).stepNumber());
  }

  @Test
  void getLogsNormalizesRawGitHubLogFormatting() throws Exception {
    Path runDirectory = tempDir.resolve("repositories/99/workflow-runs/7");
    Files.createDirectories(runDirectory.resolve("deploy"));
    Files.writeString(
        runDirectory.resolve("deploy/system.txt"),
        """
        2026-03-12T20:46:28.9730189Z ##[group]Run echo "Running post-deployment checks..."
        2026-03-12T20:46:28.9730649Z \u001B[36;1mecho "Running post-deployment checks..."\u001B[0m
        2026-03-12T20:46:28.9731014Z \u001B[36;1msleep 1\u001B[0m
        2026-03-12T20:46:28.9782708Z ##[endgroup]
        2026-03-12T20:46:28.9850146Z Running post-deployment checks...
        """);
    WorkflowRunLogManifest manifest =
        new WorkflowRunLogManifest(7L, 99L, OffsetDateTime.parse("2026-03-12T10:15:30Z"), 1);
    WorkflowRun workflowRun = createWorkflowRun(7L, "deploy", "Deploy preview");

    when(workflowRunLogStorageService.ensureLogsCached(7L))
        .thenReturn(new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, false));
    when(deploymentService.getWorkflowJobStatus(7L)).thenReturn(new WorkflowJobsResponse());

    WorkflowRunLogReaderService service =
        new WorkflowRunLogReaderService(
            workflowRunLogStorageService, deploymentService, workflowRunLogFileResolver);

    WorkflowRunLogsResponse response = service.getLogs(7L);

    assertEquals(1, response.groups().size());
    assertEquals(
        """
        [group]Run echo "Running post-deployment checks..."
        echo "Running post-deployment checks..."
        sleep 1
        [endgroup]
        Running post-deployment checks...""",
        response.groups().getFirst().files().getFirst().content());
  }

  @Test
  void getLogsOrdersStepFilesBeforeAuxiliaryFilesAndFullJobLogLast() throws Exception {
    Path runDirectory = tempDir.resolve("repositories/99/workflow-runs/7");
    Files.createDirectories(runDirectory.resolve("deploy"));
    Files.writeString(runDirectory.resolve("deploy/10_cleanup.txt"), "cleanup log");
    Files.writeString(runDirectory.resolve("deploy/2_checks.txt"), "checks log");
    Files.writeString(runDirectory.resolve("deploy/system.txt"), "system log");
    Files.writeString(runDirectory.resolve("deploy/deploy.txt"), "full deploy log");
    WorkflowRunLogManifest manifest =
        new WorkflowRunLogManifest(7L, 99L, OffsetDateTime.parse("2026-03-12T10:15:30Z"), 4);
    WorkflowRun workflowRun = createWorkflowRun(7L, "deploy", "Deploy preview");

    when(workflowRunLogStorageService.ensureLogsCached(7L))
        .thenReturn(new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, false));
    when(deploymentService.getWorkflowJobStatus(7L)).thenReturn(new WorkflowJobsResponse());

    WorkflowRunLogsResponse response =
        new WorkflowRunLogReaderService(
                workflowRunLogStorageService, deploymentService, workflowRunLogFileResolver)
            .getLogs(7L);

    assertEquals(
        List.of(
            "deploy/2_checks.txt",
            "deploy/10_cleanup.txt",
            "deploy/system.txt",
            "deploy/deploy.txt"),
        response.groups().getFirst().files().stream().map(WorkflowRunLogFileDto::path).toList());
    assertEquals(
        List.of("checks", "cleanup", "system", "Full job log"),
        response.groups().getFirst().files().stream()
            .map(WorkflowRunLogFileDto::displayName)
            .toList());
  }

  @Test
  void getLogsOrdersGroupsByMatchedGitHubJobStartTime() throws Exception {
    Path runDirectory = tempDir.resolve("repositories/99/workflow-runs/7");
    Files.createDirectories(runDirectory.resolve("build"));
    Files.createDirectories(runDirectory.resolve("deploy"));
    Files.createDirectories(runDirectory.resolve("validate"));
    Files.writeString(runDirectory.resolve("build/system.txt"), "build log");
    Files.writeString(runDirectory.resolve("deploy/system.txt"), "deploy log");
    Files.writeString(runDirectory.resolve("validate/system.txt"), "validate log");
    final WorkflowRunLogManifest manifest =
        new WorkflowRunLogManifest(7L, 99L, OffsetDateTime.parse("2026-03-12T10:15:30Z"), 3);
    final WorkflowRun workflowRun = createWorkflowRun(7L, "deploy", "Deploy preview");

    final WorkflowJobDto buildJob = new WorkflowJobDto();
    buildJob.setId(1L);
    buildJob.setName("build");
    buildJob.setStartedAt(OffsetDateTime.parse("2026-03-12T10:02:00Z"));

    final WorkflowJobDto deployJob = new WorkflowJobDto();
    deployJob.setId(2L);
    deployJob.setName("deploy");
    deployJob.setStartedAt(OffsetDateTime.parse("2026-03-12T10:03:00Z"));

    final WorkflowJobDto validateJob = new WorkflowJobDto();
    validateJob.setId(3L);
    validateJob.setName("validate");
    validateJob.setStartedAt(OffsetDateTime.parse("2026-03-12T10:01:00Z"));

    final WorkflowJobsResponse workflowJobsResponse = new WorkflowJobsResponse();
    workflowJobsResponse.setJobs(List.of(buildJob, deployJob, validateJob));

    when(workflowRunLogStorageService.ensureLogsCached(7L))
        .thenReturn(new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, false));
    when(deploymentService.getWorkflowJobStatus(7L)).thenReturn(workflowJobsResponse);

    WorkflowRunLogsResponse response =
        new WorkflowRunLogReaderService(
                workflowRunLogStorageService, deploymentService, workflowRunLogFileResolver)
            .getLogs(7L);

    assertEquals(
        List.of("validate", "build", "deploy"),
        response.groups().stream().map(WorkflowRunLogGroupDto::name).toList());
  }

  @Test
  void getLogsPreservesGitHubSquareBracketMarkers() throws Exception {
    Path runDirectory = tempDir.resolve("repositories/99/workflow-runs/7");
    Files.createDirectories(runDirectory.resolve("deploy"));
    Files.writeString(
        runDirectory.resolve("deploy/system.txt"),
        """
        2026-03-12T20:46:28.9850146Z ##[command]/usr/bin/git status
        2026-03-12T20:46:29.0850146Z ##[warning]Using a deprecated flag
        2026-03-12T20:46:30.0850146Z ##[error]Process completed with exit code 1
        2026-03-12T20:46:31.0850146Z ##[group]Finishing: deploy
        2026-03-12T20:46:32.0850146Z ##[endgroup]
        """);
    WorkflowRunLogManifest manifest =
        new WorkflowRunLogManifest(7L, 99L, OffsetDateTime.parse("2026-03-12T10:15:30Z"), 1);
    WorkflowRun workflowRun = createWorkflowRun(7L, "deploy", "Deploy preview");

    when(workflowRunLogStorageService.ensureLogsCached(7L))
        .thenReturn(new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, false));
    when(deploymentService.getWorkflowJobStatus(7L)).thenReturn(new WorkflowJobsResponse());

    WorkflowRunLogsResponse response =
        new WorkflowRunLogReaderService(
                workflowRunLogStorageService, deploymentService, workflowRunLogFileResolver)
            .getLogs(7L);

    assertEquals(
        """
        [command]/usr/bin/git status
        [warning]Using a deprecated flag
        [error]Process completed with exit code 1
        [group]Finishing: deploy
        [endgroup]""",
        response.groups().getFirst().files().getFirst().content());
  }

  @Test
  void getLogsEnrichesGroupsAndFilesWithGitHubJobAndStepStatus() throws Exception {
    Path runDirectory = tempDir.resolve("repositories/99/workflow-runs/7");
    Files.createDirectories(runDirectory.resolve("build"));
    Files.writeString(runDirectory.resolve("build/1_checkout.txt"), "checkout log");
    Files.writeString(runDirectory.resolve("build/2_test.txt"), "test log");
    Files.writeString(runDirectory.resolve("build/system.txt"), "system log");
    final WorkflowRunLogManifest manifest =
        new WorkflowRunLogManifest(7L, 99L, OffsetDateTime.parse("2026-03-12T10:15:30Z"), 3);
    final WorkflowRun workflowRun = createWorkflowRun(7L, "deploy", "Deploy preview");

    final WorkflowJobsResponse workflowJobsResponse = new WorkflowJobsResponse();
    WorkflowJobDto workflowJob = new WorkflowJobDto();
    workflowJob.setName("build");
    workflowJob.setStatus("completed");
    workflowJob.setConclusion("failure");

    WorkflowStepDto checkoutStep = new WorkflowStepDto();
    checkoutStep.setNumber(1);
    checkoutStep.setName("Checkout");
    checkoutStep.setStatus("completed");
    checkoutStep.setConclusion("success");

    WorkflowStepDto testStep = new WorkflowStepDto();
    testStep.setNumber(2);
    testStep.setName("Test");
    testStep.setStatus("completed");
    testStep.setConclusion("failure");

    workflowJob.setSteps(List.of(checkoutStep, testStep));
    workflowJobsResponse.setJobs(List.of(workflowJob));

    when(workflowRunLogStorageService.ensureLogsCached(7L))
        .thenReturn(new WorkflowRunLogCacheResult(workflowRun, runDirectory, manifest, false));
    when(deploymentService.getWorkflowJobStatus(7L)).thenReturn(workflowJobsResponse);

    WorkflowRunLogsResponse response =
        new WorkflowRunLogReaderService(
                workflowRunLogStorageService, deploymentService, workflowRunLogFileResolver)
            .getLogs(7L);

    WorkflowRunLogGroupDto buildGroup = response.groups().getFirst();
    assertEquals("build", buildGroup.name());
    assertEquals("build", buildGroup.jobName());
    assertEquals("completed", buildGroup.jobStatus());
    assertEquals("failure", buildGroup.jobConclusion());
    assertEquals(
        List.of("Checkout", "Test"),
        buildGroup.steps().stream().map(WorkflowRunLogStepDto::name).toList());

    List<WorkflowRunLogFileDto> files = buildGroup.files();
    assertEquals(Integer.valueOf(1), files.get(0).stepNumber());
    assertEquals("Checkout", files.get(0).stepName());
    assertEquals("success", files.get(0).stepConclusion());
    assertEquals(null, files.get(0).stepStartedAt());
    assertEquals(null, files.get(0).stepCompletedAt());
    assertEquals(Integer.valueOf(2), files.get(1).stepNumber());
    assertEquals("Test", files.get(1).stepName());
    assertEquals("failure", files.get(1).stepConclusion());
    assertEquals(null, files.get(2).stepNumber());
    assertEquals(null, files.get(2).stepConclusion());
  }

  private WorkflowRun createWorkflowRun(Long workflowRunId, String name, String displayTitle) {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(99L);
    repository.setNameWithOwner("owner/repo");

    WorkflowRun workflowRun = new WorkflowRun();
    workflowRun.setId(workflowRunId);
    workflowRun.setRepository(repository);
    workflowRun.setName(name);
    workflowRun.setDisplayTitle(displayTitle);
    workflowRun.setHtmlUrl("https://github.com/owner/repo/actions/runs/" + workflowRunId);
    workflowRun.setStatus(WorkflowRun.Status.COMPLETED);
    workflowRun.setConclusion(java.util.Optional.of(WorkflowRun.Conclusion.SUCCESS));
    return workflowRun;
  }
}
