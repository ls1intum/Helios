package de.tum.cit.aet.helios.deployment.github;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Interface representing a source of deployment data.
 * This interface is necessary to convert from both GitHubDeploymentDto and GHDeployment
 * to our Deployment entity. One is obtained from a REST call and the other is
 * received from the webhook, and they exhibit slight differences. To generalize and
 * utilize them for conversion, this interface is employed.
 */
public interface DeploymentSource {
    Long getId();

    String getNodeId();

    String getUrl();

    String getStatusesUrl();

    String getSha();

    String getRef();

    String getTask();

    String getEnvironment();

    String getRepositoryUrl();

    OffsetDateTime getCreatedAt() throws IOException;

    OffsetDateTime getUpdatedAt() throws IOException;
}