package de.tum.cit.aet.helios.ai.testfailure;

/**
 * Immutable snapshot of all context collected for a single failing test case. Passed between
 * context assembly, prompt building, and response parsing stages.
 *
 * <p>{@code testSourceFile} is {@code null} when the source file could not be fetched from GitHub.
 */
record TestFailureContext(
    String repositoryName,
    String branch,
    String testSuiteName,
    String testCaseName,
    String failureMessage,
    String errorType,
    String stackTrace,
    String testCaseLogs,
    String testSuiteLogs,
    String testSourceFile) {}
