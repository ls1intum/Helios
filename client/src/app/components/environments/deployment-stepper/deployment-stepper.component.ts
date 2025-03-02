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

  @Input()
  set deployment(value: EnvironmentDeployment | undefined) {
    this._deployment.set(value);
  }
  get deployment(): EnvironmentDeployment | undefined {
    return this._deployment();
  }

  steps: ('REQUESTED' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS')[] = ['REQUESTED', 'PENDING', 'IN_PROGRESS', 'SUCCESS'];

  estimatedTimes = computed<EstimatedTimes>(() => {
    const deployment = this._deployment();
    return {
      REQUESTED: 1,
      PENDING: deployment?.prName != null ? 1 : 10,
      IN_PROGRESS: 4,
    };
  });

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

  get currentEffectiveStepIndex(): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    const index = this.steps.indexOf(this.deployment.state as 'REQUESTED' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS');
    return index !== -1 ? index : 3;
  }

  isErrorState(): boolean {
    return ['ERROR', 'FAILURE'].includes(this.deployment?.state || '');
  }

  isUnknownState(): boolean {
    return ['UNKNOWN', 'INACTIVE'].includes(this.deployment?.state || '');
  }

  getStepStatus(index: number): string {
    const effectiveStep = this.currentEffectiveStepIndex;
    if (this.isUnknownState()) return 'unknown';
    if (index < effectiveStep) return 'completed';
    if (index === effectiveStep) return this.isErrorState() ? 'error' : this.steps[index] === 'SUCCESS' ? 'completed' : 'active';
    return 'upcoming';
  }

  getProgress(index: number): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    if (this.isErrorState() || this.deployment.state === 'SUCCESS') return 100;

    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMs = this.time - start > 0 ? this.time - start : 0;
    const stepKey = this.steps[index] as keyof EstimatedTimes;
    const estimatedMs = this.estimatedTimes()[stepKey] * 60000;

    if (index < this.currentEffectiveStepIndex) return 100;
    if (index > this.currentEffectiveStepIndex) return 0;

    const ratio = Math.min(elapsedMs / estimatedMs, 1);
    return Math.floor(ratio * 100);
  }

  getStepTime(index: number): string {
    if (!this.deployment || !this.deployment.createdAt) return '';
    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMs = this.time - start > 0 ? this.time - start : 0;
    const elapsedMinutes = elapsedMs / 60000;

    const stepKey = this.steps[index] as keyof EstimatedTimes;
    const estimatedTime = this.estimatedTimes()[stepKey] || 0;

    if (this.getStepStatus(index) === 'completed') {
      return 'Completed';
    }

    if (this.getStepStatus(index) === 'upcoming') {
      return `${Math.floor(estimatedTime)}m 0s remaining`;
    }

    const remaining = estimatedTime - elapsedMinutes;
    const minutes = Math.floor(remaining);
    const seconds = Math.floor((remaining - minutes) * 60);
    return remaining > 0 ? `${minutes}m ${seconds}s remaining` : '0m 0s remaining';
  }

  getTotalTimeEstimate(): string {
    if (!this.deployment || !this.deployment.createdAt) return '';
    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMs = this.time - start > 0 ? this.time - start : 0;
    const elapsedMinutes = elapsedMs / 60000;

    const totalEstimated = this.estimatedTimes().REQUESTED + this.estimatedTimes().PENDING + this.estimatedTimes().IN_PROGRESS;
    const remaining = totalEstimated - elapsedMinutes;
    const minutes = Math.floor(remaining);
    const seconds = Math.floor((remaining - minutes) * 60);
    return remaining > 0 ? `${minutes}m ${seconds}s` : '0m 0s';
  }

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
