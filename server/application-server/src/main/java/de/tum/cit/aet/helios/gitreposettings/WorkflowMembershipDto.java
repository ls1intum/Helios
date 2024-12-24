package de.tum.cit.aet.helios.gitreposettings;

import jakarta.validation.constraints.NotNull;

public record WorkflowMembershipDto(@NotNull Long workflowId, @NotNull Integer orderIndex) {

  public static WorkflowMembershipDto fromMembership(WorkflowGroupMembership membership) {
    return new WorkflowMembershipDto(membership.getWorkflow().getId(), membership.getOrderIndex());
  }
}
