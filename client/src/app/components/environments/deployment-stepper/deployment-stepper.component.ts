import { Component, computed, inject, Input, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { ProgressBarModule } from 'primeng/progressbar';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { TooltipModule } from 'primeng/tooltip';
import { DeploymentTimingService } from '@app/core/services/deployment-timing.service';

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
  private _deployment = signal<EnvironmentDeployment | undefined>(undefined);
  private currentTime = signal<number>(Date.now());
  private timingService = inject(DeploymentTimingService);

  @Input()
  set deployment(value: EnvironmentDeployment | undefined) {
    // Update the deployment signal
    this._deployment.set(value);

    // Update timing service with new deployment state
    if (value?.id && value?.state) {
      this.timingService.updateDeploymentState(value);
    }
  }
  get deployment(): EnvironmentDeployment | undefined {
    return this._deployment();
  }

  steps: ('PENDING' | 'IN_PROGRESS')[] = ['PENDING', 'IN_PROGRESS'];

  estimatedTimes = computed<EstimatedTimes>(() => {
    const deployment = this._deployment();
    const prExists = deployment?.prName != null;
    return {
      REQUESTED: prExists ? 2 : 11, // REQUESTED is not shown but still part of logic
      PENDING: prExists ? 2 : 11,
      IN_PROGRESS: 4,
    };
  });

  intervalId: number | undefined;

  ngOnInit(): void {
    // Set up interval to update current time every second
    this.intervalId = window.setInterval(() => {
      this.currentTime.set(Date.now());
    }, 1000);

    // Run cleanup for old timing data
    this.timingService.cleanupOldData();
  }

  ngOnDestroy(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  get currentEffectiveStepIndex(): number {
    if (!this.deployment || !this.deployment.createdAt || !this.deployment.id) return 0;

    // If in error state, find the last known valid state
    if (this.isErrorState()) {
      // Look through the stepStartTimes to find the last step that was started
      const deploymentId = this.deployment.id;

      for (let i = this.steps.length - 1; i >= 0; i--) {
        const step = this.steps[i];
        if (this.timingService.getStepStartTime(deploymentId, step)) {
          return i;
        }
      }
      return -1; // Special value to indicate no valid step found in error state
    }

    const currentState = this.deployment.state;

    // Special handling for REQUESTED state (map to PENDING in UI)
    if (currentState === 'REQUESTED') {
      return 0; // PENDING is now the first step in the UI
    }

    const index = this.steps.indexOf(currentState as 'PENDING' | 'IN_PROGRESS');
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
    if (!this.deployment || !this.deployment.createdAt || !this.deployment.id) return 0;
    if (this.deployment.state === 'SUCCESS') return 100;

    const stepKey = this.steps[index] as keyof EstimatedTimes;
    const currentState = this.deployment.state;
    const effectiveStep = this.currentEffectiveStepIndex;
    const deploymentId = this.deployment.id;

    // If in error state with no valid step, show empty progress bars
    if (this.isErrorState() && effectiveStep === -1) return 0;

    // Skip the remaining checks if effectiveStep is -1 to avoid incorrect comparisons
    if (effectiveStep === -1) return 0;

    if (index < effectiveStep) return 100;
    if (index > effectiveStep || (this.isErrorState() && index > effectiveStep)) return 0;

    // For current step
    const stepStartTime = this.timingService.getStepStartTime(deploymentId, currentState!) || new Date(this.deployment.createdAt).getTime();

    // If in error state, use the updatedAt time to calculate the progress at failure
    const currentTime = this.isErrorState() && this.deployment.updatedAt ? new Date(this.deployment.updatedAt).getTime() : this.currentTime();

    const elapsedMs = Math.max(0, currentTime - stepStartTime); // Ensure elapsed time is never negative
    const estimatedMs = this.estimatedTimes()[stepKey] * 60000;

    const ratio = Math.min(elapsedMs / estimatedMs, 1);
    return Math.max(0, Math.floor(ratio * 100)); // Ensure we never return a negative percentage
  }

  getRemainingTimeForCurrentStep(): number {
    if (!this.deployment?.state || !this.deployment.createdAt || !this.deployment.id) return 0;

    const currentState = this.deployment.state;
    const deploymentId = this.deployment.id;

    // Use the stored step start time or fall back to the current time
    const stepStartTime = this.timingService.getStepStartTime(deploymentId, currentState) || Date.now();
    const elapsedMs = this.currentTime() - stepStartTime;
    const estimatedMs = (this.estimatedTimes()[currentState as keyof EstimatedTimes] || 0) * 60000;

    return Math.max(0, estimatedMs - elapsedMs);
  }

  getTotalRemainingTime(): string {
    if (!this.deployment?.state || !this.deployment.createdAt) return '';
    if (this.isErrorState() || this.isSuccessState()) return '';

    const currentIndex = this.currentEffectiveStepIndex;
    let totalRemainingMs = this.getRemainingTimeForCurrentStep();

    // Add estimated time for upcoming steps
    for (let i = currentIndex + 1; i < this.steps.length; i++) {
      const stepKey = this.steps[i] as keyof EstimatedTimes;
      totalRemainingMs += this.estimatedTimes()[stepKey] * 60000;
    }

    const minutes = Math.floor(totalRemainingMs / 60000);
    const seconds = Math.floor((totalRemainingMs % 60000) / 1000);
    return totalRemainingMs > 0 ? `${minutes}m ${seconds}s` : '';
  }

  getStepTime(index: number): string {
    if (!this.deployment?.state || !this.deployment.createdAt) return '';

    const currentIndex = this.currentEffectiveStepIndex;

    // Handle error state
    if (this.isErrorState()) {
      if (currentIndex === -1) return 'Failed'; // Show all steps as failed when no valid step found
      if (index < currentIndex) return 'Completed';
      if (index === currentIndex) return 'Failed';
      return '';
    }

    // Handle success state
    if (this.isSuccessState()) {
      return 'Completed';
    }

    // Handle normal flow
    if (index < currentIndex) {
      return 'Completed';
    }

    if (index > currentIndex) {
      // For upcoming steps, show estimated time
      const stepKey = this.steps[index] as keyof EstimatedTimes;
      const estimatedMinutes = this.estimatedTimes()[stepKey];
      return `~${estimatedMinutes}m 0s\nestimated`;
    }

    // For current step
    const remainingMs = this.getRemainingTimeForCurrentStep();
    const minutes = Math.floor(remainingMs / 60000);
    const seconds = Math.floor((remainingMs % 60000) / 1000);
    return remainingMs > 0 ? `${minutes}m ${seconds}s\nremaining` : `0m 0s\nremaining`;
  }

  getStepDisplayName(step: string): string {
    return (
      {
        REQUESTED: 'PRE-DEPLOYMENT', // Map REQUESTED to PRE-DEPLOYMENT (though not shown in UI)
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
