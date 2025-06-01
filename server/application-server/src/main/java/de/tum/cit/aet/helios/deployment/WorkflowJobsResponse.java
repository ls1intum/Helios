package de.tum.cit.aet.helios.deployment;

import java.util.List;
import lombok.Data;

@Data
public class WorkflowJobsResponse {
  private Integer totalCount;
  private List<WorkflowJobDto> jobs;
}
