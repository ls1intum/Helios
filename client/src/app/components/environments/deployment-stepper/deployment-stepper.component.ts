import { Component, computed, Input, OnDestroy, OnInit, signal } from '@angular/core';
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
  private _deployment = signal<EnvironmentDeployment | undefined>(undefined);
  private lastKnownState = signal<string | undefined>(undefined);
  private stepStartTimes = signal<Map<string, number>>(new Map());
  private currentTime = signal<number>(Date.now());

  @Input()
  set deployment(value: EnvironmentDeployment | undefined) {
    // Track step transitions
    if (value?.state && value.state !== this.lastKnownState()) {
      // Use the deployment's updatedAt timestamp for the step start time
      const stepStartTime = value.updatedAt ? new Date(value.updatedAt).getTime() : Date.now();
      this.stepStartTimes.update(times => {
        times.set(value.state!, stepStartTime);
        return times;
      });
      this.lastKnownState.set(value.state);
    }
    this._deployment.set(value);
  }
  get deployment(): EnvironmentDeployment | undefined {
    return this._deployment();
  }

  steps: ('REQUESTED' | 'PENDING' | 'IN_PROGRESS')[] = ['REQUESTED', 'PENDING', 'IN_PROGRESS'];

  estimatedTimes = computed<EstimatedTimes>(() => {
    const deployment = this._deployment();
    return {
      REQUESTED: 1,
      PENDING: deployment?.prName != null ? 1 : 10,
      IN_PROGRESS: 4,
    };
  });

  intervalId: number | undefined;

  ngOnInit(): void {
    this.intervalId = window.setInterval(() => {
      this.currentTime.set(Date.now());
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  get currentEffectiveStepIndex(): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    const index = this.steps.indexOf(this.deployment.state as 'REQUESTED' | 'PENDING' | 'IN_PROGRESS');
    return index !== -1 ? index : 2;
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
    if (index < effectiveStep) return 'completed';
    if (index === effectiveStep) return this.isErrorState() ? 'error' : 'active';
    return 'upcoming';
  }

  getProgress(index: number): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    if (this.isErrorState() && this.currentEffectiveStepIndex === index) return 100;
    if (this.deployment.state === 'SUCCESS') return 100;

    const stepKey = this.steps[index] as keyof EstimatedTimes;
    const currentState = this.deployment.state;

    if (index < this.currentEffectiveStepIndex) return 100;
    if (index > this.currentEffectiveStepIndex || (this.isErrorState() && index > this.currentEffectiveStepIndex)) return 0;

    // For current step
    const stepStartTime = this.stepStartTimes().get(currentState!) || new Date(this.deployment.createdAt).getTime();
    const elapsedMs = this.currentTime() - stepStartTime;
    const estimatedMs = this.estimatedTimes()[stepKey] * 60000;

    const ratio = Math.min(elapsedMs / estimatedMs, 1);
    return Math.floor(ratio * 100);
  }

  getRemainingTimeForCurrentStep(): number {
    if (!this.deployment?.state || !this.deployment.createdAt) return 0;

    const currentState = this.deployment.state;
    // Use the stored step start time or fall back to the deployment's creation time
    const stepStartTime = this.stepStartTimes().get(currentState) || new Date(this.deployment.createdAt).getTime();
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
    if (this.isErrorState() && index > this.currentEffectiveStepIndex) return '';

    const currentIndex = this.currentEffectiveStepIndex;

    if (index < currentIndex || (this.isSuccessState() && index === this.steps.length - 1)) {
      return 'Completed';
    }

    if (this.isErrorState() || this.isSuccessState()) {
      return '';
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
        REQUESTED: 'REQUESTED',
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
