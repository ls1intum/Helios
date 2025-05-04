package de.tum.cit.aet.helios.notification.email;

/**
 * Payload for the {@code deployment-failure} e‑mail notification.
 * This payload is used to inform users about a failed deployment.
 *
 * @param username The username of the user who triggered the deployment.
 * @param sourceBranch The source branch of the deployment.
 * @param pullRequest The pull request associated with the deployment.
 * @param environment The environment where the deployment failed.
 * @param repositoryId The ID of the repository where the deployment occurred.
 * @param githubWorkflowUrl The URL of the GitHub workflow run.
 * @param repositoryName The name of the repository where the deployment occurred.
 */
public record DeploymentFailurePayload(
    String username,
    String sourceBranch,
    String pullRequest,
    String environment,
    String githubWorkflowUrl,
    String repositoryId,
    String repositoryName
) implements EmailNotificationPayload {

  @Override
  public String template() {
    return "deployment-failure";
  }

  @Override
  public String subject() {
    return "🚨 Deployment failed – %s (%s)".formatted(repositoryName, environment);
  }
}
