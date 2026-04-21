package de.tum.cit.aet.helios.ai.testfailure;

import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.ai.AiTextUtils;
import de.tum.cit.aet.helios.tests.TestCase;
import de.tum.cit.aet.helios.tests.TestCaseRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestFailureAnalysisService {
  private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private final AiProperties aiProperties;
  private final TestCaseRepository testCaseRepository;
  private final TestFailureAnalyzer analyzer;

  public TestFailureAnalysisResponseDto analyzeTestFailure(Long repositoryId, Long testCaseId) {
    validateAiEnabled();

    TestCase testCase = resolveAndValidate(repositoryId, testCaseId);
    String providerId = aiProperties.getDefaultProvider();
    if (providerId == null || providerId.isBlank()) {
      providerId = "openai";
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    try {
      TestFailureAnalysisResultDto result = analyzer.analyze(testCase, aiProperties, providerId);
      return buildSuccessResponse(repositoryId, startedAt, result);
    } catch (Exception ex) {
      log.error(
          "AI analysis failed for repositoryId={}, testCaseId={}, provider={}",
          repositoryId,
          testCaseId,
          providerId,
          ex);
      return buildFailureResponse(repositoryId, startedAt, ex);
    }
  }

  private TestCase resolveAndValidate(Long repositoryId, Long testCaseId) {
    TestCase testCase =
        testCaseRepository
            .findForTestFailureAnalysisByTestCaseId(repositoryId, testCaseId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Failed test context not found for the given repository and test case."));

    if (testCase.getStatus() != TestCase.TestStatus.FAILED
        && testCase.getStatus() != TestCase.TestStatus.ERROR) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "AI analysis is only available for failed or errored tests.");
    }
    return testCase;
  }

  private TestFailureAnalysisResponseDto buildSuccessResponse(
      Long repositoryId, OffsetDateTime startedAt, TestFailureAnalysisResultDto result) {
    OffsetDateTime completedAt = OffsetDateTime.now();
    long durationMs = Duration.between(startedAt, completedAt).toMillis();
    return new TestFailureAnalysisResponseDto(
        repositoryId, TestFailureAnalysisStatus.COMPLETED, result, null, completedAt, durationMs);
  }

  private TestFailureAnalysisResponseDto buildFailureResponse(
      Long repositoryId, OffsetDateTime startedAt, Exception ex) {
    OffsetDateTime failedAt = OffsetDateTime.now();
    long durationMs = Duration.between(startedAt, failedAt).toMillis();
    return new TestFailureAnalysisResponseDto(
        repositoryId,
        TestFailureAnalysisStatus.FAILED,
        null,
        AiTextUtils.truncate(ex.getMessage(), MAX_ERROR_MESSAGE_LENGTH),
        failedAt,
        durationMs);
  }

  private void validateAiEnabled() {
    if (!aiProperties.isEnabled()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI analysis is disabled. Enable helios.ai.enabled first.");
    }
  }
}
