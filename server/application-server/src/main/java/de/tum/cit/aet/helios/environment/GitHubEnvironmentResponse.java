package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class GitHubEnvironmentResponse {

    @JsonProperty("total_count")
    private int totalCount;

    private List<GitHubEnvironment> environments;
}
