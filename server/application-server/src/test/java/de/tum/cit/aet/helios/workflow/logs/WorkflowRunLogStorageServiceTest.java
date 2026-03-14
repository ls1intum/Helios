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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
  private OffsetDateTime fixedNow;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
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
  }

  @Test
  void cacheLogsDownloadsWorkflowArchiveForOlderRuns() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", fixedNow.minusHours(2));
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.downloadWorkflowRunLogs("owner/repo", 7L))
        .thenReturn(
            createZipArchive(
                Map.of(
                    "old-job.txt", "older run log",
                    "nested/step.txt", "still from run archive")));

    WorkflowRunLogCacheResult response = workflowRunLogStorageService.ensureLogsCached(7L);

    assertFalse(response.cacheHit());
    assertEquals(2, response.manifest().fileCount());
    assertEquals(
        "older run log", Files.readString(response.runDirectory().resolve("old-job.txt")));
    assertEquals(
        "still from run archive",
        Files.readString(response.runDirectory().resolve("nested/step.txt")));
    verify(gitHubService).downloadWorkflowRunLogs("owner/repo", 7L);
    verify(gitHubService, never()).getWorkflowJobStatus("owner/repo", 7L);
  }

  @Test
  void cacheLogsDownloadsWorkflowArchiveWhenWorkflowTimestampIsMissing() throws Exception {
    WorkflowRun workflowRun = createWorkflowRun(7L, 99L, "owner/repo", null);
    when(workflowRunRepository.findById(Long.valueOf(7L))).thenReturn(Optional.of(workflowRun));
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(99L);
    when(gitHubService.downloadWorkflowRunLogs("owner/repo", 7L))
        .thenReturn(createZipArchive(Map.of("job-1.txt", "first log line")));

    WorkflowRunLogCacheResult response = workflowRunLogStorageService.ensureLogsCached(7L);

    assertFalse(response.cacheHit());
    assertTrue(Files.exists(response.runDirectory().resolve("job-1.txt")));
    verify(gitHubService).downloadWorkflowRunLogs("owner/repo", 7L);
    verify(gitHubService, never()).getWorkflowJobStatus("owner/repo", 7L);
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
