package de.tum.cit.aet.helios.ai.testfailure;

import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.ai.AiTextUtils;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.tests.TestCase;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
class TestFailureContextAssembler {
  private final GitHubService gitHubService;

  TestFailureContext assemble(TestCase testCase, AiProperties properties) {
    var testFailureProperties = properties.getTestFailure();
    var suite = testCase.getTestSuite();
    var run = suite.getWorkflowRun();
    String repositoryName = run.getRepository().getNameWithOwner();
    String headSha = run.getHeadSha();

    String stackTrace =
        AiTextUtils.truncate(
            AiTextUtils.nullableText(testCase.getStackTrace()),
            testFailureProperties.getMaxSectionChars());
    String testCaseLogs =
        AiTextUtils.truncate(
            AiTextUtils.nullableText(testCase.getSystemOut()),
            testFailureProperties.getMaxSectionChars());
    String testSuiteLogs =
        AiTextUtils.truncate(
            AiTextUtils.nullableText(suite.getSystemOut()),
            testFailureProperties.getMaxSectionChars());
    String testSourceFile =
        fetchTestSourceFile(
            repositoryName,
            headSha,
            testCase.getClassName(),
            testFailureProperties.getMaxSectionChars());

    return new TestFailureContext(
        repositoryName,
        AiTextUtils.nullableText(run.getHeadBranch()),
        suite.getName(),
        testCase.getName(),
        AiTextUtils.nullableText(testCase.getMessage()),
        AiTextUtils.nullableText(testCase.getErrorType()),
        stackTrace,
        testCaseLogs,
        testSuiteLogs,
        testSourceFile);
  }

  /**
   * Fetches the test source file from GitHub at the given commit SHA.
   *
   * <p>Tries standard Maven/Gradle source roots for Java and Kotlin. Returns {@code null} if the
   * file cannot be found or fetched.
   */
  private String fetchTestSourceFile(
      String repositoryNameWithOwner,
      String headSha,
      String fullyQualifiedClassName,
      int maxChars) {
    if (fullyQualifiedClassName == null || fullyQualifiedClassName.isBlank()) {
      return null;
    }
    if (headSha == null || headSha.isBlank()) {
      return null;
    }

    String relativePath = fullyQualifiedClassName.replace('.', '/');
    List<String> candidatePaths =
        List.of(
            "src/test/java/" + relativePath + ".java",
            "src/test/kotlin/" + relativePath + ".kt",
            "src/main/java/" + relativePath + ".java",
            "src/main/kotlin/" + relativePath + ".kt");

    try {
      for (String path : candidatePaths) {
        String content = gitHubService.getFileContent(repositoryNameWithOwner, path, headSha);
        if (content != null) {
          log.debug("Fetched test source file: {} at {}", path, headSha);
          return AiTextUtils.truncate(content, maxChars);
        }
      }
    } catch (IOException ex) {
      log.warn(
          "Could not access GitHub repository {} for source file fetch",
          repositoryNameWithOwner,
          ex);
    }

    log.debug(
        "No source file found for class {} in repository {}",
        fullyQualifiedClassName,
        repositoryNameWithOwner);
    return null;
  }
}
