package de.tum.cit.aet.helios.workflow.logs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.cit.aet.helios.deployment.WorkflowJobDto;
import de.tum.cit.aet.helios.deployment.WorkflowJobsResponse;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowRunLogStorageServiceTest {

  @Mock private WorkflowRunRepository workflowRunRepository;

  @Mock private GitHubService gitHubService;

  @TempDir Path tempDir;

  private WorkflowRunLogStorageService workflowRunLogStorageService;
  private MockedStatic<RepositoryContext> repositoryContextMockedStatic;
  private ObjectMapper snakeCaseObjectMapper;
  private OffsetDateTime fixedNow;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    snakeCaseObjectMapper =
        objectMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    fixedNow = OffsetDateTime.parse("2026-03-13T12:00:00Z");

    WorkflowRunLogStorageProperties properties = new WorkflowRunLogStorageProperties(tempDir);
    workflowRunLogStorageService =
        new WorkflowRunLogStorageService(
            workflowRunRepository, gitHubService, properties, objectMapper) {
          @Override
          protected OffsetDateTime currentTime() {
            return fixedNow;
          }
        };
    repositoryContextMockedStatic = mockStatic(RepositoryContext.class);
  }

  @AfterEach
  void tearDown() {
    repositoryContextMockedStatic.close();
  }

  @Test
  void cacheLogsDownloadsWorkflowArchiveForRecentlyCompletedRuns() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", fixedNow.minusMinutes(30));
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.downloadWorkflowRunLogs("owner/repo", 7L))
        .thenReturn(
            createZipArchive(
                Map.of(
                    "job-1.txt", "first log line",
                    "nested/job-2.txt", "second log line")));

    WorkflowRunLogCacheResult response = workflowRunLogStorageService.ensureLogsCached(7L);

    assertFalse(response.cacheHit());
    assertEquals(2, response.manifest().fileCount());
    assertArrayEquals(
        "first log line".getBytes(StandardCharsets.UTF_8),
        Files.readAllBytes(response.runDirectory().resolve("job-1.txt")));
    assertArrayEquals(
        "second log line".getBytes(StandardCharsets.UTF_8),
        Files.readAllBytes(response.runDirectory().resolve("nested/job-2.txt")));
    verify(gitHubService).downloadWorkflowRunLogs("owner/repo", 7L);
    verify(gitHubService, never()).getWorkflowJobStatus("owner/repo", 7L);
    verify(gitHubService, never()).downloadWorkflowJobLogs("owner/repo", 101L);
  }

  @Test
  void cacheLogsUsesJobLogsForRunsCompletedMoreThanAnHourAgo() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", fixedNow.minusHours(2));
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.getWorkflowJobStatus("owner/repo", 7L))
        .thenReturn(
            createWorkflowJobsResponseJson(
                createWorkflowJob(101L, "build", fixedNow.minusHours(2)),
                createWorkflowJob(
                    102L, "integration/test", fixedNow.minusHours(2).plusMinutes(5))));
    when(gitHubService.downloadWorkflowJobLogs("owner/repo", 101L))
        .thenReturn(
            """
            2026-03-13T10:01:00Z Preparing runner
            2026-03-13T10:01:10Z Checking out sources
            2026-03-13T10:02:10Z Running integration suite
            """
                .getBytes(StandardCharsets.UTF_8));
    when(gitHubService.downloadWorkflowJobLogs("owner/repo", 102L))
        .thenReturn(
            """
            2026-03-13T10:05:00Z Integration tests starting
            2026-03-13T10:05:30Z Integration tests passed
            """
                .getBytes(StandardCharsets.UTF_8));

    WorkflowRunLogCacheResult response = workflowRunLogStorageService.ensureLogsCached(7L);

    assertFalse(response.cacheHit());
    assertEquals(2, response.manifest().fileCount());
    assertEquals(
        """
        2026-03-13T10:01:00Z Preparing runner
        2026-03-13T10:01:10Z Checking out sources
        2026-03-13T10:02:10Z Running integration suite
        """
            .stripTrailing(),
        Files.readString(response.runDirectory().resolve("0_build.txt")).stripTrailing());
    assertEquals(
        """
        2026-03-13T10:05:00Z Integration tests starting
        2026-03-13T10:05:30Z Integration tests passed
        """
            .stripTrailing(),
        Files.readString(response.runDirectory().resolve("1_integration_test.txt"))
            .stripTrailing());
    verify(gitHubService).getWorkflowJobStatus("owner/repo", 7L);
    verify(gitHubService, never()).downloadWorkflowRunLogs("owner/repo", 7L);
    verify(gitHubService).downloadWorkflowJobLogs("owner/repo", 101L);
    verify(gitHubService).downloadWorkflowJobLogs("owner/repo", 102L);
  }

  @Test
  void cacheLogsUsesLatestJobCompletionTimeWhenWorkflowTimestampIsMissing() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", null);
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.getWorkflowJobStatus("owner/repo", 7L))
        .thenReturn(
            createWorkflowJobsResponseJson(
                createWorkflowJob(101L, "build", fixedNow.minusMinutes(20))));
    when(gitHubService.downloadWorkflowRunLogs("owner/repo", 7L))
        .thenReturn(createZipArchive(Map.of("job-1.txt", "first log line")));

    WorkflowRunLogCacheResult response = workflowRunLogStorageService.ensureLogsCached(7L);

    assertFalse(response.cacheHit());
    assertTrue(Files.exists(response.runDirectory().resolve("job-1.txt")));
    verify(gitHubService).getWorkflowJobStatus("owner/repo", 7L);
    verify(gitHubService).downloadWorkflowRunLogs("owner/repo", 7L);
    verify(gitHubService, never()).downloadWorkflowJobLogs("owner/repo", 101L);
  }

  @Test
  void cacheLogsReusesExistingCacheWithoutRedownloading() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", fixedNow.minusMinutes(30));
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.downloadWorkflowRunLogs("owner/repo", 7L))
        .thenReturn(createZipArchive(Map.of("job-1.txt", "first log line")));

    WorkflowRunLogCacheResult first = workflowRunLogStorageService.ensureLogsCached(7L);
    WorkflowRunLogCacheResult second = workflowRunLogStorageService.ensureLogsCached(7L);

    assertFalse(first.cacheHit());
    assertTrue(second.cacheHit());
    verify(gitHubService, times(1)).downloadWorkflowRunLogs("owner/repo", 7L);
  }

  @Test
  void cacheLogsRejectsWorkflowRunOutsideRepositoryContext() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 100L, "owner/repo", fixedNow.minusMinutes(30));
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);

    assertThrows(
        EntityNotFoundException.class, () -> workflowRunLogStorageService.ensureLogsCached(7L));
    verify(gitHubService, never()).downloadWorkflowRunLogs("owner/repo", 7L);
  }

  @Test
  void cacheLogsRejectsWorkflowRunThatHasNotCompletedYet() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", fixedNow.minusMinutes(30));
    workflowRun.setStatus(WorkflowRun.Status.IN_PROGRESS);
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);

    assertThrows(
        IllegalStateException.class, () -> workflowRunLogStorageService.ensureLogsCached(7L));
    verify(gitHubService, never()).downloadWorkflowRunLogs("owner/repo", 7L);
  }

  @Test
  void cacheLogsRemovesIncompleteCacheWhenDownloadFails() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", fixedNow.minusMinutes(30));
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.downloadWorkflowRunLogs("owner/repo", 7L))
        .thenThrow(new IOException("GitHub unavailable"));

    assertThrows(IOException.class, () -> workflowRunLogStorageService.ensureLogsCached(7L));

    assertFalse(Files.exists(tempDir.resolve("repositories/99/workflow-runs/7")));
  }

  @Test
  void cacheLogsRejectsUnsafeZipEntries() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", fixedNow.minusMinutes(30));
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.downloadWorkflowRunLogs("owner/repo", 7L))
        .thenReturn(createZipArchive(Map.of("../evil.txt", "malicious")));

    assertThrows(IOException.class, () -> workflowRunLogStorageService.ensureLogsCached(7L));
    assertFalse(Files.exists(tempDir.resolve("evil.txt")));
  }

  @Test
  void cacheLogsFailsWhenNoCompletionTimeCanBeDetermined() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", null);
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.getWorkflowJobStatus("owner/repo", 7L))
        .thenReturn(createWorkflowJobsResponseJson(createWorkflowJob(101L, "build", null)));

    assertThrows(IOException.class, () -> workflowRunLogStorageService.ensureLogsCached(7L));
    verify(gitHubService, never()).downloadWorkflowRunLogs("owner/repo", 7L);
    verify(gitHubService, never()).downloadWorkflowJobLogs("owner/repo", 101L);
  }

  private WorkflowRun createWorkflowRun(
      Long workflowRunId,
      Long repositoryId,
      String nameWithOwner,
      OffsetDateTime updatedAt) {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(repositoryId);
    repository.setNameWithOwner(nameWithOwner);

    WorkflowRun workflowRun = new WorkflowRun();
    workflowRun.setId(workflowRunId);
    workflowRun.setRepository(repository);
    workflowRun.setStatus(WorkflowRun.Status.COMPLETED);
    workflowRun.setUpdatedAt(updatedAt);
    return workflowRun;
  }

  private WorkflowJobDto createWorkflowJob(
      Long workflowJobId, String name, OffsetDateTime completedAt) {
    WorkflowJobDto workflowJob = new WorkflowJobDto();
    workflowJob.setId(workflowJobId);
    workflowJob.setName(name);
    workflowJob.setCompletedAt(completedAt);
    return workflowJob;
  }

  private String createWorkflowJobsResponseJson(WorkflowJobDto... jobs) throws IOException {
    WorkflowJobsResponse response = new WorkflowJobsResponse();
    response.setTotalCount(jobs.length);
    response.setJobs(List.of(jobs));
    return snakeCaseObjectMapper.writeValueAsString(response);
  }

  private byte[] createZipArchive(Map<String, String> entries) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (ZipOutputStream zipOutput = new ZipOutputStream(outputStream)) {
      for (Map.Entry<String, String> entry : new LinkedHashMap<>(entries).entrySet()) {
        zipOutput.putNextEntry(new ZipEntry(entry.getKey()));
        zipOutput.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zipOutput.closeEntry();
      }
    }
    return outputStream.toByteArray();
  }
}
