import { DestroyRef, Injectable, Signal, computed, inject, signal } from '@angular/core';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

interface DeploymentTimingData {
  lastKnownState: string | undefined;
  stepStartTimes: Map<string, number>;
  terminatedAt?: number; // Time when the deployment reached a terminal state (SUCCESS, ERROR, FAILURE)
}

interface EstimatedTimes {
  REQUESTED: number;
  PENDING: number;
  IN_PROGRESS: number;
}

@Injectable({
  providedIn: 'root',
})
export class DeploymentTimingService {
  private destroyRef = inject(DestroyRef);

  // Map deployment IDs to their timing data
  private deploymentTimings = new Map<string, DeploymentTimingData>();

  // Define deployment steps
  public readonly steps: ('PENDING' | 'IN_PROGRESS')[] = ['PENDING', 'IN_PROGRESS'];

  // Centralized current time that updates every second
  private _currentTime = signal<number>(Date.now());
  public readonly currentTime: Signal<number> = this._currentTime;

  /**
   * Creates a time-aware computed signal that automatically updates with time changes
   * @param computeFn Function that computes a value, will be re-evaluated on time changes
   * @returns A signal that updates both when dependencies change and when time changes
   */
  public timeAwareComputed<T>(computeFn: () => T): Signal<T> {
    return computed(() => {
      // Create dependency on the current time
      this._currentTime();
      // Return the computed value
      return computeFn();
    });
  }

  /**
   * Creates a time-aware function that will return fresh values as time changes
   * This is useful for template methods that need to accept parameters
   * @param fn The function to make time-aware
   * @returns A function that will return fresh values as time changes
   */
  public createTimeAwareFunction<A extends unknown[], R>(fn: (...args: A) => R): (...args: A) => R {
    // Create a closure over the current time to ensure freshness
    return (...args: A): R => {
      // Reading the current time creates a dependency in templates that use this function
      this._currentTime();
      // Call the original function with all arguments
      return fn(...args);
    };
  }

