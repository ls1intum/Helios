package de.tum.cit.aet.helios.deployment.github;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.github.adapter.GHDeploymentSourceAdapter;
import de.tum.cit.aet.helios.deployment.github.adapter.GitHubDeploymentSourceAdapter;
import org.kohsuke.github.GHDeployment;
import org.springframework.stereotype.Component;

@Component
public class DeploymentSourceFactory {

  public DeploymentSource create(Object source, Deployment.State state) {
    if (source instanceof GHDeployment) {
      return new GHDeploymentSourceAdapter((GHDeployment) source, state);
    } else if (source instanceof GitHubDeploymentDto) {
      return new GitHubDeploymentSourceAdapter((GitHubDeploymentDto) source, state);
    } else {
      throw new IllegalArgumentException(
          "Unsupported source type for deployment conversion: " + source.getClass().getName());
    }
  }
}
