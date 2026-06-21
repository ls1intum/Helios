package de.tum.cit.aet.helios.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.tests.parsers.TestResultParser;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.function.InputStreamFunction;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestResultProcessorTest {

  @Mock private GitHubService gitHubService;
  @Mock private WorkflowRunRepository workflowRunRepository;
  @Mock private GitRepoRepository gitRepoRepository;
  @Mock private JunitParser junitParser;
  @Mock private TestCaseStatisticsService statisticsService;

  @InjectMocks private TestResultProcessor testResultProcessor;

  private WorkflowRun workflowRun;
  private Workflow workflow;
  private GitRepository gitRepository;
  private TestType javaTestType;

  @BeforeEach
  void setUp() {
    gitRepository = new GitRepository();
    gitRepository.setRepositoryId(1L);
    gitRepository.setDefaultBranch("main");

    workflow = new Workflow();
    javaTestType = new TestType();
    javaTestType.setId(1L);
    javaTestType.setName("Java Tests");
    javaTestType.setArtifactName("java-test-results");
    workflow.setTestTypes(Set.of(javaTestType));

    workflowRun = new WorkflowRun();
    workflowRun.setId(123L);
    workflowRun.setName("Test Workflow Run");
    workflowRun.setRepository(gitRepository);
    workflowRun.setWorkflow(workflow);
    workflowRun.setStatus(WorkflowRun.Status.COMPLETED);
    workflowRun.setTestProcessingStatus(null); // Eligible for processing by default
    workflowRun.setUpdatedAt(OffsetDateTime.now().minusMinutes(5)); // Recently updated
    workflowRun.setHeadBranch("feature-branch");
  }

  // Tests for shouldProcess
  @Test
  void shouldProcess_returnsTrue_whenEligible() {
    assertTrue(testResultProcessor.shouldProcess(workflowRun));
  }

  @Test
  void shouldProcess_returnsFalse_whenStatusNotCompleted() {
    workflowRun.setStatus(WorkflowRun.Status.IN_PROGRESS);
    assertFalse(testResultProcessor.shouldProcess(workflowRun));
  }

  @Test
  void shouldProcess_returnsFalse_whenNoTestTypes() {
    workflow.setTestTypes(Collections.emptySet());
    assertFalse(testResultProcessor.shouldProcess(workflowRun));
  }

  @Test
  void shouldProcess_returnsFalse_whenTooOld() {
    workflowRun.setUpdatedAt(OffsetDateTime.now().minusHours(3));
    assertFalse(testResultProcessor.shouldProcess(workflowRun));
  }

  @Test
  void shouldProcess_returnsFalse_whenAlreadyProcessed() {
    workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSED);
    assertFalse(testResultProcessor.shouldProcess(workflowRun));
  }

  @Test
  void shouldProcess_returnsFalse_whenProcessingFailed() {
    workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.FAILED);
    assertFalse(testResultProcessor.shouldProcess(workflowRun));
  }

  @Test
  void shouldProcess_returnsFalse_whenCurrentlyProcessing() {
    workflowRun.setTestProcessingStatus(WorkflowRun.TestProcessingStatus.PROCESSING);
    assertFalse(testResultProcessor.shouldProcess(workflowRun));
  }

  // --- Tests for processRun ---
  @Test
  void processRun_marksProcessedWithNoResults_whenRunHasNoArtifacts() throws IOException {
    // A completed CI run can legitimately carry no test artifact (conditional/skipped jobs).
    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> emptyArtifacts = mock(PagedIterable.class);
    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockEmptyIterator = mock(PagedIterator.class);
    when(mockEmptyIterator.hasNext()).thenReturn(false);
    when(emptyArtifacts.iterator()).thenReturn(mockEmptyIterator);
    when(gitHubService.getWorkflowRunArtifacts(anyLong(), anyLong())).thenReturn(emptyArtifacts);

    // Use an Answer to capture the status at the time of each save
    List<WorkflowRun.TestProcessingStatus> capturedStatuses = new ArrayList<>();
    doAnswer(
            invocation -> {
              WorkflowRun runArg = invocation.getArgument(0);
              capturedStatuses.add(runArg.getTestProcessingStatus());
              return null; // Void method
            })
        .when(workflowRunRepository)
        .save(any(WorkflowRun.class));

    testResultProcessor.processRun(workflowRun);

    // Verify save was called twice
    verify(workflowRunRepository, times(2)).save(any(WorkflowRun.class));

    // No artifacts is "no results", not a failure: PROCESSING -> PROCESSED with empty suites.
    assertEquals(2, capturedStatuses.size());
    assertEquals(WorkflowRun.TestProcessingStatus.PROCESSING, capturedStatuses.get(0));
    assertEquals(WorkflowRun.TestProcessingStatus.PROCESSED, capturedStatuses.get(1));
    assertNotNull(workflowRun.getTestSuites());
    assertTrue(workflowRun.getTestSuites().isEmpty());
  }

  @Test
  void processRun_setsStatusToProcessed_onSuccessfulProcessing() throws IOException {
    // Mock GitHubService to return an artifact
    GHArtifact mockArtifact = mock(GHArtifact.class);
    when(mockArtifact.getName()).thenReturn("java-test-results");

    TestResultParser.TestSuite parsedSuite =
        new TestResultParser.TestSuite(
            "TestSuite", LocalDateTime.now(), 1, 0, 0, 0, 0.0, "", Collections.emptyList());
    byte[] zipBytes =
        createMockZip("test-results.xml", "<testsuite name='TestSuite'></testsuite>".getBytes());

    // Mock download behavior using thenAnswer with inferred InputStreamFunction type
    when(mockArtifact.download(any()))
        .thenAnswer(
            invocation -> {
              InputStreamFunction<List<TestSuite>> function = invocation.getArgument(0);
              return function.apply(new ByteArrayInputStream(zipBytes));
            });

    // Mock PagedIterator
    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockPagedIterator = mock(PagedIterator.class);
    when(mockPagedIterator.hasNext()).thenReturn(true, false); // Iterate once
    when(mockPagedIterator.next()).thenReturn(mockArtifact);

    // Mock PagedIterable to return the mocked PagedIterator
    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> artifacts = mock(PagedIterable.class);
    when(artifacts.iterator()).thenReturn(mockPagedIterator);

    when(gitHubService.getWorkflowRunArtifacts(anyLong(), anyLong())).thenReturn(artifacts);

    // Mock JunitParser
    when(junitParser.supports(eq("test-results.xml"))).thenReturn(true);
    when(junitParser.parse(any(InputStream.class))).thenReturn(List.of(parsedSuite));

    // Mock GitRepoRepository for statistics update part
    when(gitRepoRepository.findById(anyLong())).thenReturn(Optional.of(gitRepository));

    testResultProcessor.processRun(workflowRun);

    assertEquals(WorkflowRun.TestProcessingStatus.PROCESSED, workflowRun.getTestProcessingStatus());
    assertNotNull(workflowRun.getTestSuites());
    assertFalse(workflowRun.getTestSuites().isEmpty());
    assertEquals("TestSuite", workflowRun.getTestSuites().get(0).getName());
    verify(statisticsService, atLeastOnce())
        .updateStatisticsForTestSuite(any(TestSuite.class), anyString(), any(GitRepository.class));
  }

  @Test
  void processRun_setsStatusToFailed_whenArtifactDownloadFails() throws IOException {
    when(gitHubService.getWorkflowRunArtifacts(anyLong(), anyLong()))
        .thenThrow(new IOException("Artifact fetch failed"));

    testResultProcessor.processRun(workflowRun);

    assertEquals(WorkflowRun.TestProcessingStatus.FAILED, workflowRun.getTestProcessingStatus());
    assertNull(workflowRun.getTestSuites());
    verify(workflowRunRepository, times(2)).save(workflowRun); // Initial processing, then failed
  }

  @Test
  void processRun_marksProcessedWithNoResults_whenNoArtifactMatchesTestType() throws IOException {
    GHArtifact mockArtifact = mock(GHArtifact.class);
    when(mockArtifact.getName()).thenReturn("other-artifact"); // Does not match javaTestType

    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockPagedIterator = mock(PagedIterator.class);
    when(mockPagedIterator.hasNext()).thenReturn(true, false);
    when(mockPagedIterator.next()).thenReturn(mockArtifact);

    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> artifacts = mock(PagedIterable.class);
    when(artifacts.iterator()).thenReturn(mockPagedIterator);

    when(gitHubService.getWorkflowRunArtifacts(anyLong(), anyLong())).thenReturn(artifacts);

    testResultProcessor.processRun(workflowRun);

    // No matching artifact is "no results" for this run, not a failure.
    assertEquals(WorkflowRun.TestProcessingStatus.PROCESSED, workflowRun.getTestProcessingStatus());
    assertNotNull(workflowRun.getTestSuites());
    assertTrue(workflowRun.getTestSuites().isEmpty());
    verify(workflowRunRepository, times(2)).save(workflowRun);
  }

  @Test
  void processRun_aggregatesPhasedArtifactsMatchedByGlob() throws IOException {
    // One test type whose artifact name is a glob spanning both E2E phases.
    TestType e2eTestType = new TestType();
    e2eTestType.setId(2L);
    e2eTestType.setName("E2E Tests");
    e2eTestType.setArtifactName("JUnit Test Results Phase *");
    workflow.setTestTypes(Set.of(e2eTestType));

    GHArtifact phase1 = mock(GHArtifact.class);
    when(phase1.getName()).thenReturn("JUnit Test Results Phase 1");
    GHArtifact phase2 = mock(GHArtifact.class);
    when(phase2.getName()).thenReturn("JUnit Test Results Phase 2");

    byte[] zip1 = createMockZip("results.xml", "<testsuite name='P1'></testsuite>".getBytes());
    byte[] zip2 = createMockZip("results.xml", "<testsuite name='P2'></testsuite>".getBytes());
    when(phase1.download(any()))
        .thenAnswer(
            invocation -> {
              InputStreamFunction<List<TestSuite>> fn = invocation.getArgument(0);
              return fn.apply(new ByteArrayInputStream(zip1));
            });
    when(phase2.download(any()))
        .thenAnswer(
            invocation -> {
              InputStreamFunction<List<TestSuite>> fn = invocation.getArgument(0);
              return fn.apply(new ByteArrayInputStream(zip2));
            });

    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockPagedIterator = mock(PagedIterator.class);
    when(mockPagedIterator.hasNext()).thenReturn(true, true, false);
    when(mockPagedIterator.next()).thenReturn(phase1, phase2);
    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> artifacts = mock(PagedIterable.class);
    when(artifacts.iterator()).thenReturn(mockPagedIterator);
    when(gitHubService.getWorkflowRunArtifacts(anyLong(), anyLong())).thenReturn(artifacts);

    when(junitParser.supports(eq("results.xml"))).thenReturn(true);
    TestResultParser.TestSuite s1 =
        new TestResultParser.TestSuite(
            "P1", LocalDateTime.now(), 1, 0, 0, 0, 0.0, "", Collections.emptyList());
    TestResultParser.TestSuite s2 =
        new TestResultParser.TestSuite(
            "P2", LocalDateTime.now(), 1, 0, 0, 0, 0.0, "", Collections.emptyList());
    when(junitParser.parse(any(InputStream.class))).thenReturn(List.of(s1), List.of(s2));
    when(gitRepoRepository.findById(anyLong())).thenReturn(Optional.of(gitRepository));

    testResultProcessor.processRun(workflowRun);

    assertEquals(WorkflowRun.TestProcessingStatus.PROCESSED, workflowRun.getTestProcessingStatus());
    assertNotNull(workflowRun.getTestSuites());
    // Both phases parsed and aggregated under the single E2E test type.
    assertEquals(2, workflowRun.getTestSuites().size());
    assertTrue(
        workflowRun.getTestSuites().stream().allMatch(ts -> ts.getTestType() == e2eTestType));
  }

  @Test
  void processRun_updatesStatsForDefaultBranchAndCombined_whenOnDefaultBranch() throws IOException {
    workflowRun.setHeadBranch("main"); // Set current branch to default

    // Mock GitHubService, artifact, download, parser (similar to successful processing test)
    GHArtifact mockArtifact = mock(GHArtifact.class);
    when(mockArtifact.getName()).thenReturn("java-test-results");
    TestResultParser.TestSuite parsedSuite =
        new TestResultParser.TestSuite(
            "TestSuite", LocalDateTime.now(), 1, 0, 0, 0, 0.0, "", Collections.emptyList());
    byte[] zipBytes =
        createMockZip("test-results.xml", "<testsuite name=\'TestSuite\'></testsuite>".getBytes());
    when(mockArtifact.download(any()))
        .thenAnswer(
            invocation -> {
              InputStreamFunction<List<TestSuite>> function = invocation.getArgument(0);
              return function.apply(new ByteArrayInputStream(zipBytes));
            });

    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockPagedIterator = mock(PagedIterator.class);
    when(mockPagedIterator.hasNext()).thenReturn(true, false);
    when(mockPagedIterator.next()).thenReturn(mockArtifact);

    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> artifacts = mock(PagedIterable.class);
    when(artifacts.iterator()).thenReturn(mockPagedIterator);
    when(gitHubService.getWorkflowRunArtifacts(anyLong(), anyLong())).thenReturn(artifacts);

    when(junitParser.supports(eq("test-results.xml"))).thenReturn(true);
    when(junitParser.parse(any(InputStream.class))).thenReturn(List.of(parsedSuite));
    when(gitRepoRepository.findById(anyLong()))
        .thenReturn(Optional.of(gitRepository)); // gitRepository has defaultBranch="main"

    testResultProcessor.processRun(workflowRun);

    assertEquals(WorkflowRun.TestProcessingStatus.PROCESSED, workflowRun.getTestProcessingStatus());
    verify(statisticsService)
        .updateStatisticsForTestSuite(any(TestSuite.class), eq("main"), eq(gitRepository));
    verify(statisticsService)
        .updateStatisticsForTestSuite(any(TestSuite.class), eq("combined"), eq(gitRepository));
  }

  // Helper to create a mock zip file in memory
  private byte[] createMockZip(String entryName, byte[] content) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      ZipEntry entry = new ZipEntry(entryName);
      zos.putNextEntry(entry);
      zos.write(content);
      zos.closeEntry();
    }
    return baos.toByteArray();
  }
}
