import { Component, computed, Input, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { ProgressBarModule } from 'primeng/progressbar';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { TooltipModule } from 'primeng/tooltip';

interface EstimatedTimes {
  PENDING: number;
  IN_PROGRESS: number;
}

@Component({
  selector: 'app-deployment-stepper',
  imports: [CommonModule, IconsModule, ProgressBarModule, TooltipModule],
  templateUrl: './deployment-stepper.component.html',
})
export class DeploymentStepperComponent implements OnInit, OnDestroy {
  private _deployment = signal<EnvironmentDeployment | undefined>(undefined);
  private lastKnownState = signal<string | undefined>(undefined);
  private stepStartTimes = signal<Map<string, number>>(new Map());
  private currentTime = signal<number>(Date.now());

  @Input()
  set deployment(value: EnvironmentDeployment | undefined) {
    if (!value) {
      this._deployment.set(undefined);
      return;
    }

    this._deployment.set(value);

    // If the state has changed, record the start time for the new state
    if (value.state && value.state !== this.lastKnownState()) {
      const currentState = value.state;

      // Only set the start time if it hasn't been set before
      if (!this.stepStartTimes().has(currentState)) {
        this.stepStartTimes.update(times => {
          // When transitioning to a new state, set the start time to now
          // to ensure we start with the full estimated time for this step
          const startTime = Date.now();
          times.set(currentState, startTime);
          return times;
        });
      }

      this.lastKnownState.set(currentState);
    }
  }
  get deployment(): EnvironmentDeployment | undefined {
    return this._deployment();
  }

  steps: ('PENDING' | 'IN_PROGRESS')[] = ['PENDING', 'IN_PROGRESS'];

  estimatedTimes = computed<EstimatedTimes>(() => {
    return {
      PENDING: this.deployment?.prName != null ? 2 : 11, // Combined time for REQUESTED + PENDING
      IN_PROGRESS: 4,
    };
  });

  intervalId: number | undefined;

  ngOnInit(): void {
    // Set up interval to update current time every second
    this.intervalId = window.setInterval(() => {
      this.currentTime.set(Date.now());
    }, 1000);

    // If a deployment is already set, initialize the step start time for the current state
    // This handles the case where the component is initialized with a deployment already in progress
    if (this.deployment?.state && !this.stepStartTimes().has(this.deployment.state)) {
      const currentState = this.deployment.state;
      this.stepStartTimes.update(times => {
        // For initial state, use the current time to ensure full estimated time
        const startTime = Date.now();
        times.set(currentState, startTime);
        return times;
      });
      this.lastKnownState.set(currentState);
    }
  }

  ngOnDestroy(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  get currentEffectiveStepIndex(): number {
    if (!this.deployment || !this.deployment.createdAt) return -1;

    // If in error state, find the last known valid state
    if (this.isErrorState()) {
      // Look through the stepStartTimes to find the last step that was started
      const startedSteps = Array.from(this.stepStartTimes().keys());
      for (let i = this.steps.length - 1; i >= 0; i--) {
        if (startedSteps.includes(this.steps[i])) {
          return i;
        }
      }
      return -1; // Special value to indicate no valid step found in error state
    }

    const index = this.steps.indexOf(this.deployment.state as 'PENDING' | 'IN_PROGRESS');
    return index !== -1 ? index : 0;
  }

  isErrorState(): boolean {
    return ['ERROR', 'FAILURE'].includes(this.deployment?.state || '');
  }

  isUnknownState(): boolean {
    return ['UNKNOWN', 'INACTIVE'].includes(this.deployment?.state || '');
  }

  isSuccessState(): boolean {
    return this.deployment?.state === 'SUCCESS';
  }

  getStepStatus(index: number): string {
    const effectiveStep = this.currentEffectiveStepIndex;

    if (this.isUnknownState()) return 'unknown';
    if (this.isSuccessState()) return 'completed';

    // If in error state, handle each step appropriately
    if (this.isErrorState()) {
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

  getProgress(index: number): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    if (this.deployment.state === 'SUCCESS') return 100;

    const stepKey = this.steps[index] as keyof EstimatedTimes;
    const currentState = this.deployment.state;
    const effectiveStep = this.currentEffectiveStepIndex;

    // If in error state with no valid step, show empty progress bars
    if (this.isErrorState() && effectiveStep === -1) return 0;

    if (index < this.currentEffectiveStepIndex) return 100;
    if (index > this.currentEffectiveStepIndex || (this.isErrorState() && index > this.currentEffectiveStepIndex)) return 0;

    // For current step
    const stepStartTime = this.stepStartTimes().get(currentState!) || new Date(this.deployment.createdAt).getTime();

    // If in error state, use the updatedAt time to calculate the progress at failure
    const currentTime = this.isErrorState() && this.deployment.updatedAt ? new Date(this.deployment.updatedAt).getTime() : this.currentTime();

    const elapsedMs = currentTime - stepStartTime;
    const estimatedMs = this.estimatedTimes()[stepKey] * 60000;

    const ratio = Math.min(elapsedMs / estimatedMs, 1);
    return Math.floor(ratio * 100);
  }

  getRemainingTimeForCurrentStep(): number {
    if (!this.deployment?.state) {
      return 0;
    }

    const currentIndex = this.currentEffectiveStepIndex;
    if (currentIndex < 0 || currentIndex >= this.steps.length) {
      return 0;
    }

    const currentStep = this.steps[currentIndex];
    const stepStartTime = this.stepStartTimes().get(this.deployment.state) || Date.now();

    const estimatedMinutes = this.estimatedTimes()[currentStep] || 0;
    const estimatedMs = estimatedMinutes * 60 * 1000;
    const elapsedMs = this.currentTime() - stepStartTime;
    const remainingMs = Math.max(0, estimatedMs - elapsedMs);

    // Return in seconds
    return Math.floor(remainingMs / 1000);
  }

  getTotalRemainingTime(): string {
    if (!this.deployment) {
      return '0m 0s';
    }

    const currentIndex = this.currentEffectiveStepIndex;
    const estimatedTimes = this.estimatedTimes();

    // If we're in an error or success state, don't show remaining time
    if (this.isErrorState() || this.isSuccessState()) {
      return '0m 0s';
    }

    // Calculate remaining time for current step
    let totalRemainingSeconds = 0;

    // If we have a current step
    if (currentIndex >= 0 && currentIndex < this.steps.length) {
      // Add remaining time for current step
      totalRemainingSeconds += this.getRemainingTimeForCurrentStep();

      // Add time for future steps
      for (let i = currentIndex + 1; i < this.steps.length; i++) {
        const stepName = this.steps[i];
        totalRemainingSeconds += (estimatedTimes[stepName] || 0) * 60;
      }
    } else {
      // If no step is active yet, show total time for all steps
      for (let i = 0; i < this.steps.length; i++) {
        const stepName = this.steps[i];
        totalRemainingSeconds += (estimatedTimes[stepName] || 0) * 60;
      }
    }

    // Format the remaining time
    const minutes = Math.floor(totalRemainingSeconds / 60);
    const seconds = Math.floor(totalRemainingSeconds % 60);
    return `${minutes}m ${seconds}s`;
  }

  getStepTime(index: number): string {
    if (!this.deployment) {
      return '';
    }

    const currentEffectiveIndex = this.currentEffectiveStepIndex;
    const stepName = this.steps[index];
    const estimatedTimeMinutes = this.estimatedTimes()[stepName] || 0;

    // For completed steps
    if (index < currentEffectiveIndex) {
      return 'Completed';
    }

    // For the current step
    if (index === currentEffectiveIndex) {
      // Get the remaining time for the current step
      if (this.deployment.state === stepName) {
        const remainingSeconds = this.getRemainingTimeForCurrentStep();
        const minutes = Math.floor(remainingSeconds / 60);
        const seconds = Math.floor(remainingSeconds % 60);
        return `${minutes}m ${seconds}s`;
      }
    }

    // For future steps
    return `${estimatedTimeMinutes}m 0s`;
  }

  getStepDisplayName(step: string): string {
    return (
      {
        PENDING: 'PRE-DEPLOYMENT',
        IN_PROGRESS: 'DEPLOYMENT',
      }[step] || step
    );
  }

  getDeploymentDuration(): string {
    if (!this.deployment || !this.deployment.createdAt) return '';
    let endTime: number;
    if (['SUCCESS', 'ERROR', 'FAILURE'].includes(this.deployment.state || '')) {
      endTime = this.deployment.updatedAt ? new Date(this.deployment.updatedAt).getTime() : this.currentTime();
    } else {
      endTime = this.currentTime();
    }
    const startTime = new Date(this.deployment.createdAt).getTime();
    const elapsedMs = endTime - startTime;
    if (elapsedMs < 0) return '0m 0s';
    const minutes = Math.floor(elapsedMs / 60000);
    const seconds = Math.floor((elapsedMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }
}
