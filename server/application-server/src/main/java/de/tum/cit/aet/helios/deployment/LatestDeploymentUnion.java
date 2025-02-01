package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.user.User;
import java.time.OffsetDateTime;

/** Represents a union of either a real Deployment, a HeliosDeployment, or none. */
public class LatestDeploymentUnion {
  private final Deployment realDeployment;
  private final HeliosDeployment heliosDeployment;

  private LatestDeploymentUnion(Deployment realDeployment, HeliosDeployment heliosDeployment) {
    this.realDeployment = realDeployment;
    this.heliosDeployment = heliosDeployment;
  }

  public static LatestDeploymentUnion realDeployment(Deployment dep) {
    return new LatestDeploymentUnion(dep, null);
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

  public String getUrl() {
    if (isRealDeployment()) {
      return realDeployment.getUrl();
    } else {
      return null;
    }
  }

  public Deployment.State getState() {
    if (isRealDeployment()) {
      return realDeployment.getState();
    } else if (isHeliosDeployment()) {
      return mapHeliosStatusToDeploymentState(heliosDeployment.getStatus());
    } else {
      return null;
    }
  }

  private static Deployment.State mapHeliosStatusToDeploymentState(
      HeliosDeployment.Status heliosStatus) {
    return switch (heliosStatus) {
      case WAITING, QUEUED -> Deployment.State.PENDING;
      case IN_PROGRESS -> Deployment.State.IN_PROGRESS;
      case DEPLOYMENT_SUCCESS -> Deployment.State.SUCCESS;
      case FAILED -> Deployment.State.FAILURE;
      case IO_ERROR, UNKNOWN -> Deployment.State.UNKNOWN;
    };
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

  public boolean isNone() {
    return !isRealDeployment() && !isHeliosDeployment();
  }
}
