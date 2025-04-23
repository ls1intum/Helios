package de.tum.cit.aet.helios.heliosdeployment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.user.User;
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
import org.kohsuke.github.GHWorkflowRun;

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

  // Enum to represent deployment status
  public enum Status {
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
    /** Deployment status is unknown. */
    UNKNOWN;
  }

  public void setStatus(Status status) {
    this.status = status;
    // Automatically update timestamp when status is updated
    this.statusUpdatedAt = OffsetDateTime.now();
  }

  public static HeliosDeployment.Status mapWorkflowRunStatus(
      GHWorkflowRun.Status workflowStatus, GHWorkflowRun.Conclusion workflowConclusion) {
    if (workflowStatus == GHWorkflowRun.Status.PENDING) {
      return Status.WAITING;
    } else if (workflowStatus == GHWorkflowRun.Status.QUEUED) {
      return Status.QUEUED;
    } else if (workflowStatus == GHWorkflowRun.Status.IN_PROGRESS) {
      return HeliosDeployment.Status.IN_PROGRESS;
    } else if (workflowStatus == GHWorkflowRun.Status.COMPLETED) {
      return switch (workflowConclusion) {
        case SUCCESS -> Status.DEPLOYMENT_SUCCESS;
        case FAILURE, STARTUP_FAILURE, TIMED_OUT -> Status.FAILED;
        default -> Status.UNKNOWN;
      };
    } else {
      return Status.UNKNOWN;
    }
  }

  public static Deployment.State mapHeliosStatusToDeploymentState(
      HeliosDeployment.Status heliosStatus) {
    return switch (heliosStatus) {
      case WAITING -> Deployment.State.PENDING;
      case QUEUED -> Deployment.State.PENDING;
      case IN_PROGRESS -> Deployment.State.IN_PROGRESS;
      case DEPLOYMENT_SUCCESS -> Deployment.State.SUCCESS;
      case FAILED -> Deployment.State.FAILURE;
      case IO_ERROR, UNKNOWN -> Deployment.State.UNKNOWN;
    };
  }

  public static HeliosDeployment.Status mapDeploymentStateToHeliosStatus(Deployment.State state) {
    return switch (state) {
      case WAITING, PENDING -> Status.WAITING;
      case IN_PROGRESS -> Status.IN_PROGRESS;
      case SUCCESS -> Status.DEPLOYMENT_SUCCESS;
      case FAILURE, ERROR -> Status.FAILED;
      case UNKNOWN, INACTIVE -> Status.UNKNOWN;
      case QUEUED -> Status.QUEUED;
    };
  }
}
