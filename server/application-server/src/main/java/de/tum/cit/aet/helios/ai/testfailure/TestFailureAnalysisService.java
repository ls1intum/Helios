package de.tum.cit.aet.helios.ai.testfailure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.ai.AiTextUtils;
import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.tests.TestCase;
import de.tum.cit.aet.helios.tests.TestCaseRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Log4j2
public class TestFailureAnalysisService {
  private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  private final AiProperties aiProperties;
  private final AuthService authService;
  private final TestCaseRepository testCaseRepository;
  private final TestFailureAnalyzer analyzer;
  private final TestFailureAnalysisRepository testFailureAnalysisRepository;
  private final ObjectMapper objectMapper;
  private final Object rateLimitMutex = new Object();

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

    String requesterUserId = resolveRequesterUserId();
    TestFailureAnalysis analysisRecord =
        reserveAnalysisRecord(testCase, providerId, requesterUserId);
    try {
      TestFailureAnalysisResultDto result = analyzer.analyze(testCase, aiProperties, providerId);
      TestFailureAnalysisResponseDto response =
          buildSuccessResponse(repositoryId, analysisRecord.getCreatedAt(), result);
      markAnalysisCompleted(analysisRecord, response, result);
      return response;
    } catch (Exception ex) {
      log.error(
          "AI analysis failed for repositoryId={}, testCaseId={}, provider={}",
          repositoryId,
          testCaseId,
          providerId,
          ex);
      TestFailureAnalysisResponseDto failureResponse =
          buildFailureResponse(repositoryId, analysisRecord.getCreatedAt(), ex);
      markAnalysisFailed(analysisRecord, failureResponse);
      return failureResponse;
    }
  }

  private TestFailureAnalysis reserveAnalysisRecord(
      TestCase testCase,
      String providerId,
      String requesterUserId) {
    OffsetDateTime now = OffsetDateTime.now();
    AiProperties.RateLimitProperties limits = aiProperties.getTestFailure().getRateLimit();

    // Synchronize the rate limit check and record creation to prevent race conditions
    // that could allow users to exceed limits when making concurrent requests.
    synchronized (rateLimitMutex) {
      if (limits.isEnabled()) {
        enforceRateLimits(requesterUserId, now, limits);
      }

      TestFailureAnalysis entry = new TestFailureAnalysis();
      entry.setTestCase(testCase);
      entry.setProviderId(providerId);
      entry.setRequesterUserId(requesterUserId);
      entry.setStatus(TestFailureAnalysisStatus.IN_PROGRESS);
      entry.setCreatedAt(now);
      entry.setUpdatedAt(now);
      return testFailureAnalysisRepository.save(entry);
    }
  }

  private void enforceRateLimits(
      String requesterUserId, OffsetDateTime now, AiProperties.RateLimitProperties limits) {
    enforceBurstLimit(requesterUserId, now, limits);
    enforceDailyUserLimit(requesterUserId, now, limits);
  }

  private void enforceBurstLimit(
      String requesterUserId, OffsetDateTime now, AiProperties.RateLimitProperties limits) {
    Duration window = limits.getPerUserBurstWindow();
    if (!isLimitEnabled(limits.getPerUserBurstMax()) || window == null || !window.isPositive()) {
      return;
    }

    OffsetDateTime cutoff = now.minus(window);
    long recentCount =
        testFailureAnalysisRepository.countByRequesterUserIdAndCreatedAtAfter(
            requesterUserId, cutoff);
    if (recentCount < limits.getPerUserBurstMax()) {
      return;
    }

    long retryAfterSeconds =
        testFailureAnalysisRepository
            .findFirstByRequesterUserIdAndCreatedAtAfterOrderByCreatedAtAsc(requesterUserId, cutoff)
            .map(entry -> computeRetryAfterSeconds(entry.getCreatedAt(), window, now))
            .orElse(window.getSeconds());
    long retryAfterMinutes = Math.max(1, (retryAfterSeconds + 59) / 60);
    String retryAfterText =
        retryAfterMinutes == 1 ? "1 minute" : retryAfterMinutes + " minutes";
    throw new TestFailureAnalysisRateLimitExceededException(
        "You've reached the limit for AI analysis requests. Please try again in "
            + retryAfterText
            + ".",
        retryAfterSeconds);
  }

  private void enforceDailyUserLimit(
      String requesterUserId, OffsetDateTime now, AiProperties.RateLimitProperties limits) {
    if (!isLimitEnabled(limits.getPerUserDailyMax())) {
      return;
    }

    OffsetDateTime dayStart = currentServerDayStart(now);
    long dailyCount =
        testFailureAnalysisRepository.countByRequesterUserIdAndCreatedAtAfter(
            requesterUserId, dayStart);
    if (dailyCount >= limits.getPerUserDailyMax()) {
      throw new TestFailureAnalysisRateLimitExceededException(
          "You've reached the limit for AI analysis requests. Please try again tomorrow.",
          durationUntilNextServerDay(now).toSeconds());
    }
  }

  private long computeRetryAfterSeconds(
      OffsetDateTime createdAt, Duration window, OffsetDateTime now) {
    OffsetDateTime retryAt = createdAt.plus(window);
    long seconds = Duration.between(now, retryAt).toSeconds();
    return Math.max(1, seconds);
  }

  private OffsetDateTime currentServerDayStart(OffsetDateTime now) {
    ZoneId serverZone = ZoneId.systemDefault();
    return now
        .toInstant()
        .atZone(serverZone)
        .toLocalDate()
        .atStartOfDay(serverZone)
        .toOffsetDateTime();
  }

  private Duration durationUntilNextServerDay(OffsetDateTime now) {
    ZoneId serverZone = ZoneId.systemDefault();
    ZonedDateTime serverNow = now.toInstant().atZone(serverZone);
    ZonedDateTime nextDayStart =
        serverNow.toLocalDate().plusDays(1).atStartOfDay(serverZone);
    return Duration.between(serverNow.toInstant(), nextDayStart.toInstant());
  }

  private boolean isLimitEnabled(int limit) {
    return limit > 0;
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
      Long repositoryId, OffsetDateTime createdAt, TestFailureAnalysisResultDto result) {
    OffsetDateTime completedAt = OffsetDateTime.now();
    long durationMs = Duration.between(createdAt, completedAt).toMillis();
    return new TestFailureAnalysisResponseDto(
        repositoryId,
        TestFailureAnalysisResponseStatus.COMPLETED,
        result,
        null,
        completedAt,
        durationMs,
        false);
  }

  private TestFailureAnalysisResponseDto buildFailureResponse(
      Long repositoryId, OffsetDateTime createdAt, Exception ex) {
    OffsetDateTime completedAt = OffsetDateTime.now();
    long durationMs = Duration.between(createdAt, completedAt).toMillis();
    return new TestFailureAnalysisResponseDto(
        repositoryId,
        TestFailureAnalysisResponseStatus.FAILED,
        null,
        AiTextUtils.truncate(ex.getMessage(), MAX_ERROR_MESSAGE_LENGTH),
        completedAt,
        durationMs,
        false);
  }

  private Optional<TestFailureAnalysisResponseDto> readCachedResponse(
      Long repositoryId, Long testCaseId, String providerId) {
    try {
      return testFailureAnalysisRepository
          .findFirstByTestCase_IdAndProviderIdAndStatusOrderByUpdatedAtDescIdDesc(
              testCaseId, providerId, TestFailureAnalysisStatus.COMPLETED)
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
              TestFailureAnalysisResponseStatus.COMPLETED,
              result,
              null,
              entry.getUpdatedAt(),
              entry.getDurationMs(),
              true));
    } catch (JsonProcessingException ex) {
      log.warn(
          "Ignoring cached AI analysis with unreadable result payload id={}", entry.getId(), ex);
      return Optional.empty();
    }
  }

  private void markAnalysisCompleted(
      TestFailureAnalysis analysisRecord,
      TestFailureAnalysisResponseDto response,
      TestFailureAnalysisResultDto result) {
    if (analysisRecord == null) {
      return;
    }
    try {
      String resultJson = objectMapper.writeValueAsString(result);
      analysisRecord.setStatus(TestFailureAnalysisStatus.COMPLETED);
      analysisRecord.setResultJson(resultJson);
      analysisRecord.setUpdatedAt(response.analyzedAt());
      analysisRecord.setDurationMs(response.durationMs());
      testFailureAnalysisRepository.save(analysisRecord);
    } catch (Exception ex) {
      log.warn(
          "Failed to store completed AI analysis row id={}", analysisRecord.getId(), ex);
    }
  }

  private void markAnalysisFailed(
      TestFailureAnalysis analysisRecord, TestFailureAnalysisResponseDto response) {
    if (analysisRecord == null) {
      return;
    }
    try {
      analysisRecord.setStatus(TestFailureAnalysisStatus.FAILED);
      analysisRecord.setUpdatedAt(response.analyzedAt());
      analysisRecord.setDurationMs(response.durationMs());
      testFailureAnalysisRepository.save(analysisRecord);
    } catch (Exception ex) {
      log.warn("Failed to store failed AI analysis row id={}", analysisRecord.getId(), ex);
    }
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
          "AI analysis is disabled.");
    }
  }

  private String resolveRequesterUserId() {
    String githubId = authService.getGithubId();
    if (!StringUtils.hasText(githubId)) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to resolve current user for AI analysis limits.");
    }
    return githubId;
  }
}
