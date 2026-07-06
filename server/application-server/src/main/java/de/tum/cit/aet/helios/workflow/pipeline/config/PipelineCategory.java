package de.tum.cit.aet.helios.workflow.pipeline.config;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

/**
 * A per-repository pipeline stage lane (e.g. "Build", "Test", "Quality"), owning an ordered list of
 * {@link PipelineNode}s. Hangs off {@link GitRepoSettings} so each repository has its own
 * configurable pipeline. Mirrors the ordering conventions of {@code WorkflowGroup}.
 */
@Entity
@Table(
    name = "pipeline_category",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"repository_settings_id", "order_index"})
    })
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PipelineCategory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_settings_id")
  @ToString.Exclude
  private GitRepoSettings gitRepoSettings;

  private int orderIndex;

  @OneToMany(mappedBy = "pipelineCategory", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @ToString.Exclude
  private List<PipelineNode> nodes = new ArrayList<>();
}
