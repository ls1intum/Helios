package de.tum.cit.aet.helios.deployment.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class GitHubDeploymentDto {

    private Long id;

    @JsonProperty("node_id")
    private String nodeId;

    private String url;

    private String sha;

    private String ref;

    private String task;

    @JsonProperty("original_environment")
    private String originalEnvironment;

    private String environment;

    private String description;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    @JsonProperty("statuses_url")
    private String statusesUrl;

    @JsonProperty("repository_url")
    private String repositoryUrl;

    @JsonProperty("transient_environment")
    private Boolean transientEnvironment;

    @JsonProperty("production_environment")
    private Boolean productionEnvironment;

    // Missing fields
    // GithubApp
    // GithubUser
    // payload
}
