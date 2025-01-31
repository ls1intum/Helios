package de.tum.cit.aet.helios.deployment.github.adapter;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentDto;
import java.time.OffsetDateTime;

public class GitHubDeploymentSourceAdapter implements DeploymentSource {

  private final GitHubDeploymentDto gitHubDeploymentDto;

  private final Deployment.State state;

  public GitHubDeploymentSourceAdapter(
      GitHubDeploymentDto gitHubDeploymentDto, Deployment.State state) {
    this.gitHubDeploymentDto = gitHubDeploymentDto;
    this.state = state;
  }

  @Override
  public Long getId() {
    return gitHubDeploymentDto.getId();
  }

  @Override
  public String getNodeId() {
    return gitHubDeploymentDto.getNodeId();
  }

  @Override
  public String getUrl() {
    return gitHubDeploymentDto.getUrl();
  }

  @Override
  public Deployment.State getState() {
    return state;
  }

  @Override
  public String getStatusesUrl() {
    return gitHubDeploymentDto.getStatusesUrl();
  }

  @Override
  public String getSha() {
    return gitHubDeploymentDto.getSha();
  }

  @Override
  public String getRef() {
    return gitHubDeploymentDto.getRef();
  }

  @Override
  public String getTask() {
    return gitHubDeploymentDto.getTask();
  }

  @Override
  public String getEnvironment() {
    return gitHubDeploymentDto.getEnvironment();
  }

  @Override
  public String getUserLogin() {
    return gitHubDeploymentDto.getCreator().login();
  }

  @Override
  public String getRepositoryUrl() {
    return gitHubDeploymentDto.getRepositoryUrl();
  }

  @Override
  public OffsetDateTime getCreatedAt() {
    return gitHubDeploymentDto.getCreatedAt();
  }

  @Override
  public OffsetDateTime getUpdatedAt() {
    return gitHubDeploymentDto.getUpdatedAt();
  }
}
