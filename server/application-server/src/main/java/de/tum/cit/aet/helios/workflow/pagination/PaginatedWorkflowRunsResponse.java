package de.tum.cit.aet.helios.workflow.pagination;

import de.tum.cit.aet.helios.workflow.WorkflowRunDto;
import java.util.List;

public record PaginatedWorkflowRunsResponse(
    List<WorkflowRunDto> runs,
    int page,
    int size,
    long totalElements,
    int totalPages) {}

