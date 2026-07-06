package de.tum.cit.aet.helios.workflow.pipeline.config;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A configurable pipeline node within a {@link PipelineCategory}. Maps to GitHub Actions jobs by
 * name: a job matches when its name starts with any {@code jobNameMatchers} entry
 * (case-insensitive) and, if set, its workflow name contains {@code workflowNameMatcher}.
 * {@code nodeKey} avoids the SQL reserved word "key".
 */
@Entity
@Table(
    name = "pipeline_node",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"pipeline_category_id", "order_index"})
    })
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PipelineNode {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pipeline_category_id")
  @ToString.Exclude
  private PipelineCategory pipelineCategory;

  @Column(name = "node_key", nullable = false)
  private String nodeKey;

  @Column(nullable = false)
  private String label;

  private String workflowNameMatcher;

  private int orderIndex;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "pipeline_node_job_matcher",
      joinColumns = @JoinColumn(name = "pipeline_node_id"))
  @Column(name = "matcher")
  private List<String> jobNameMatchers = new ArrayList<>();
}
