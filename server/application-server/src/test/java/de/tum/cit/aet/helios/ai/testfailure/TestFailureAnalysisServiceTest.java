package de.tum.cit.aet.helios.ai.testfailure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.TestCase;
import de.tum.cit.aet.helios.tests.TestCaseRepository;
import de.tum.cit.aet.helios.tests.TestSuite;
import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

  @Mock private AuthService authService;
  @Mock private TestCaseRepository testCaseRepository;
  @Mock private TestFailureAnalyzer analyzer;
  @Mock private TestFailureAnalysisRepository testFailureAnalysisRepository;

  private AiProperties aiProperties;
  private TestFailureAnalysisService service;

  @BeforeEach
  void setUp() {
    aiProperties = new AiProperties();
    aiProperties.setEnabled(true);
    aiProperties.setDefaultProvider("openai");
    aiProperties.getTestFailure().setMaxSectionChars(6000);
    lenient().when(authService.getGithubId()).thenReturn("test-user");
    lenient()
        .when(testFailureAnalysisRepository.save(any(TestFailureAnalysis.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    service =
        new TestFailureAnalysisService(
            aiProperties,
            authService,
            testCaseRepository,
            analyzer,
            testFailureAnalysisRepository,
            new ObjectMapper());
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
    when(
            testFailureAnalysisRepository
                .findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
                    42L, "openai", TestFailureAnalysisStatus.COMPLETED))
        .thenReturn(Optional.empty());
    when(analyzer.analyze(testCase, aiProperties, "openai")).thenReturn(result);

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L, false);

    assertEquals(TestFailureAnalysisResponseStatus.COMPLETED, response.status());
    assertEquals(5L, response.repositoryId());
    assertEquals(result, response.result());
    assertNotNull(response.analyzedAt());
    assertNotNull(response.durationMs());
    assertFalse(response.cacheHit());
    verify(analyzer).analyze(testCase, aiProperties, "openai");
    verify(testFailureAnalysisRepository, atLeastOnce()).save(any(TestFailureAnalysis.class));
  }

  @Test
  void analyzeTestFailureReturnsFailedResponseWhenAnalyzerThrows() {
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.ERROR);

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));
    when(
            testFailureAnalysisRepository
                .findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
                    42L, "openai", TestFailureAnalysisStatus.COMPLETED))
        .thenReturn(Optional.empty());
    when(analyzer.analyze(testCase, aiProperties, "openai"))
        .thenThrow(new IllegalStateException("provider unavailable"));

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L, false);

    assertEquals(TestFailureAnalysisResponseStatus.FAILED, response.status());
    assertEquals(5L, response.repositoryId());
    assertNull(response.result());
    assertNotNull(response.errorMessage());
    assertNotNull(response.analyzedAt());
    assertNotNull(response.durationMs());
    assertFalse(response.cacheHit());
    verify(testFailureAnalysisRepository, atLeastOnce()).save(any(TestFailureAnalysis.class));
  }

  @Test
  void analyzeTestFailureThrowsWhenAiIsDisabled() {
    aiProperties.setEnabled(false);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> service.analyzeTestFailure(5L, 42L, false));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
  }

  @Test
  void analyzeTestFailureThrowsWhenTestCaseIsNotFailedOrErrored() {
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.PASSED);

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> service.analyzeTestFailure(5L, 42L, false));

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
    when(
            testFailureAnalysisRepository
                .findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
                    42L, "openai", TestFailureAnalysisStatus.COMPLETED))
        .thenReturn(Optional.empty());
    when(analyzer.analyze(testCase, aiProperties, "openai")).thenReturn(result);

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L, false);

    assertEquals(TestFailureAnalysisResponseStatus.COMPLETED, response.status());
    assertEquals(result, response.result());
    assertFalse(response.cacheHit());
    verify(analyzer).analyze(testCase, aiProperties, "openai");
  }

  @Test
  void analyzeTestFailureReturnsCachedCompletedResponseWhenEntryExists() throws Exception {
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.FAILED);
    TestFailureAnalysisResultDto cachedResult =
        new TestFailureAnalysisResultDto(
            "Cached summary",
            List.of("Hypothesis A"),
            List.of("Evidence A"),
            List.of("Fix A"),
            0.8,
            "openai",
            "gpt-4o-mini");
    OffsetDateTime completedAt = OffsetDateTime.now().minusHours(1).withNano(0);

    TestFailureAnalysis cachedEntry = new TestFailureAnalysis();
    cachedEntry.setId(10L);
    cachedEntry.setTestCase(testCase);
    cachedEntry.setProviderId("openai");
    cachedEntry.setStatus(TestFailureAnalysisStatus.COMPLETED);
    cachedEntry.setCreatedAt(completedAt.minusSeconds(10));
    cachedEntry.setResultJson(new ObjectMapper().writeValueAsString(cachedResult));
    cachedEntry.setUpdatedAt(completedAt);
    cachedEntry.setDurationMs(123L);

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));
    when(
            testFailureAnalysisRepository
                .findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
                    42L, "openai", TestFailureAnalysisStatus.COMPLETED))
        .thenReturn(Optional.of(cachedEntry));

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L, false);

    assertEquals(TestFailureAnalysisResponseStatus.COMPLETED, response.status());
    assertEquals(cachedResult, response.result());
    assertEquals(completedAt, response.analyzedAt());
    assertEquals(123L, response.durationMs());
    assertTrue(response.cacheHit());
    verify(testFailureAnalysisRepository)
        .findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
            42L, "openai", TestFailureAnalysisStatus.COMPLETED);
    verify(analyzer, never()).analyze(any(), any(), anyString());
    verify(testFailureAnalysisRepository, never()).save(any(TestFailureAnalysis.class));
  }

  @Test
  void analyzeTestFailureRegenerateBypassesCachedEntry() throws Exception {
    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.FAILED);
    TestFailureAnalysisResultDto refreshedResult =
        new TestFailureAnalysisResultDto(
            "Fresh summary",
            List.of("Hypothesis B"),
            List.of("Evidence B"),
            List.of("Fix B"),
            0.9,
            "openai",
            "gpt-4o-mini");

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));
    when(analyzer.analyze(testCase, aiProperties, "openai")).thenReturn(refreshedResult);

    TestFailureAnalysisResponseDto response = service.analyzeTestFailure(5L, 42L, true);

    assertEquals(TestFailureAnalysisResponseStatus.COMPLETED, response.status());
    assertEquals(refreshedResult, response.result());
    assertFalse(response.cacheHit());
    verify(analyzer).analyze(testCase, aiProperties, "openai");
    verify(testFailureAnalysisRepository, atLeastOnce()).save(any(TestFailureAnalysis.class));
  }

  @Test
  void analyzeTestFailureThrowsRateLimitExceptionWhenBurstLimitIsExceeded() {
    aiProperties.getTestFailure().getRateLimit().setEnabled(true);
    aiProperties.getTestFailure().getRateLimit().setPerUserBurstMax(5);
    aiProperties.getTestFailure().getRateLimit().setPerUserBurstWindow(Duration.ofMinutes(10));

    TestCase testCase = createTestCase(5L, 100L, 11L, 7L, TestCase.TestStatus.FAILED);
    TestFailureAnalysis oldestRequestInWindow = new TestFailureAnalysis();
    oldestRequestInWindow.setCreatedAt(OffsetDateTime.now().minusMinutes(9));

    when(testCaseRepository.findForTestFailureAnalysisByTestCaseId(5L, 42L))
        .thenReturn(Optional.of(testCase));
    when(
            testFailureAnalysisRepository
                .findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
                    42L, "openai", TestFailureAnalysisStatus.COMPLETED))
        .thenReturn(Optional.empty());
    when(testFailureAnalysisRepository.countByRequesterUserIdAndCreatedAtAfter(any(), any()))
        .thenReturn(5L);
    when(
            testFailureAnalysisRepository
                .findFirstByRequesterUserIdAndCreatedAtAfterOrderByCreatedAtAsc(any(), any()))
        .thenReturn(Optional.of(oldestRequestInWindow));

    TestFailureAnalysisRateLimitExceededException exception =
        assertThrows(
            TestFailureAnalysisRateLimitExceededException.class,
            () -> service.analyzeTestFailure(5L, 42L, false));

    long retryAfterMinutes = Math.max(1, (exception.getRetryAfterSeconds() + 59) / 60);
    String retryAfterText =
        retryAfterMinutes == 1 ? "1 minute" : retryAfterMinutes + " minutes";
    assertEquals(
        "You've reached the limit for AI analysis requests. Please try again in "
            + retryAfterText
            + ".",
        exception.getMessage());
    assertTrue(exception.getRetryAfterSeconds() > 0);
    verify(analyzer, never()).analyze(any(), any(), anyString());
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
