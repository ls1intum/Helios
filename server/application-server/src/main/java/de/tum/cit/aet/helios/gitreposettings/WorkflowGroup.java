package de.tum.cit.aet.helios.gitreposettings;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "workflow_group",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"repository_settings_id", "order_index"})
    })
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkflowGroup {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_settings_id")
  private GitRepoSettings gitRepoSettings;

  // Ordering for groups
  private int orderIndex;

  @OneToMany(mappedBy = "workflowGroup", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  private List<WorkflowGroupMembership> memberships = new ArrayList<>();
}
