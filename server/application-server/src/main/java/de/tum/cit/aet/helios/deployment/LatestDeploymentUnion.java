package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.user.User;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;

/** Represents a union of either a real Deployment, a HeliosDeployment, or none. */
@RequiredArgsConstructor
public class LatestDeploymentUnion {
  private final Deployment realDeployment;
  private final HeliosDeployment heliosDeployment;

  public static LatestDeploymentUnion realDeployment(
      Deployment dep, OffsetDateTime heliosDeploymentCreatedAt) {
    dep.setCreatedAt(heliosDeploymentCreatedAt);
    return new LatestDeploymentUnion(dep, null);
  }

  public static LatestDeploymentUnion realDeployment(Deployment dep) {
    return new LatestDeploymentUnion(dep, null);
  }

  /**
   * Create a LatestDeploymentUnion with a real Deployment and a HeliosDeployment as a fallback.
   *
   * @param dep The github deployment
   * @param helios The helios deployment as a fallback for some fields
   * @return The LatestDeploymentUnion
   */
  public static LatestDeploymentUnion realDeployment(Deployment dep, HeliosDeployment helios) {
    dep.setCreatedAt(helios.getCreatedAt());
    return new LatestDeploymentUnion(dep, helios);
  }

  public static LatestDeploymentUnion heliosDeployment(HeliosDeployment helios) {
    return new LatestDeploymentUnion(null, helios);
  }

  public static LatestDeploymentUnion none() {
    return new LatestDeploymentUnion(null, null);
  }

  public boolean isRealDeployment() {
    return realDeployment != null;
  }

  public boolean isHeliosDeployment() {
    return heliosDeployment != null && realDeployment == null;
  }

  public boolean hasHeliosDeployment() {
    return heliosDeployment != null;
  }

  public Deployment getRealDeployment() {
    return realDeployment;
  }

  public HeliosDeployment getHeliosDeployment() {
    return heliosDeployment;
  }

  public Long getId() {
    if (isRealDeployment()) {
      return realDeployment.getId();
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getId();
    } else {
      return null;
    }
  }

  public String getWorkflowRunHtmlUrl() {
    if (hasHeliosDeployment()) {
      return this.getHeliosDeployment().getWorkflowRunHtmlUrl();
    }
    return null;
  }

  public RepositoryInfoDto getRepository() {
    if (isRealDeployment()) {
      return RepositoryInfoDto.fromRepository(realDeployment.getRepository());
    } else if (isHeliosDeployment()) {
      return RepositoryInfoDto.fromRepository(heliosDeployment.getEnvironment().getRepository());
    } else {
      return null;
    }
  }

  public String getUrl() {
    if (isRealDeployment()) {
      return realDeployment.getUrl();
    } else {
      return null;
    }
  }

  public Environment getEnvironment() {
    if (isRealDeployment()) {
      return realDeployment.getEnvironment();
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getEnvironment();
    } else {
      return null;
    }
  }

  public State getState() {
    if (isRealDeployment()) {
      return State.fromDeploymentState(realDeployment.getState());
    } else if (isHeliosDeployment()) {
      return State.fromHeliosStatus(heliosDeployment.getStatus());
    } else {
      return null;
    }
  }

  public static enum State {
    REQUESTED,

    // Deployment.State
    PENDING,
    WAITING,
    SUCCESS,
    ERROR,
    FAILURE,
    IN_PROGRESS,
    QUEUED,
    INACTIVE,
    UNKNOWN;

    public static State fromDeploymentState(Deployment.State state) {
      return switch (state) {
        case PENDING -> PENDING;
        case WAITING -> WAITING;
        case SUCCESS -> SUCCESS;
        case ERROR -> ERROR;
        case FAILURE -> FAILURE;
        case IN_PROGRESS -> IN_PROGRESS;
        case QUEUED -> QUEUED;
        case INACTIVE -> INACTIVE;
        case UNKNOWN -> UNKNOWN;
        default -> throw new IllegalArgumentException("Invalid state: " + state);
      };
    }

    public static State fromHeliosStatus(HeliosDeployment.Status status) {
      return switch (status) {
        case WAITING -> REQUESTED;
        case QUEUED, IN_PROGRESS -> PENDING;
        case DEPLOYMENT_SUCCESS -> SUCCESS;
        case FAILED -> FAILURE;
        case IO_ERROR, UNKNOWN -> UNKNOWN;
      };
    }
  }

  public String getStatusesUrl() {
    if (isRealDeployment()) {
      return realDeployment.getStatusesUrl();
    } else {
      return null;
    }
  }

  public String getSha() {
    if (isRealDeployment()) {
      return realDeployment.getSha();
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getSha();
    } else {
      return null;
    }
  }

  public String getRef() {
    if (isRealDeployment()) {
      return realDeployment.getRef();
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getBranchName();
    } else {
      return null;
    }
  }

  public String getTask() {
    if (isRealDeployment()) {
      return realDeployment.getTask();
    } else if (isHeliosDeployment()) {
      return "helios-deploy";
    } else {
      return null;
    }
  }

  public User getCreator() {
    if (isRealDeployment()) {
      return realDeployment.getCreator();
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getCreator();
    } else {
      return null;
    }
  }

  public OffsetDateTime getCreatedAt() {
    if (isRealDeployment()) {
      return realDeployment.getCreatedAt();
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getCreatedAt();
    } else {
      return null;
    }
  }

  public OffsetDateTime getUpdatedAt() {
    if (isRealDeployment()) {
      return realDeployment.getUpdatedAt();
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getUpdatedAt();
    } else {
      return null;
    }
  }

  public String getPullRequestName() {
    if (isRealDeployment()) {
      return realDeployment.getPullRequest() != null
          ? realDeployment.getPullRequest().getTitle()
          : null;
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getPullRequest() != null
          ? heliosDeployment.getPullRequest().getTitle()
          : null;
    } else {
      return null;
    }
  }

  public Integer getPullRequestNumber() {
    if (isRealDeployment()) {
      return realDeployment.getPullRequest() != null
          ? realDeployment.getPullRequest().getNumber()
          : null;
    } else if (isHeliosDeployment()) {
      return heliosDeployment.getPullRequest() != null
          ? heliosDeployment.getPullRequest().getNumber()
          : null;
    } else {
      return null;
    }
  }

  public boolean isNone() {
    return !isRealDeployment() && !isHeliosDeployment();
  }

  public DeploymentType getType() {
    if (isRealDeployment()) {
      return DeploymentType.GITHUB;
    } else if (isHeliosDeployment()) {
      return DeploymentType.HELIOS;
    } else {
      return null;
    }
  }

  public static enum DeploymentType {
    GITHUB,
    HELIOS
  }
}
