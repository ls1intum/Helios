package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class GitHubEnvironment {

    private Long id;
    private String name;
    private String url;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}
