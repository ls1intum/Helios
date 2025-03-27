package de.tum.cit.aet.helios.gitreposettings;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkflowGroupDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull Integer orderIndex,
    List<WorkflowMembershipDto> memberships) {

  public static WorkflowGroupDto fromWorkflowGroup(WorkflowGroup workflowGroup) {
    return new WorkflowGroupDto(
        workflowGroup.getId(),
        workflowGroup.getName(),
        workflowGroup.getOrderIndex(),
        workflowGroup.getMemberships().stream()
            .map(WorkflowMembershipDto::fromMembership)
            .toList());
  }
}
