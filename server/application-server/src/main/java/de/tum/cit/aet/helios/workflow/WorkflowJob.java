package de.tum.cit.aet.helios.workflow;

import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A single job within a {@link WorkflowRun} — a GitHub Actions {@code workflow_job}. Persisted so
 * the pipeline view can render individual CI stages (e.g. {@code "Build / Build .war artifact"}) as
 * first-class nodes instead of only the whole run. Consolidated CI (Artemis' single "CI"
 * orchestrator run) exposes Build/Test/Quality/E2E as jobs within one run, so job-level data is
 * what makes those stages visible.
 *
 * <p>The GitHub job id is the primary key (via {@link BaseGitServiceEntity}); status and conclusion
 * reuse the {@link WorkflowRun} vocabulary. Tenant scoping is carried by the inherited
 * {@code repository} association.
 */
@Entity
@Table(name = "workflow_job")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class WorkflowJob extends BaseGitServiceEntity {

  /** Full GitHub job name, e.g. {@code "Build / Build .war artifact"}. */
  private String name;

  /** Name of the workflow this job belongs to, e.g. {@code "CI"}. */
  private String workflowName;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_run_id", nullable = false)
  @ToString.Exclude
  private WorkflowRun workflowRun;

  @Enumerated(EnumType.STRING)
  private WorkflowRun.Status status;

  @Enumerated(EnumType.STRING)
  private WorkflowRun.Conclusion conclusion;

  private OffsetDateTime startedAt;

  private OffsetDateTime completedAt;

  private String htmlUrl;

  private String headBranch;

  private String headSha;
}
