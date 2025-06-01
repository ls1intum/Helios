package de.tum.cit.aet.helios.deployment;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class WorkflowStepDto {
  private String name;
  private Integer number;
  private String status;
  private String conclusion;
  private OffsetDateTime startedAt;
  private OffsetDateTime completedAt;
}
