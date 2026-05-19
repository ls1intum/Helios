package de.tum.cit.aet.helios.workflow.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Durable row per GitHub Actions workflow job. See plan §A.
 *
 * <p>Persisted alongside the existing deployment-timing path; this captures rows that the timing
 * service would otherwise discard.
 */
@Entity
@Table(name = "workflow_job")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkflowJob {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "workflow_run_id", nullable = false)
  private Long workflowRunId;

  @Column(name = "repository_id", nullable = false)
  private Long repositoryId;

  @Column(name = "name", nullable = false, length = 512)
  private String name;

  @Column(name = "workflow_name", length = 512)
  private String workflowName;

  @Column(name = "head_branch", length = 512)
  private String headBranch;

  @Column(name = "head_sha", length = 40)
  private String headSha;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "conclusion", length = 32)
  private String conclusion;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "queue_wait_seconds")
  private Integer queueWaitSeconds;

  @Column(name = "run_duration_seconds")
  private Integer runDurationSeconds;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "labels", columnDefinition = "text[]")
  private List<String> labels;

  @Column(name = "label_set_hash", length = 64)
  private String labelSetHash;

  @Column(name = "runner_id")
  private Long runnerId;

  @Column(name = "runner_name")
  private String runnerName;

  @Column(name = "runner_group_id")
  private Long runnerGroupId;

  @Column(name = "runner_group_name")
  private String runnerGroupName;

  @Enumerated(EnumType.STRING)
  @Column(name = "runner_kind", nullable = false, length = 16)
  private RunnerKind runnerKind = RunnerKind.UNKNOWN;

  @Enumerated(EnumType.STRING)
  @Column(name = "queued_reason", length = 32)
  private QueuedReason queuedReason;

  @Column(name = "is_stuck", nullable = false)
  private boolean isStuck;

  @Column(name = "stuck_detected_at")
  private OffsetDateTime stuckDetectedAt;

  @Column(name = "last_reconcile_attempt_at")
  private OffsetDateTime lastReconcileAttemptAt;

  public enum RunnerKind {
    GITHUB_HOSTED,
    SELF_HOSTED,
    UNKNOWN
  }

  public enum QueuedReason {
    NO_RUNNER_ONLINE,
    RUNNERS_BUSY,
    CONCURRENCY_LOCK,
    PENDING_APPROVAL,
    UNKNOWN
  }
}
