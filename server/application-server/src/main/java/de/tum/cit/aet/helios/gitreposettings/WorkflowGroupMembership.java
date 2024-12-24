package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.workflow.Workflow;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "workflow_group_membership")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkflowGroupMembership {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_group_id")
  private WorkflowGroup workflowGroup;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_id")
  private Workflow workflow;

  // Ordering for workflows within a group
  private int orderIndex;
}
