package de.tum.cit.aet.helios.gitreposettings;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkflowGroupDTO(
    @NonNull Long id,
    @NotBlank String name,
    @NonNull Integer orderIndex,
    List<WorkflowMembershipDTO> memberships) {

  public static WorkflowGroupDTO fromWorkflowGroup(WorkflowGroup workflowGroup) {
    return new WorkflowGroupDTO(
        workflowGroup.getId(),
        workflowGroup.getName(),
        workflowGroup.getOrderIndex(),
        workflowGroup.getMemberships().stream()
            .map(WorkflowMembershipDTO::fromMembership)
            .toList());
  }
}
