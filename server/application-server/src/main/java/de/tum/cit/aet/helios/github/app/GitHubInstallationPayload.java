package de.tum.cit.aet.helios.github.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


/**
 * Represents a minimal payload structure for a GitHub App installation event.
 * Contains only relevant fields for processing.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GitHubInstallationPayload {

  private String action;

  @JsonProperty("installation")
  private Installation installation;

  @JsonProperty("repositories_added")
  private List<Repository> repositoriesAdded;

  @JsonProperty("repositories_removed")
  private List<Repository> repositoriesRemoved;

  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  static class Installation {
    @JsonProperty("app_id")
    private long appId;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @ToString
  static class Repository {
    @JsonProperty("full_name")
    private String fullName;
  }
}