  constructor() {
    // Update the current time every second
    interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this._currentTime.set(Date.now());
      });
  }

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
      const stepStartTime = this._currentTime();
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

  // Get the estimated times for each step based on deployment properties
  public getEstimatedTimes(deployment: EnvironmentDeployment): EstimatedTimes {
    const prExists = deployment?.prName != null;
    return {
      REQUESTED: prExists ? 2 : 11, // REQUESTED is not shown but still part of logic
      PENDING: prExists ? 2 : 11,
      IN_PROGRESS: 4,
    };
  }

  // Determine if a deployment is in an error state
  public isErrorState(deployment: EnvironmentDeployment): boolean {
    return ['ERROR', 'FAILURE'].includes(deployment?.state || '');
  }

  // Determine if a deployment is in a success state
  public isSuccessState(deployment: EnvironmentDeployment): boolean {
    return deployment?.state === 'SUCCESS';
  }

  // Determine if a deployment is in an unknown state
  public isUnknownState(deployment: EnvironmentDeployment): boolean {
    return ['UNKNOWN', 'INACTIVE'].includes(deployment?.state || '');
  }

  // Get the current effective step index for a deployment
  public getCurrentEffectiveStepIndex(deployment: EnvironmentDeployment): number {
    if (!deployment || !deployment.createdAt || !deployment.id) return 0;

    // If in error state, find the last known valid state
    if (this.isErrorState(deployment)) {
      // Look through the stepStartTimes to find the last step that was started
      const deploymentId = deployment.id;

      for (let i = this.steps.length - 1; i >= 0; i--) {
        const step = this.steps[i];
        if (this.getStepStartTime(deploymentId, step)) {
          return i;
        }
      }
      return -1; // Special value to indicate no valid step found in error state
    }

    const currentState = deployment.state;

    // Special handling for REQUESTED state (map to PENDING in UI)
    if (currentState === 'REQUESTED') {
      return 0; // PENDING is now the first step in the UI
    }

    const index = this.steps.indexOf(currentState as 'PENDING' | 'IN_PROGRESS');
    return index !== -1 ? index : 0;
  }

  // Get remaining time for the current step of a deployment
  public getRemainingTimeForCurrentStep(deployment: EnvironmentDeployment): number {
    if (!deployment?.state || !deployment.createdAt || !deployment.id) return 0;

    const currentState = deployment.state;
    const deploymentId = deployment.id;

    // Use the stored step start time or fall back to the current time
    const stepStartTime = this.getStepStartTime(deploymentId, currentState) || this._currentTime();
    const elapsedMs = this._currentTime() - stepStartTime;
    const estimatedMs = (this.getEstimatedTimes(deployment)[currentState as keyof EstimatedTimes] || 0) * 60000;

    return Math.max(0, estimatedMs - elapsedMs);
  }

  // Get step display name
  public getStepDisplayName(step: string): string {
    const stepMap = {
      REQUESTED: 'PRE-DEPLOYMENT', // Map REQUESTED to PRE-DEPLOYMENT (though not shown in UI)
      PENDING: 'PRE-DEPLOYMENT',
      IN_PROGRESS: 'DEPLOYMENT',
    };
    return stepMap[step as keyof typeof stepMap] || step;
  }

  // Get step status
  public getStepStatus(deployment: EnvironmentDeployment, index: number): string {
    if (!deployment) return 'unknown';

    const effectiveStep = this.getCurrentEffectiveStepIndex(deployment);

    if (this.isUnknownState(deployment)) return 'unknown';
    if (this.isSuccessState(deployment)) return 'completed';

    // If in error state, handle each step appropriately
    if (this.isErrorState(deployment)) {
      if (effectiveStep === -1) return 'error'; // Show all steps as error when no valid step found
      if (index < effectiveStep) return 'completed';
      if (index === effectiveStep) return 'error';
      return 'unknown';
    }

    // Normal flow (no error)
    if (index < effectiveStep) return 'completed';
    if (index === effectiveStep) return 'active';
    return 'upcoming';
  }

  // Get progress percentage for a step
  public getProgress(deployment: EnvironmentDeployment, index: number): number {
    if (!deployment || !deployment.createdAt || !deployment.id) return 0;
    if (deployment.state === 'SUCCESS') return 100;

    const stepKey = this.steps[index] as keyof EstimatedTimes;
    const currentState = deployment.state;
    const effectiveStep = this.getCurrentEffectiveStepIndex(deployment);
    const deploymentId = deployment.id;

    // If in error state with no valid step, show empty progress bars
    if (this.isErrorState(deployment) && effectiveStep === -1) return 0;

    // Skip the remaining checks if effectiveStep is -1 to avoid incorrect comparisons
    if (effectiveStep === -1) return 0;

    if (index < effectiveStep) return 100;
    if (index > effectiveStep || (this.isErrorState(deployment) && index > effectiveStep)) return 0;

    // For current step
    const stepStartTime = this.getStepStartTime(deploymentId, currentState!) || new Date(deployment.createdAt).getTime();

    // If in error state, use the updatedAt time to calculate the progress at failure
    let currentTime: number;
    if (this.isErrorState(deployment) && deployment.updatedAt) {
      currentTime = new Date(deployment.updatedAt).getTime();
    } else {
      currentTime = this._currentTime();
    }

    const elapsedMs = Math.max(0, currentTime - stepStartTime); // Ensure elapsed time is never negative
    const estimatedMs = this.getEstimatedTimes(deployment)[stepKey] * 60000;

    const ratio = Math.min(elapsedMs / estimatedMs, 1);
    return Math.max(0, Math.floor(ratio * 100)); // Ensure we never return a negative percentage
  }

  // Get time display for a step
  public getStepTime(deployment: EnvironmentDeployment, index: number): string {
    if (!deployment?.state || !deployment.createdAt) return '';

    const currentIndex = this.getCurrentEffectiveStepIndex(deployment);

    // Handle error state
    if (this.isErrorState(deployment)) {
      if (currentIndex === -1) return 'Failed'; // Show all steps as failed when no valid step found
      if (index < currentIndex) return 'Completed';
      if (index === currentIndex) return 'Failed';
      return '';
    }

    // Handle success state
    if (this.isSuccessState(deployment)) {
      return 'Completed';
    }

    // Handle normal flow
    if (index < currentIndex) {
      return 'Completed';
    }

    if (index > currentIndex) {
      // For upcoming steps, show estimated time
      const stepKey = this.steps[index] as keyof EstimatedTimes;
      const estimatedMinutes = this.getEstimatedTimes(deployment)[stepKey];
      return `~${estimatedMinutes}m 0s\nestimated`;
    }

    // For current step
    const remainingMs = this.getRemainingTimeForCurrentStep(deployment);
    const minutes = Math.floor(remainingMs / 60000);
    const seconds = Math.floor((remainingMs % 60000) / 1000);
    return remainingMs > 0 ? `${minutes}m ${seconds}s\nremaining` : `0m 0s\nremaining`;
  }

  // Get the total remaining time for a deployment as a string (e.g. "5m 30s")
  getTotalRemainingTime(deployment: EnvironmentDeployment): string {
    if (!deployment?.state || !deployment.createdAt) return '';
    if (this.isErrorState(deployment) || this.isSuccessState(deployment)) return '';

    const currentIndex = this.getCurrentEffectiveStepIndex(deployment);
    let totalRemainingMs = this.getRemainingTimeForCurrentStep(deployment);

    // Add estimated time for upcoming steps
    for (let i = currentIndex + 1; i < this.steps.length; i++) {
      const stepKey = this.steps[i] as keyof EstimatedTimes;
      totalRemainingMs += this.getEstimatedTimes(deployment)[stepKey] * 60000;
    }

    const minutes = Math.floor(totalRemainingMs / 60000);
    const seconds = Math.floor((totalRemainingMs % 60000) / 1000);
    return totalRemainingMs > 0 ? `${minutes}m ${seconds}s` : '';
  }

  // Calculate the duration of a deployment
  public getDeploymentDuration(deployment: EnvironmentDeployment): string {
    if (!deployment || !deployment.createdAt) return '';
    let endTime: number;
    if (['SUCCESS', 'ERROR', 'FAILURE'].includes(deployment.state || '')) {
      endTime = deployment.updatedAt ? new Date(deployment.updatedAt).getTime() : this._currentTime();
    } else {
      endTime = this._currentTime();
    }
    const startTime = new Date(deployment.createdAt).getTime();
    const elapsedMs = endTime - startTime;
    if (elapsedMs < 0) return '0m 0s';
    const minutes = Math.floor(elapsedMs / 60000);
    const seconds = Math.floor((elapsedMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }

  // Clean up data for deployments that are in terminal states and older than a certain threshold
  cleanupOldData(maxAgeInMs: number = 24 * 60 * 60 * 1000): void {
    const now = this._currentTime();
    for (const [deploymentId, data] of this.deploymentTimings.entries()) {
      if (data.terminatedAt && now - data.terminatedAt > maxAgeInMs) {
        this.deploymentTimings.delete(deploymentId);
      }
    }
  }
}
