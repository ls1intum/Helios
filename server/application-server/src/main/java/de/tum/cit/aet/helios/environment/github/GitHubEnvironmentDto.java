package de.tum.cit.aet.helios.environment.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class GitHubEnvironmentDto {

  private Long id;
  private String name;
  private String url;

  @JsonProperty("html_url")
  private String htmlUrl;

  @JsonProperty("created_at")
  private OffsetDateTime createdAt;

  @JsonProperty("updated_at")
  private OffsetDateTime updatedAt;

  @JsonProperty("protection_rules")
  private List<GitHubEnvironmentProtectionRuleDto> protectionRules;
}
