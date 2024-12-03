package de.tum.cit.aet.helios.deployment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class GitHubDeployment {

    private Long id;

    @JsonProperty("node_id")
    private String nodeId;

    private String url;

    private String sha;

    private String ref;

    private String task;

    // JsonNode to handle dynamic payload types (object or string)
    private JsonNode payload;

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
}
