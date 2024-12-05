package de.tum.cit.aet.helios.environment.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class GitHubEnvironmentApiResponse {

    @JsonProperty("total_count")
    private int totalCount;

    private List<GitHubEnvironmentDTO> environments;
}
