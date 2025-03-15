import { Injectable } from '@angular/core';
import { EnvironmentDeployment } from '@app/core/modules/openapi';

interface DeploymentTimingData {
  lastKnownState: string | undefined;
  stepStartTimes: Map<string, number>;
  terminatedAt?: number; // Time when the deployment reached a terminal state (SUCCESS, ERROR, FAILURE)
}

@Injectable({
  providedIn: 'root',
})
export class DeploymentTimingService {
  // Map deployment IDs to their timing data
  private deploymentTimings = new Map<string, DeploymentTimingData>();

  // Get timing data for a deployment, creating it if it doesn't exist
  getTimingData(deploymentId: string | number): DeploymentTimingData {
    const id = String(deploymentId);
    if (!this.deploymentTimings.has(id)) {
      this.deploymentTimings.set(id, {
        lastKnownState: undefined,
        stepStartTimes: new Map<string, number>(),
      });
    }
    return this.deploymentTimings.get(id)!;
  }

  // Update timing data when a deployment state changes
  updateDeploymentState(deployment: EnvironmentDeployment): void {
    if (!deployment.id || !deployment.state) return;

    const deploymentId = String(deployment.id);
    const timingData = this.getTimingData(deploymentId);
    const newState = deployment.state;

    // Only track state transitions
    if (newState !== timingData.lastKnownState) {
      const stepStartTime = Date.now();
      const previousState = timingData.lastKnownState;

      // Special case: If transitioning from REQUESTED to PENDING, don't reset the timer
      // since they're both part of the PRE-DEPLOYMENT step in the UI
      if (previousState === 'REQUESTED' && newState === 'PENDING') {
        // Keep the REQUESTED start time for PENDING
        const requestedStartTime = timingData.stepStartTimes.get('REQUESTED');
        if (requestedStartTime) {
          timingData.stepStartTimes.set(newState, requestedStartTime);
        } else {
          // Fallback if there's no REQUESTED start time for some reason
          timingData.stepStartTimes.set(newState, stepStartTime);
        }
      } else {
        // Normal case: set new start time for the current state
        timingData.stepStartTimes.set(newState, stepStartTime);
      }

      // If this is a terminal state, record the time
      if (['SUCCESS', 'ERROR', 'FAILURE'].includes(newState)) {
        timingData.terminatedAt = stepStartTime;
      }

      timingData.lastKnownState = newState;
    }
  }

  // Get the start time for a particular state of a deployment
  getStepStartTime(deploymentId: string | number, state: string): number | undefined {
    return this.getTimingData(deploymentId).stepStartTimes.get(state);
  }

  // Get the last known state for a deployment
  getLastKnownState(deploymentId: string | number): string | undefined {
    return this.getTimingData(deploymentId).lastKnownState;
  }

  // Clean up data for deployments that are in terminal states and older than a certain threshold
  cleanupOldData(maxAgeInMs: number = 24 * 60 * 60 * 1000): void {
    const now = Date.now();
    for (const [deploymentId, data] of this.deploymentTimings.entries()) {
      if (data.terminatedAt && now - data.terminatedAt > maxAgeInMs) {
        this.deploymentTimings.delete(deploymentId);
      }
    }
  }
}
