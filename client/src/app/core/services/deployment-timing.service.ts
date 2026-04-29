import { DestroyRef, Injectable, Signal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DeploymentTimerDto, DeploymentTimerStepDto, EnvironmentDeployment } from '@app/core/modules/openapi';
import { interval } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class DeploymentTimingService {
  private destroyRef = inject(DestroyRef);

  private _currentTime = signal<number>(Date.now());
  public readonly currentTime: Signal<number> = this._currentTime;

  public timeAwareComputed<T>(computeFn: () => T): Signal<T> {
    return computed(() => {
      this._currentTime();
      return computeFn();
    });
  }

  public createTimeAwareFunction<A extends unknown[], R>(fn: (...args: A) => R): (...args: A) => R {
    return (...args: A): R => {
      this._currentTime();
      return fn(...args);
    };
  }

  constructor() {
    interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this._currentTime.set(Date.now());
      });
  }

  public getTimer(deployment: EnvironmentDeployment | null | undefined): DeploymentTimerDto | undefined {
    return deployment?.timer ?? this.createFallbackTimer(deployment);
  }

  public getHeaderTimeLabel(timer: DeploymentTimerDto | undefined): string {
    if (!timer || timer.headerMode === 'NONE') {
      return '';
    }

    if (timer.headerMode === 'DURATION') {
      return this.formatDurationBetween(timer.headerStartedAt, timer.headerEndedAt);
    }

    const totalSeconds = timer.headerMode === 'REMAINING' ? this.getRemainingSeconds(timer) : this.getEstimatedSeconds(timer);
    if (totalSeconds <= 0) {
      return '';
    }

    const formatted = this.formatSeconds(totalSeconds);
    return timer.headerMode === 'REMAINING' ? `${formatted} remaining` : `~${formatted} estimated`;
  }

  public getStepTime(step: DeploymentTimerStepDto): string {
    switch (step.mode) {
      case 'COMPLETED':
        return 'Completed';
      case 'FAILED':
        return 'Failed';
      case 'ESTIMATED':
        return `~${this.formatSeconds(step.estimateSeconds ?? 0)}\nestimated`;
      case 'REMAINING':
        return `${this.formatSeconds(this.getStepRemainingSeconds(step))}\nremaining`;
      case 'NONE':
      default:
        return '';
    }
  }

  public getProgress(step: DeploymentTimerStepDto): number {
    if (step.status === 'completed') {
      return 100;
    }
    if (step.status !== 'active' || step.mode !== 'REMAINING' || !step.startedAt) {
      return 0;
    }

    const estimateMs = Math.max((step.estimateSeconds ?? 0) * 1000, 0);
    if (estimateMs <= 0) {
      return 0;
    }

    const elapsedMs = Math.max(0, this._currentTime() - this.parseTime(step.startedAt));
    return Math.min(Math.floor((elapsedMs / estimateMs) * 100), 99);
  }

  public getTotalRemainingTime(deployment: EnvironmentDeployment): string {
    const timer = this.getTimer(deployment);
    if (!timer || timer.headerMode === 'NONE' || timer.headerMode === 'DURATION') {
      return '';
    }
    const remainingSeconds = timer.headerMode === 'REMAINING' ? this.getRemainingSeconds(timer) : this.getEstimatedSeconds(timer);
    return remainingSeconds > 0 ? this.formatSeconds(remainingSeconds) : '';
  }

  private getRemainingSeconds(timer: DeploymentTimerDto): number {
    return timer.steps.reduce((total, step) => total + this.getStepDisplaySeconds(step), 0);
  }

  private getEstimatedSeconds(timer: DeploymentTimerDto): number {
    return timer.headerEstimateSeconds ?? timer.steps.reduce((total, step) => total + (step.estimateSeconds ?? 0), 0);
  }

  private getStepDisplaySeconds(step: DeploymentTimerStepDto): number {
    if (step.mode === 'REMAINING') {
      return this.getStepRemainingSeconds(step);
    }
    if (step.mode === 'ESTIMATED') {
      return step.estimateSeconds ?? 0;
    }
    return 0;
  }

  private getStepRemainingSeconds(step: DeploymentTimerStepDto): number {
    const estimateMs = Math.max((step.estimateSeconds ?? 0) * 1000, 0);
    if (!step.startedAt || estimateMs <= 0) {
      return Math.floor(estimateMs / 1000);
    }

    const elapsedMs = Math.max(0, this._currentTime() - this.parseTime(step.startedAt));
    return Math.floor(Math.max(0, estimateMs - elapsedMs) / 1000);
  }

  private formatDurationBetween(startedAt?: string, endedAt?: string): string {
    if (!startedAt) {
      return '';
    }

    const startTime = this.parseTime(startedAt);
    const endTime = endedAt ? this.parseTime(endedAt) : this._currentTime();
    const elapsedSeconds = Math.floor(Math.max(0, endTime - startTime) / 1000);
    return this.formatSeconds(elapsedSeconds);
  }

  private parseTime(value: string): number {
    let timeString = value;
    if (!timeString.endsWith('Z') && !timeString.match(/[+-]\d{2}:?\d{2}$/)) {
      timeString += 'Z';
    }
    const parsed = new Date(timeString).getTime();
    return Number.isNaN(parsed) ? this._currentTime() : parsed;
  }

  private formatSeconds(totalSeconds: number): string {
    const safeSeconds = Math.max(0, Math.floor(totalSeconds));
    const minutes = Math.floor(safeSeconds / 60);
    const seconds = safeSeconds % 60;
    return `${minutes}m ${seconds}s`;
  }

  private createFallbackTimer(deployment: EnvironmentDeployment | null | undefined): DeploymentTimerDto | undefined {
    if (!deployment) {
      return undefined;
    }

    const preDeployEstimate = deployment.estimatedPreDeployDurationSeconds ?? (deployment.prName != null ? 2 * 60 : 11 * 60);
    const deployEstimate = deployment.estimatedDeployDurationSeconds ?? 4 * 60;
    const failed = deployment.state === 'ERROR' || deployment.state === 'FAILURE';
    const success = deployment.state === 'SUCCESS';
    const unknown = deployment.state === 'UNKNOWN' || deployment.state === 'INACTIVE';

    if (unknown) {
      return {
        title: 'Deployment Status Unknown',
        headerMode: 'NONE',
        showQueuedMessage: false,
        steps: [this.fallbackStep('PRE_DEPLOYMENT', 'PRE-DEPLOYMENT', 'unknown', 'NONE'), this.fallbackStep('DEPLOYMENT', 'DEPLOYMENT', 'unknown', 'NONE')],
      };
    }

    if (success || failed) {
      const failedInDeployment = failed && !!deployment.deployJobStartedAt;
      return {
        title: success ? 'Deployment Completed' : 'Deployment Failed',
        headerMode: 'DURATION',
        headerStartedAt: deployment.workflowStartedAt ?? deployment.createdAt,
        headerEndedAt: deployment.updatedAt,
        showQueuedMessage: false,
        steps: [
          this.fallbackStep(
            'PRE_DEPLOYMENT',
            'PRE-DEPLOYMENT',
            success || failedInDeployment ? 'completed' : 'error',
            success || failedInDeployment ? 'COMPLETED' : 'FAILED',
            deployment.workflowStartedAt,
            deployment.deployJobStartedAt ?? deployment.updatedAt,
            preDeployEstimate
          ),
          this.fallbackStep(
            'DEPLOYMENT',
            'DEPLOYMENT',
            success ? 'completed' : failedInDeployment ? 'error' : 'unknown',
            success ? 'COMPLETED' : failedInDeployment ? 'FAILED' : 'NONE',
            deployment.deployJobStartedAt,
            deployment.updatedAt,
            deployEstimate
          ),
        ],
      };
    }

    if (deployment.deployJobStartedAt) {
      return {
        title: 'Deployment in Progress',
        headerMode: 'REMAINING',
        headerStartedAt: deployment.deployJobStartedAt,
        headerEstimateSeconds: deployEstimate,
        showQueuedMessage: false,
        steps: [
          this.fallbackStep('PRE_DEPLOYMENT', 'PRE-DEPLOYMENT', 'completed', 'COMPLETED', deployment.workflowStartedAt, deployment.deployJobStartedAt, preDeployEstimate),
          this.fallbackStep('DEPLOYMENT', 'DEPLOYMENT', 'active', 'REMAINING', deployment.deployJobStartedAt, undefined, deployEstimate),
        ],
      };
    }

    const workflowStarted = !!deployment.workflowStartedAt;
    const queued = deployment.state === 'QUEUED';
    return {
      title: queued ? 'Deployment Queued' : workflowStarted || deployment.state === 'IN_PROGRESS' ? 'Deployment in Progress' : 'Deployment Requested',
      headerMode: workflowStarted ? 'REMAINING' : 'ESTIMATED',
      headerStartedAt: deployment.workflowStartedAt,
      headerEstimateSeconds: preDeployEstimate + deployEstimate,
      showQueuedMessage: queued,
      steps: [
        this.fallbackStep('PRE_DEPLOYMENT', 'PRE-DEPLOYMENT', 'active', workflowStarted ? 'REMAINING' : 'ESTIMATED', deployment.workflowStartedAt, undefined, preDeployEstimate),
        this.fallbackStep('DEPLOYMENT', 'DEPLOYMENT', 'upcoming', 'ESTIMATED', undefined, undefined, deployEstimate),
      ],
    };
  }

  private fallbackStep(
    key: DeploymentTimerStepDto['key'],
    label: string,
    status: DeploymentTimerStepDto['status'],
    mode: DeploymentTimerStepDto['mode'],
    startedAt?: string,
    endedAt?: string,
    estimateSeconds?: number
  ): DeploymentTimerStepDto {
    return { key, label, status, mode, startedAt, endedAt, estimateSeconds };
  }
}
