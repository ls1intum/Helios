package de.tum.cit.aet.helios.workflow.pagination;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WorkflowRunPageRequest {
  @Builder.Default
  private int page = 1;
  @Builder.Default
  private int size = 20;
  private String sortField;
  private String sortDirection;
  @Builder.Default
  private WorkflowRunFilterType filterType = WorkflowRunFilterType.ALL;
  private String searchTerm;
}
