package de.tum.cit.aet.helios.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubWorkflowRunsPage {

  @JsonProperty("total_count")
  private int totalCount;

  @Getter
  @JsonProperty("workflow_runs")
  private List<GitHubWorkflowRunItem> workflowRuns;

  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class GitHubWorkflowRunItem {
    private long id;
  }
}
