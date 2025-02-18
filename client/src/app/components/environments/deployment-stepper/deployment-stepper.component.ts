import { Component, Input, OnInit, OnDestroy, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { ProgressBarModule } from 'primeng/progressbar';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { TooltipModule } from 'primeng/tooltip';

interface EstimatedTimes {
  REQUESTED: number;
  PENDING: number;
  IN_PROGRESS: number;
}

@Component({
  selector: 'app-deployment-stepper',
  imports: [CommonModule, IconsModule, ProgressBarModule, TooltipModule],
  templateUrl: './deployment-stepper.component.html',
})
export class DeploymentStepperComponent implements OnInit, OnDestroy {
  // Create a private signal to hold the deployment value.
  private _deployment = signal<EnvironmentDeployment | undefined>(undefined);

  @Input()
  set deployment(value: EnvironmentDeployment | undefined) {
    this._deployment.set(value);
  }
  get deployment(): EnvironmentDeployment | undefined {
    return this._deployment();
  }

  // Define the four steps. (Note: estimatedTimes are defined only for the first three.)
  steps: ('REQUESTED' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS')[] = ['REQUESTED', 'PENDING', 'IN_PROGRESS', 'SUCCESS'];

  // Mapping of state keys to their display descriptions.
  stepDescriptions: {
    [key in 'QUEUED' | 'REQUESTED' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'ERROR' | 'FAILURE' | 'UNKNOWN' | 'INACTIVE']: string;
  } = {
    QUEUED: 'Deployment Queued',
    REQUESTED: 'Request sent to Github',
    PENDING: 'Preparing deployment',
    IN_PROGRESS: 'Deployment in progress',
    SUCCESS: 'Deployment successful',
    ERROR: 'Deployment error',
    FAILURE: 'Deployment failed',
    UNKNOWN: 'State unknown',
    INACTIVE: 'Inactive deployment',
  };

  // Estimated times in minutes for each non-terminal step.
  // Define estimatedTimes as a computed signal that depends on the reactive deployment signal.
  estimatedTimes = computed<EstimatedTimes>(() => {
    const deployment = this._deployment();
    return {
      REQUESTED: 1,
      PENDING: deployment?.prName != null ? 1 : 10, // if deployment started from PR then no build time it's 1 minute, if there is a build via branch then it's 4-7 minute in avg
      IN_PROGRESS: 4, // deployment state takes around 1-3 mintues
    };
  });

  // A timer updated every second to drive the dynamic progress.
  time: number = Date.now();
  intervalId: number | undefined;

  ngOnInit(): void {
    this.intervalId = window.setInterval(() => {
      this.time = Date.now();
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  /**
   * Determines the effective step index.
   * If the deployment state is terminal (SUCCESS, ERROR, FAILURE), use that state.
   * Otherwise, use the virtual (elapsed-time based) step.
   */
  get currentEffectiveStepIndex(): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    const index = this.steps.indexOf(this.deployment.state as 'REQUESTED' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS');
    if (this.deployment?.type === 'HELIOS' && this.deployment?.state === 'IN_PROGRESS') {
      return 1;
    }
    return index !== -1 ? index : 3;
  }

  /**
   * Checks if the deployment is in an error state.
   */
  isErrorState(): boolean {
    return ['ERROR', 'FAILURE'].includes(this.deployment?.state || '');
  }

  /**
   * Checks if the deployment is in an unknown state.
   */
  isUnknownState(): boolean {
    return ['UNKNOWN', 'INACTIVE'].includes(this.deployment?.state || '');
  }

  /**
   * Returns a status string for a given step index.
   * - "completed" if the step index is less than the current effective step.
   * - "active" if it matches the current effective step (or "error" if in error state).
   * - "upcoming" otherwise.
   */
  getStepStatus(index: number): string {
    const effectiveStep = this.currentEffectiveStepIndex;
    if (this.isUnknownState()) return 'unknown';
    if (index < effectiveStep) return 'completed';
    if (index === effectiveStep) return this.isErrorState() ? 'error' : this.steps[index] === 'SUCCESS' ? 'completed' : 'active';
    return 'upcoming';
  }

  /**
   * Computes overall progress in a piecewise manner.
   * Each segment (REQUESTED, PENDING, IN_PROGRESS) is allotted 25% of the bar.
   * For the current segment, progress is calculated as a ratio of its elapsed time;
   * if a segment is already completed (i.e. virtualStepIndex > segment index), that segment is full.
   * When the virtual step reaches 3, the progress returns 100%.
   */
  get dynamicProgress(): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    if (['UNKNOWN', 'INACTIVE'].includes(this.deployment.state || '')) {
      return 0;
    } else if (['SUCCESS', 'ERROR', 'FAILURE'].includes(this.deployment.state || '')) {
      return 100;
    }
    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMs = this.time - start > 0 ? this.time - start : 0;
    if (this.currentEffectiveStepIndex === 3) return 100;

    const segStart = this.currentEffectiveStepIndex * 33;
    const estimatedMs = this.estimatedTimes()[this.steps[this.currentEffectiveStepIndex] as keyof EstimatedTimes] * 60000;
    const elapsedForSegmentMs = elapsedMs > 0 ? elapsedMs : 0;
    let ratio = elapsedForSegmentMs / estimatedMs;
    if (ratio > 1) {
      ratio = 1;
    }
    const progress = segStart + ratio * 33;
    return Math.floor(progress);
  }

  /**
   * Returns the estimated time remaining (in minutes) for a given step.
   * It subtracts the elapsed time from the cumulative estimated time up to that step.
   */
  getTimeEstimate(index: number): string {
    if (!this.deployment || !this.deployment.createdAt) return '';
    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMinutes = (this.time - start) / 60000;
    let cumulative = 0;
    for (let i = 0; i < 3; i++) {
      const est = this.estimatedTimes()[this.steps[i] as keyof EstimatedTimes];
      if (this.getStepStatus(i) !== 'completed') {
        cumulative += est;
      }
      if (i === index) {
        if (this.getStepStatus(i) === 'completed') {
          return '';
        }
        const remaining = cumulative - elapsedMinutes;
        const minutes = Math.floor(remaining);
        const seconds = Math.floor((remaining - minutes) * 60);
        return remaining > 0 ? `${minutes}m ${seconds}s` : '';
      }
    }
    return '';
  }

  /**
   * Returns a display name for a given step.
   */
  getStepDisplayName(step: string): string {
    return (
      {
        REQUESTED: 'REQUESTED',
        PENDING: 'PRE-DEPLOYMENT',
        IN_PROGRESS: 'DEPLOYING',
        SUCCESS: 'SUCCESS',
      }[step] || step
    );
  }

  /**
   * Computes the deployment duration by subtracting the deployment creation time
   * from the finish time (or current time if finishedAt isn’t available).
   */
  getDeploymentDuration(): string {
    if (!this.deployment || !this.deployment.createdAt) return '';

    let endTime: number;
    if (['SUCCESS', 'ERROR', 'FAILURE'].includes(this.deployment.state || '')) {
      endTime = this.deployment.updatedAt ? new Date(this.deployment.updatedAt).getTime() : this.time;
    } else {
      endTime = this.time;
    }

    const startTime = new Date(this.deployment.createdAt).getTime();
    const elapsedMs = endTime - startTime;
    if (elapsedMs < 0) return '0m 0s';

    const minutes = Math.floor(elapsedMs / 60000);
    const seconds = Math.floor((elapsedMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }
}
