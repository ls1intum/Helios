package de.tum.cit.aet.helios.deployment.github.adapter;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.kohsuke.github.GHDeployment;

public class GhDeploymentSourceAdapter implements DeploymentSource {

  private final GHDeployment ghDeployment;
  private final Deployment.State state;

  public GhDeploymentSourceAdapter(GHDeployment ghDeployment, Deployment.State state) {
    this.ghDeployment = ghDeployment;
    this.state = state;
  }

  @Override
  public Long getId() {
    return ghDeployment.getId();
  }

  @Override
  public String getNodeId() {
    return ghDeployment.getNodeId();
  }

  @Override
  public String getUrl() {
    return ghDeployment.getUrl().toString();
  }

  @Override
  public Deployment.State getState() {
    return state;
  }

  @Override
  public String getStatusesUrl() {
    return ghDeployment.getStatusesUrl().toString();
  }

  @Override
  public String getSha() {
    return ghDeployment.getSha();
  }

  @Override
  public String getRef() {
    return ghDeployment.getRef();
  }

  @Override
  public String getUserLogin() {
    try {
      return ghDeployment.getCreator().getLogin();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public String getTask() {
    return ghDeployment.getTask();
  }

  @Override
  public String getEnvironment() {
    return ghDeployment.getEnvironment();
  }

  @Override
  public String getRepositoryUrl() {
    return ghDeployment.getRepositoryUrl().toString();
  }

  @Override
  public OffsetDateTime getCreatedAt() throws IOException {
    return ghDeployment.getCreatedAt().toInstant().atOffset(OffsetDateTime.now().getOffset());
  }

  @Override
  public OffsetDateTime getUpdatedAt() throws IOException {
    return ghDeployment.getUpdatedAt().toInstant().atOffset(OffsetDateTime.now().getOffset());
  }
}
