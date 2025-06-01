package de.tum.cit.aet.helios.deployment;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class WorkflowJobDto {
  private Long id;
  private String name;
  private String status;
  private String conclusion;
  private OffsetDateTime startedAt;
  private OffsetDateTime completedAt;
  private String workflowName;
  private String headBranch;

  // Runner information
  private Long runnerId;
  private String runnerName;
  private Long runnerGroupId;
  private String runnerGroupName;
  private List<String> labels;

  private String htmlUrl;

  private List<WorkflowStepDto> steps;
}
