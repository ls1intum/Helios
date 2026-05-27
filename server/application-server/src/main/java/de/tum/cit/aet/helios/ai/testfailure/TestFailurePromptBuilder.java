package de.tum.cit.aet.helios.ai.testfailure;

import org.springframework.stereotype.Component;

@Component
class TestFailurePromptBuilder {

  /** Builds the system prompt for test failure analysis. */
  @SuppressWarnings("checkstyle:LineLength")
  String buildSystemPrompt() {
    return """
        You are an expert assistant that performs root-cause analysis for software test failures.

        Analyze the provided test failure context and identify the most likely root causes with supporting evidence.

        Rules:
        - Base every hypothesis on explicit evidence from the provided context.
        - If evidence is insufficient, state that explicitly in the summary and evidence fields.
        - Keep recommendations concrete and directly actionable.
        - Set confidence between 0.0 and 1.0 based on the strength of available evidence.
        - Limit root cause hypotheses to the 3 most likely candidates.
        - Limit recommended fixes to the 5 most actionable items.
        """;
  }

  String buildUserPrompt(TestFailureContext ctx) {
    StringBuilder prompt = new StringBuilder();

    prompt.append(
        """
        Repository: %s
        Branch: %s
        Test Suite: %s
        Test Case: %s
        Failure Message: %s
        Error Type: %s

        === Stack Trace ===
        %s
        """
            .formatted(
                ctx.repositoryName(),
                ctx.branch(),
                ctx.testSuiteName(),
                ctx.testCaseName(),
                ctx.failureMessage(),
                ctx.errorType(),
                ctx.stackTrace()));

    if (ctx.testSourceFile() != null) {
      prompt.append("\n=== Test Source File ===\n").append(ctx.testSourceFile()).append("\n");
    }

    prompt
        .append("\n=== Test Case Logs ===\n")
        .append(ctx.testCaseLogs())
        .append("\n\n=== Test Suite Logs ===\n")
        .append(ctx.testSuiteLogs());

    return prompt.toString();
  }
}
