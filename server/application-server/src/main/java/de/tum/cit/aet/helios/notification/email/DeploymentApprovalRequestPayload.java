package de.tum.cit.aet.helios.notification.email;

import de.tum.cit.aet.helios.notification.NotificationPreference;

/**
 * Payload for the {@code deployment-approval-request} e-mail notification.
 *
 * <p>Sent to each User-type required reviewer when a deployment to a protected GitHub
 * environment is deferred to manual review (see {@code ApprovalService.reviewDeployment}'s
 * {@code DEFERRED_TO_REVIEWERS} branch). The "Review in Helios" link in the email lands
 * the reviewer on {@code /pending-approvals?focus=<deploymentId>}; authentication is the
 * regular Keycloak login flow + reviewer-list authorization on the approve/decline
 * endpoints — no extra token in the URL, so leaked emails cannot grant approval to the
 * wrong recipient.
 *
 * @param username           the reviewer's login (recipient personalisation)
 * @param environmentName    the environment requiring review
 * @param repositoryNameWithOwner  e.g. {@code "ls1intum/Helios"}
 * @param repositoryId       Helios's internal repo id (for routing back into the UI)
 * @param deploymentId       the {@code helios_deployment.id} the reviewer should resolve
 * @param creatorLogin       who clicked Deploy
 * @param branchName         source branch
 * @param sha                full commit sha (UI shows the short form)
 * @param workflowRunHtmlUrl link to the GitHub workflow run (for context)
 */
public record DeploymentApprovalRequestPayload(
    String username,
    String environmentName,
    String repositoryNameWithOwner,
    String repositoryId,
    String deploymentId,
    String creatorLogin,
    String branchName,
    String sha,
    String workflowRunHtmlUrl) implements EmailNotificationPayload {

  @Override
  public String template() {
    return "deployment-approval-request";
  }

  @Override
  public String subject() {
    return "Approval needed: deploy to %s by @%s".formatted(environmentName, creatorLogin);
  }

  @Override
  public NotificationPreference.Type type() {
    return NotificationPreference.Type.DEPLOYMENT_APPROVAL_REQUEST;
  }
}
