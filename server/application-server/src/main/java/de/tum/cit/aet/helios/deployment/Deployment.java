package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.kohsuke.github.GHDeploymentState;

@Entity
@Table(name = "deployment")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Deployment extends BaseGitServiceEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id", nullable = false)
  private GitRepository repository;

  @ManyToOne
  @JoinColumn(name = "environment_id", nullable = false)
  private Environment environment;

  // PR associated with this deployment
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pull_request_id")
  private PullRequest pullRequest;

  @Column(name = "node_id")
  private String nodeId;

  private String url;

  // Enum to represent the current state of the deployment
  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = true)
  private State state = State.UNKNOWN;

  @Column(name = "statuses_url")
  private String statusesUrl;

  private String sha;

  private String ref;

  private String task;

  private String environmentName;

  @Column(name = "repository_url")
  private String repositoryUrl;

  // payload field is just empty JSON object

  public enum State {
    PENDING,
    SUCCESS,
    ERROR,
    FAILURE,
    IN_PROGRESS,
    QUEUED,
    INACTIVE,
    UNKNOWN; // Fallback for unmapped states
  }

  // Map GHDeploymentState to Deployment.State
  public static State mapToState(GHDeploymentState ghState) {
    return switch (ghState) {
      case PENDING -> State.PENDING;
      case SUCCESS -> State.SUCCESS;
      case ERROR -> State.ERROR;
      case FAILURE -> State.FAILURE;
      case IN_PROGRESS -> State.IN_PROGRESS;
      case QUEUED -> State.QUEUED;
      case INACTIVE -> State.INACTIVE;
      default -> State.UNKNOWN;
    };
  }
}
