package de.tum.cit.aet.helios.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
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
}
