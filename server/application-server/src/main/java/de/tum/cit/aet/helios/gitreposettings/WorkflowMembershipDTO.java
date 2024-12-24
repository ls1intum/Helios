package de.tum.cit.aet.helios.gitreposettings;

import jakarta.validation.constraints.NotNull;

public record WorkflowMembershipDTO(@NotNull Long workflowId, @NotNull Integer orderIndex) {

  public static WorkflowMembershipDTO fromMembership(WorkflowGroupMembership membership) {
    return new WorkflowMembershipDTO(membership.getWorkflow().getId(), membership.getOrderIndex());
  }
}
