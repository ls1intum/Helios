package de.tum.cit.aet.helios.heliosdeployment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "helios_deployment")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class HeliosDeployment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "environment_id", nullable = false)
  private Environment environment;

  // user ID of the triggering user
  @Column(name = "triggering_user", nullable = false)
  private String user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.WAITING;

  @Column(name = "status_updated_at", nullable = false)
  private OffsetDateTime statusUpdatedAt;

  @Column(name = "branch_name")
  private String branchName;

  @Column(name = "source_branch_name")
  private String sourceBranchName;

  @Column(name = "build_tag")
  private String buildTag;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "workflow_params")
  private Map<String, Object> workflowParams;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deployment_id", nullable = true)
  private Long deploymentId;

  @Column(name = "workflow_run_id", nullable = true)
  private Long workflowRunId;

  @Column(name = "workflow_run_html_url", nullable = true)
  private String workflowRunHtmlUrl;

  private String sha;

  @ManyToOne
  @JoinColumn(name = "user_id")
  private User creator;

  @PrePersist
  protected void onCreate() {
    if (createdAt == null) {
      createdAt = OffsetDateTime.now();
    }
    updatedAt = OffsetDateTime.now();
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pull_request_id")
  private PullRequest pullRequest;

  @Column(name = "pre_deploy_duration_seconds")
  private Integer preDeployDurationSeconds;

  @Column(name = "deploy_duration_seconds")
  private Integer deployDurationSeconds;

  @Column(name = "deploy_job_started_at")
  private OffsetDateTime deployJobStartedAt;

  @Column(name = "workflow_started_at")
  private OffsetDateTime workflowStartedAt;

  /**
   * What Helios decided to do about required-reviewer approval when the {@code deployment_status}
   * webhook landed in WAITING state. Stamped from {@code ApprovalService} for audit visibility and
   * to short-circuit duplicate processing.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "auto_approval_decision", length = 40)
  private AutoApprovalDecision autoApprovalDecision;

  @Column(name = "auto_approval_at")
  private OffsetDateTime autoApprovalAt;

  // Enum to represent deployment status
  public enum Status {
    /**
     * Persisted before GitHub dispatch; we hold a row to attach approval state to without racing
     * the {@code deployment_status} webhook (which can arrive within ~100ms of dispatch).
     */
    PENDING_DISPATCH,
    /** Deployment called and waiting GitHub webhook listener. */
    WAITING,
    /** The queued. */
    QUEUED,
    /** Deployment is in progress. */
    IN_PROGRESS,
    /** Deployment successful finished. */
    DEPLOYMENT_SUCCESS,
    /** Deployment failed (Whether the deployment or optional build failed). */
    FAILED,
    /**
     * Deployment failed due to an I/O error. This only shows that there were a problem dispatching
     * the workflow.
     */
    IO_ERROR,
    /** Deployment was cancelled. */
    CANCELLED,
    /** Deployment status is unknown. */
    UNKNOWN;
  }

  /** Outcome of Helios's reviewer-aware auto-approval branch when the WAITING webhook arrives. */
  public enum AutoApprovalDecision {
    /** No required-reviewer protection rule, or the environment doesn't gate on reviewers. */
    NOT_APPLICABLE,
    /** Helios auto-approved on behalf of the deployer (they're a required reviewer). */
    AUTO_APPROVED,
    /**
     * The deployer is not a required reviewer; approval is deferred to the reviewers (via the
     * Helios pending-approvals UI in Phase 2; via email in Phase 3).
     */
    DEFERRED_TO_REVIEWERS,
    /**
     * A required-reviewer rule referenced a GitHub Team (which Helios doesn't yet expand). The
     * legacy "impersonate the deployment creator and try anyway" path was used as a fallback.
     */
    TEAM_REVIEWER_FALLBACK
  }

  public void setStatus(Status status) {
    this.status = status;
    // Automatically update timestamp when status is updated
    this.statusUpdatedAt = OffsetDateTime.now();
  }

  public static Status mapWorkflowRunStatus(
      WorkflowRun.Status status, WorkflowRun.Conclusion conclusion) {
    return switch (status) {
      case PENDING, WAITING, REQUESTED -> Status.WAITING;
      case QUEUED -> Status.QUEUED;
      case IN_PROGRESS -> Status.IN_PROGRESS;
      case COMPLETED -> mapCompletedConclusion(conclusion);
      case CANCELLED -> Status.CANCELLED;
      case SUCCESS -> Status.DEPLOYMENT_SUCCESS;
      case FAILURE, ACTION_REQUIRED, TIMED_OUT -> Status.FAILED;
      case NEUTRAL, SKIPPED, STALE, UNKNOWN -> Status.UNKNOWN;
    };
  }

  private static Status mapCompletedConclusion(WorkflowRun.Conclusion conclusion) {
    if (conclusion == null) {
      return Status.UNKNOWN;
    }
    return switch (conclusion) {
      case SUCCESS -> Status.DEPLOYMENT_SUCCESS;
      case CANCELLED -> Status.CANCELLED;
      case FAILURE, STARTUP_FAILURE, TIMED_OUT, ACTION_REQUIRED -> Status.FAILED;
      case NEUTRAL, SKIPPED, STALE, UNKNOWN -> Status.UNKNOWN;
    };
  }

  public static Deployment.State mapHeliosStatusToDeploymentState(
      HeliosDeployment.Status heliosStatus) {
    return switch (heliosStatus) {
      case PENDING_DISPATCH -> Deployment.State.PENDING;
      case WAITING -> Deployment.State.PENDING;
      case QUEUED -> Deployment.State.PENDING;
      case IN_PROGRESS -> Deployment.State.IN_PROGRESS;
      case DEPLOYMENT_SUCCESS -> Deployment.State.SUCCESS;
      case FAILED -> Deployment.State.FAILURE;
      case CANCELLED -> Deployment.State.CANCELLED;
      case IO_ERROR, UNKNOWN -> Deployment.State.UNKNOWN;
    };
  }

  public static HeliosDeployment.Status mapDeploymentStateToHeliosStatus(Deployment.State state) {
    return switch (state) {
      case WAITING, PENDING -> Status.WAITING;
      case IN_PROGRESS -> Status.IN_PROGRESS;
      case SUCCESS -> Status.DEPLOYMENT_SUCCESS;
      case FAILURE, ERROR -> Status.FAILED;
      case CANCELLED -> Status.CANCELLED;
      case UNKNOWN, INACTIVE -> Status.UNKNOWN;
      case QUEUED -> Status.QUEUED;
    };
  }
}
