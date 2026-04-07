package de.tum.cit.aet.helios.workflow.logs.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

class WorkflowRunLogCleanupTaskTest {

  @TempDir Path tempDir;

  private ObjectMapper objectMapper;
  private OffsetDateTime fixedNow;
  private WorkflowRunLogStorageService workflowRunLogStorageService;
  private WorkflowRunLogArchiveExtractor workflowRunLogArchiveExtractor;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    fixedNow = OffsetDateTime.parse("2026-03-15T10:00:00Z");
    workflowRunLogArchiveExtractor = new WorkflowRunLogArchiveExtractor();
    workflowRunLogStorageService =
        new WorkflowRunLogStorageService(
            mock(WorkflowRunRepository.class),
            mock(GitHubService.class),
            new WorkflowRunLogStorageProperties(tempDir),
            objectMapper,
            workflowRunLogArchiveExtractor);
  }

  @Test
  void purgeDeletesExpiredWorkflowLogCacheDirectories() throws Exception {
    Path runDirectory = createRunDirectory("99", "7");
    writeManifest(runDirectory, 7L, 99L, fixedNow.minusDays(2));

    createTask(defaultProperties()).purge();

    assertFalse(Files.exists(runDirectory));
  }

  @Test
  void purgeKeepsWorkflowLogCacheDirectoriesAtRetentionBoundary() throws Exception {
    Path runDirectory = createRunDirectory("99", "7");
    writeManifest(runDirectory, 7L, 99L, fixedNow.minusDays(1));

    createTask(defaultProperties()).purge();

    assertTrue(Files.exists(runDirectory));
  }

  @Test
  void purgeSkipsDirectoriesWithoutManifest() throws Exception {
    Path runDirectory = createRunDirectory("99", "7");
    Files.writeString(runDirectory.resolve("job-1.txt"), "log");

    createTask(defaultProperties()).purge();

    assertTrue(Files.exists(runDirectory));
  }

  @Test
  void purgeSkipsDirectoriesWithMalformedManifest() throws Exception {
    Path runDirectory = createRunDirectory("99", "7");
    Files.writeString(runDirectory.resolve(WorkflowRunLogManifest.FILE_NAME), "{not-valid-json");

    createTask(defaultProperties()).purge();

    assertTrue(Files.exists(runDirectory));
  }

  @Test
  void purgeIgnoresTemporaryAndUnrelatedDirectories() throws Exception {
    Path tempDownloadDirectory = createRunDirectory("99", "7-workflow-logs-12345");
    writeManifest(tempDownloadDirectory, 7L, 99L, fixedNow.minusDays(3));

    Path unrelatedDirectory = createRunDirectory("99", "abc");
    writeManifest(unrelatedDirectory, 8L, 99L, fixedNow.minusDays(3));

    createTask(defaultProperties()).purge();

    assertTrue(Files.exists(tempDownloadDirectory));
    assertTrue(Files.exists(unrelatedDirectory));
  }

  @Test
  void purgeReportsCandidatesWithoutDeletingInDryRunMode() throws Exception {
    Path runDirectory = createRunDirectory("99", "7");
    writeManifest(runDirectory, 7L, 99L, fixedNow.minusDays(2));

    createTask(new WorkflowRunLogCleanupProperties("0 0 2 * * *", Duration.ofDays(1), true))
        .purge();

    assertTrue(Files.exists(runDirectory));
  }

  @Test
  void purgeOnlyTreatsFixedManifestFilenameAsAuthoritative() throws Exception {
    Path runDirectory = createRunDirectory("99", "7");
    objectMapper.writeValue(
        runDirectory.resolve("manifest-2026-03-13T10-00-00Z.json").toFile(),
        new WorkflowRunLogManifest(7L, 99L, fixedNow.minusDays(2), 1));

    createTask(defaultProperties()).purge();

    assertTrue(Files.exists(runDirectory));
  }

  @Test
  void purgeLeavesRunDirectoryInPlaceWhenDeletionFails() throws Exception {
    Path runDirectory = createRunDirectory("99", "7");
    writeManifest(runDirectory, 7L, 99L, fixedNow.minusDays(2));

    WorkflowRunLogStorageService failingStorageService =
        new WorkflowRunLogStorageService(
            mock(WorkflowRunRepository.class),
            mock(GitHubService.class),
            new WorkflowRunLogStorageProperties(tempDir),
            objectMapper,
            workflowRunLogArchiveExtractor) {
          @Override
          void deleteRecursively(Path path) throws IOException {
            throw new IOException("cannot delete " + path);
          }
        };

    createTask(defaultProperties(), failingStorageService).purge();

    assertTrue(Files.exists(runDirectory));
  }

  @Test
  void cleanupPropertiesExposeExpectedDefaults() {
    MockEnvironment environment = new MockEnvironment();
    ConfigurationPropertySources.attach(environment);

    WorkflowRunLogCleanupProperties properties =
        Binder.get(environment)
            .bindOrCreate(
                "helios.logs.cleanup", Bindable.of(WorkflowRunLogCleanupProperties.class));

    assertEquals("0 0 2 * * *", properties.cron());
    assertEquals(Duration.ofDays(1), properties.maxAge());
    assertFalse(properties.dryRun());
  }

  @Test
  void cleanupPropertiesBindConfiguredValues() {
    MockEnvironment environment = new MockEnvironment();
    environment.setProperty("helios.logs.cleanup.cron", "0 30 3 * * *");
    environment.setProperty("helios.logs.cleanup.max-age", "2d");
    environment.setProperty("helios.logs.cleanup.dry-run", "true");
    ConfigurationPropertySources.attach(environment);

    WorkflowRunLogCleanupProperties properties =
        Binder.get(environment)
            .bind("helios.logs.cleanup", Bindable.of(WorkflowRunLogCleanupProperties.class))
            .orElseThrow(() -> new AssertionError("Expected cleanup properties to bind"));

    assertEquals("0 30 3 * * *", properties.cron());
    assertEquals(Duration.ofDays(2), properties.maxAge());
    assertTrue(properties.dryRun());
  }

  private WorkflowRunLogCleanupTask createTask(WorkflowRunLogCleanupProperties properties) {
    return createTask(properties, workflowRunLogStorageService);
  }

  private WorkflowRunLogCleanupTask createTask(
      WorkflowRunLogCleanupProperties properties,
      WorkflowRunLogStorageService storageService) {
    return new WorkflowRunLogCleanupTask(
        new WorkflowRunLogStorageProperties(tempDir),
        properties,
        storageService) {
      @Override
      protected OffsetDateTime currentTime() {
        return fixedNow;
      }
    };
  }

  private WorkflowRunLogCleanupProperties defaultProperties() {
    return new WorkflowRunLogCleanupProperties("0 0 2 * * *", Duration.ofDays(1), false);
  }

  private Path createRunDirectory(String repositoryId, String directoryName) throws IOException {
    Path runDirectory =
        tempDir
            .resolve("repositories")
            .resolve(repositoryId)
            .resolve("workflow-runs")
            .resolve(directoryName);
    Files.createDirectories(runDirectory);
    return runDirectory;
  }

  private void writeManifest(
      Path runDirectory, Long workflowRunId, Long repositoryId, OffsetDateTime downloadedAt)
      throws IOException {
    objectMapper.writeValue(
        runDirectory.resolve(WorkflowRunLogManifest.FILE_NAME).toFile(),
        new WorkflowRunLogManifest(workflowRunId, repositoryId, downloadedAt, 1));
  }
}
