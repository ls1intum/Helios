package de.tum.cit.aet.helios.deployment.github.adapter;

import de.tum.cit.aet.helios.deployment.github.DeploymentSource;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentDto;

import java.time.OffsetDateTime;
import java.util.Map;

public class GitHubDeploymentSourceAdapter implements DeploymentSource {

    private final GitHubDeploymentDto gitHubDeploymentDto;

    public GitHubDeploymentSourceAdapter(GitHubDeploymentDto gitHubDeploymentDto) {
        this.gitHubDeploymentDto = gitHubDeploymentDto;
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