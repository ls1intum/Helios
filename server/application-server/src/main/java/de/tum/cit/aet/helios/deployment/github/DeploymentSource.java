package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Interface representing a source of deployment data. This interface is necessary to convert from
 * both GitHubDeploymentDto and GHDeployment to our Deployment entity. One is obtained from a REST
 * call and the other is received from the webhook, and they exhibit slight differences. To
 * generalize and utilize them for conversion, this interface is employed.
 */
public interface DeploymentSource {
  Long getId();

  String getNodeId();

  String getUrl();

  Deployment.State getState();

  String getStatusesUrl();

  String getSha();

  String getRef();

  String getTask();

  String getEnvironment();

  String getUserLogin();

  String getRepositoryUrl();

  OffsetDateTime getCreatedAt() throws IOException;

  OffsetDateTime getUpdatedAt() throws IOException;

  /**
   * Returns the workflow run ID associated with this deployment, if available.
   * Only populated from webhook payloads (top-level {@code workflow_run.id});
   * the GitHub REST deployments API does not expose this field.
   */
  default Long getWorkflowRunId() {
    return null;
  }
}
