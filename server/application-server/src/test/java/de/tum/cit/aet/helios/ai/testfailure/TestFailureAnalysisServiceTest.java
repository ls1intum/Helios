package de.tum.cit.aet.helios.ai.testfailure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.TestCase;
import de.tum.cit.aet.helios.tests.TestCaseRepository;
import de.tum.cit.aet.helios.tests.TestSuite;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TestFailureAnalysisServiceTest {

  @Mock private TestCaseRepository testCaseRepository;
  @Mock private TestFailureAnalyzer analyzer;

  private AiProperties aiProperties;
  private TestFailureAnalysisService service;

  @BeforeEach
  void setUp() {
    aiProperties = new AiProperties();
    aiProperties.setEnabled(true);
    service = new TestFailureAnalysisService(aiProperties, testCaseRepository, analyzer);
  }

  @Test
  void analyzeTestFailureReturnsCompletedResponseWhenAnalyzerSucceeds() {
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.FAILED);
    TestFailureAnalysisResultDto result =
        new TestFailureAnalysisResultDto(
            "Root cause summary",
            List.of("Hypothesis A"),
            List.of("Evidence A"),
            List.of("Fix A"),
            0.8,
            "openai",
            "gpt-4o-mini");

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));
    when(analyzer.analyze(testCase, aiProperties, "openai")).thenReturn(result);

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L);

    assertEquals(TestFailureAnalysisStatus.COMPLETED, response.status());
    assertEquals(5L, response.repositoryId());
    assertEquals(result, response.result());
    assertNotNull(response.analyzedAt());
    assertNotNull(response.durationMs());
    verify(analyzer).analyze(testCase, aiProperties, "openai");
  }

  @Test
  void analyzeTestFailureReturnsFailedResponseWhenAnalyzerThrows() {
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.ERROR);

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));
    when(analyzer.analyze(testCase, aiProperties, "openai"))
        .thenThrow(new IllegalStateException("provider unavailable"));

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L);

    assertEquals(TestFailureAnalysisStatus.FAILED, response.status());
    assertEquals(5L, response.repositoryId());
    assertNull(response.result());
    assertNotNull(response.errorMessage());
    assertNotNull(response.analyzedAt());
    assertNotNull(response.durationMs());
  }

  @Test
  void analyzeTestFailureThrowsWhenAiIsDisabled() {
    aiProperties.setEnabled(false);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> service.analyzeTestFailure(5L, 42L));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
  }

  @Test
  void analyzeTestFailureThrowsWhenTestCaseIsNotFailedOrErrored() {
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.PASSED);

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> service.analyzeTestFailure(5L, 42L));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
  }

  @Test
  void analyzeTestFailureUsesDefaultProviderWhenConfigured() {
    aiProperties.setDefaultProvider("openai");
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.FAILED);
    TestFailureAnalysisResultDto result =
        new TestFailureAnalysisResultDto(
            "Root cause summary",
            List.of("Hypothesis A"),
            List.of("Evidence A"),
            List.of("Fix A"),
            0.8,
            "openai",
            "gpt-4o-mini");

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));
    when(analyzer.analyze(testCase, aiProperties, "openai")).thenReturn(result);

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L);

    assertEquals(TestFailureAnalysisStatus.COMPLETED, response.status());
    assertEquals(result, response.result());
    verify(analyzer).analyze(testCase, aiProperties, "openai");
  }

  private TestCase createTestCase(
      Long repositoryId,
      Long workflowRunId,
      Long testTypeId,
      Long testSuiteId,
      TestCase.TestStatus status) {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(repositoryId);

    WorkflowRun workflowRun = new WorkflowRun();
    workflowRun.setId(workflowRunId);
    workflowRun.setRepository(repository);

    TestType testType = new TestType();
    testType.setId(testTypeId);

    TestSuite testSuite = new TestSuite();
    testSuite.setId(testSuiteId);
    testSuite.setWorkflowRun(workflowRun);
    testSuite.setTestType(testType);

    TestCase testCase = new TestCase();
    testCase.setId(42L);
    testCase.setStatus(status);
    testCase.setTestSuite(testSuite);
    return testCase;
  }
}
