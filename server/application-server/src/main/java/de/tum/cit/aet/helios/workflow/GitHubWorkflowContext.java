package de.tum.cit.aet.helios.workflow;

/**
 * Context object for a GitHub workflow run.
 */
public record GitHubWorkflowContext(Long runId, String headBranch, String headSha) {
}
