package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LatestDeploymentUnionTest {

  @Test
  void fromHeliosStatusPreservesQueuedState() {
    assertEquals(
        LatestDeploymentUnion.State.REQUESTED,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.WAITING));
    assertEquals(
        LatestDeploymentUnion.State.QUEUED,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.QUEUED));
    assertEquals(
        LatestDeploymentUnion.State.PENDING,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.IN_PROGRESS));
  }

  @Test
  void fromHeliosStatusKeepsTerminalStatesUnchanged() {
    assertEquals(
        LatestDeploymentUnion.State.SUCCESS,
        LatestDeploymentUnion.State.fromHeliosStatus(
            HeliosDeployment.Status.DEPLOYMENT_SUCCESS));
    assertEquals(
        LatestDeploymentUnion.State.FAILURE,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.FAILED));
    assertEquals(
        LatestDeploymentUnion.State.CANCELLED,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.CANCELLED));
    assertEquals(
        LatestDeploymentUnion.State.UNKNOWN,
        LatestDeploymentUnion.State.fromHeliosStatus(HeliosDeployment.Status.UNKNOWN));
  }

  @Test
  void latestPrefersRealDeploymentWhenItIsAtLeastAsRecentAsHeliosDeployment() {
    OffsetDateTime createdAt = OffsetDateTime.now();
    Deployment deployment = deployment(1L, createdAt);
    HeliosDeployment heliosDeployment = heliosDeployment(2L, createdAt);

    LatestDeploymentUnion result =
        LatestDeploymentUnion.latest(Optional.of(heliosDeployment), Optional.of(deployment));

    assertEquals(LatestDeploymentUnion.DeploymentType.GITHUB, result.getType());
    assertEquals(1L, result.getId());
  }

  @Test
  void latestPrefersHeliosDeploymentWhenItIsNewerThanRealDeployment() {
    OffsetDateTime createdAt = OffsetDateTime.now();
    Deployment deployment = deployment(1L, createdAt.minusMinutes(1));
    HeliosDeployment heliosDeployment = heliosDeployment(2L, createdAt);

    LatestDeploymentUnion result =
        LatestDeploymentUnion.latest(Optional.of(heliosDeployment), Optional.of(deployment));

    assertEquals(LatestDeploymentUnion.DeploymentType.HELIOS, result.getType());
    assertEquals(2L, result.getId());
  }

  private Deployment deployment(Long id, OffsetDateTime createdAt) {
    Deployment deployment = new Deployment();
    deployment.setId(id);
    deployment.setEnvironment(new Environment());
    deployment.setCreatedAt(createdAt);
    return deployment;
  }

  private HeliosDeployment heliosDeployment(Long id, OffsetDateTime createdAt) {
    HeliosDeployment deployment = new HeliosDeployment();
    deployment.setId(id);
    deployment.setEnvironment(new Environment());
    deployment.setCreatedAt(createdAt);
    return deployment;
  }
}
