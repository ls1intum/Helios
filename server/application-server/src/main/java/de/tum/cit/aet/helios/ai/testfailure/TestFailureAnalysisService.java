package de.tum.cit.aet.helios.ai.testfailure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.ai.AiTextUtils;
import de.tum.cit.aet.helios.tests.TestCase;
import de.tum.cit.aet.helios.tests.TestCaseRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
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
  private final TestFailureAnalysisRepository testFailureAnalysisRepository;
  private final ObjectMapper objectMapper;

  public TestFailureAnalysisResponseDto analyzeTestFailure(
      Long repositoryId, Long testCaseId, boolean regenerate) {
    validateAiEnabled();

    TestCase testCase = resolveAndValidate(repositoryId, testCaseId);
    String providerId = resolveProviderId();

    if (!regenerate) {
      Optional<TestFailureAnalysisResponseDto> cachedResponse =
          readCachedResponse(repositoryId, testCase.getId(), providerId);
      if (cachedResponse.isPresent()) {
        return cachedResponse.get();
      }
    }

    OffsetDateTime startedAt = OffsetDateTime.now();
    try {
      TestFailureAnalysisResultDto result = analyzer.analyze(testCase, aiProperties, providerId);
      TestFailureAnalysisResponseDto response =
          buildSuccessResponse(repositoryId, startedAt, result);
      storeCompletedAnalysis(repositoryId, testCase, providerId, response, result);
      return response;
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
        repositoryId,
        TestFailureAnalysisStatus.COMPLETED,
        result,
        null,
        completedAt,
        durationMs,
        false);
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
        durationMs,
        false);
  }

  private Optional<TestFailureAnalysisResponseDto> readCachedResponse(
      Long repositoryId, Long testCaseId, String providerId) {
    try {
      return testFailureAnalysisRepository
          .findFirstByTestCase_IdAndProviderIdOrderByAnalyzedAtDescIdDesc(testCaseId, providerId)
          .flatMap(entry -> toCachedResponse(entry, repositoryId));
    } catch (Exception ex) {
      log.warn(
          "Failed to read cached AI analysis for repositoryId={} testCaseId={} provider={}",
          repositoryId,
          testCaseId,
          providerId,
          ex);
      return Optional.empty();
    }
  }

  private Optional<TestFailureAnalysisResponseDto> toCachedResponse(
      TestFailureAnalysis entry, Long repositoryId) {
    try {
      TestFailureAnalysisResultDto result =
          objectMapper.readValue(entry.getResultJson(), TestFailureAnalysisResultDto.class);
      return Optional.of(
          new TestFailureAnalysisResponseDto(
              repositoryId,
              TestFailureAnalysisStatus.COMPLETED,
              result,
              null,
              entry.getAnalyzedAt(),
              entry.getDurationMs(),
              true));
    } catch (JsonProcessingException ex) {
      log.warn(
          "Ignoring cached AI analysis with unreadable result payload id={}", entry.getId(), ex);
      return Optional.empty();
    }
  }

  private void storeCompletedAnalysis(
      Long repositoryId,
      TestCase testCase,
      String providerId,
      TestFailureAnalysisResponseDto response,
      TestFailureAnalysisResultDto result) {
    try {
      String resultJson = objectMapper.writeValueAsString(result);
      persistAnalysis(testCase, response, providerId, resultJson);
    } catch (Exception ex) {
      log.warn(
          "Failed to store completed AI analysis for repositoryId={} testCaseId={}",
          repositoryId,
          testCase.getId(),
          ex);
    }
  }

  private void persistAnalysis(
      TestCase testCase,
      TestFailureAnalysisResponseDto response,
      String providerId,
      String resultJson) {
    OffsetDateTime analyzedAt = response.analyzedAt();
    TestFailureAnalysis entry =
        testFailureAnalysisRepository
            .findFirstByTestCase_IdAndProviderIdOrderByAnalyzedAtDescIdDesc(
                testCase.getId(), providerId)
            .orElseGet(TestFailureAnalysis::new);

    entry.setTestCase(testCase);
    entry.setProviderId(providerId);
    entry.setResultJson(resultJson);
    entry.setAnalyzedAt(analyzedAt);
    entry.setDurationMs(response.durationMs());
    testFailureAnalysisRepository.save(entry);
  }

  private String resolveProviderId() {
    String configuredProvider = aiProperties.getDefaultProvider();
    if (configuredProvider == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "No AI provider configured.");
    }
    return configuredProvider.trim().toLowerCase(Locale.ROOT);
  }

  private void validateAiEnabled() {
    if (!aiProperties.isEnabled()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI analysis is disabled. Enable helios.ai.enabled first.");
    }
  }
}
