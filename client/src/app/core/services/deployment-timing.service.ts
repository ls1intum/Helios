import { DestroyRef, Injectable, Signal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { interval } from 'rxjs';

type DeploymentStep = 'PENDING' | 'IN_PROGRESS';

interface DeploymentTimingData {
  lastKnownState: string | undefined;
  stepStartTimes: Map<DeploymentStep, number>;
  terminatedAt?: number;
}

interface EstimatedTimes {
  REQUESTED: number;
  WAITING: number;
  PENDING: number;
  QUEUED: number;
  IN_PROGRESS: number;
}

@Injectable({
  providedIn: 'root',
})
export class DeploymentTimingService {
  private destroyRef = inject(DestroyRef);

  private deploymentTimings = new Map<string, DeploymentTimingData>();

  public readonly steps: DeploymentStep[] = ['PENDING', 'IN_PROGRESS'];

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

  getTimingData(deploymentId: string | number): DeploymentTimingData {
    const id = String(deploymentId);
    if (!this.deploymentTimings.has(id)) {
      this.deploymentTimings.set(id, {
        lastKnownState: undefined,
        stepStartTimes: new Map<DeploymentStep, number>(),
      });
    }
    return this.deploymentTimings.get(id)!;
  }

  updateDeploymentState(deployment: EnvironmentDeployment): void {
    if (!deployment.id || !deployment.state) {
      return;
    }

    const timingData = this.getTimingData(deployment.id);
    const newState = deployment.state;

    if (newState === timingData.lastKnownState) {
      return;
    }

    const stepStartTime = this._currentTime();
    const previousState = timingData.lastKnownState;
    const newStep = this.getStepForState(newState);
    const previousStep = previousState ? this.getStepForState(previousState) : undefined;

    if (newStep && this.shouldStartStepTimer(newState) && !this.getPersistedStepStartTime(deployment, newStep)) {
      const effectiveStartTime = previousStep === newStep && previousStep ? timingData.stepStartTimes.get(previousStep) || stepStartTime : stepStartTime;
      timingData.stepStartTimes.set(newStep, effectiveStartTime);
    }

    if (['SUCCESS', 'ERROR', 'FAILURE'].includes(newState)) {
      timingData.terminatedAt = stepStartTime;
    }

    timingData.lastKnownState = newState;
  }

  getStepStartTime(deploymentId: string | number, state: string): number | undefined {
    return this.getTimingData(deploymentId).stepStartTimes.get(state as DeploymentStep);
  }

  getLastKnownState(deploymentId: string | number): string | undefined {
    return this.getTimingData(deploymentId).lastKnownState;
  }

  public getEstimatedTimes(deployment: EnvironmentDeployment): EstimatedTimes {
    const prExists = deployment?.prName != null;
    const defaultPending = prExists ? 2 : 11;

    const pendingMin = deployment?.estimatedBuildDurationSeconds != null ? deployment.estimatedBuildDurationSeconds / 60 : defaultPending;
    const inProgressMin = deployment?.estimatedDeployDurationSeconds != null ? deployment.estimatedDeployDurationSeconds / 60 : 4;

    return {
      REQUESTED: pendingMin,
      WAITING: pendingMin,
      PENDING: pendingMin,
      QUEUED: pendingMin,
      IN_PROGRESS: inProgressMin,
    };
  }

  public isErrorState(deployment: EnvironmentDeployment): boolean {
    return ['ERROR', 'FAILURE'].includes(deployment?.state || '');
  }

  public isSuccessState(deployment: EnvironmentDeployment): boolean {
    return deployment?.state === 'SUCCESS';
  }

  public isUnknownState(deployment: EnvironmentDeployment): boolean {
    return ['UNKNOWN', 'INACTIVE'].includes(deployment?.state || '');
  }

  public getCurrentEffectiveStepIndex(deployment: EnvironmentDeployment): number {
    if (!deployment || !deployment.createdAt || !deployment.id) {
      return 0;
    }

    if (this.isErrorState(deployment)) {
      if (this.getEffectiveStepStartTime(deployment, 'IN_PROGRESS')) {
        return 1;
      }
      if (this.getEffectiveStepStartTime(deployment, 'PENDING')) {
        return 0;
      }

      const currentStep = this.getStepForState(deployment.state);
      return currentStep ? this.steps.indexOf(currentStep) : -1;
    }

    if (this.getEffectiveStepStartTime(deployment, 'IN_PROGRESS')) {
      return 1;
    }

    const currentStep = this.getStepForState(deployment.state);
    const index = currentStep ? this.steps.indexOf(currentStep) : -1;
    return index !== -1 ? index : 0;
  }

  public getRemainingTimeForCurrentStep(deployment: EnvironmentDeployment): number {
    if (!deployment?.state || !deployment.createdAt || !deployment.id) {
      return 0;
    }

    const currentIndex = this.getCurrentEffectiveStepIndex(deployment);
    if (currentIndex < 0) {
      return 0;
    }

    const currentStep = this.steps[currentIndex];
    const stepStartTime = this.getEffectiveStepStartTime(deployment, currentStep);
    const estimatedMs = this.getEstimatedTimes(deployment)[currentStep] * 60000;

    if (!stepStartTime) {
      return estimatedMs;
    }

    const currentTime = this.getProgressReferenceTime(deployment);
    const elapsedMs = Math.max(0, currentTime - stepStartTime);

    return Math.max(0, estimatedMs - elapsedMs);
  }

  public getStepDisplayName(step: string): string {
    const stepMap = {
      REQUESTED: 'PRE-DEPLOYMENT',
      PENDING: 'PRE-DEPLOYMENT',
      IN_PROGRESS: 'DEPLOYMENT',
    };
    return stepMap[step as keyof typeof stepMap] || step;
  }

  public getStepStatus(deployment: EnvironmentDeployment, index: number): string {
    if (!deployment) {
      return 'unknown';
    }

    const effectiveStep = this.getCurrentEffectiveStepIndex(deployment);

    if (this.isUnknownState(deployment)) {
      return 'unknown';
    }
    if (this.isSuccessState(deployment)) {
      return 'completed';
    }

    if (this.isErrorState(deployment)) {
      if (effectiveStep === -1) {
        return 'error';
      }
      if (index < effectiveStep) {
        return 'completed';
      }
      if (index === effectiveStep) {
        return 'error';
      }
      return 'unknown';
    }

    if (index < effectiveStep) {
      return 'completed';
    }
    if (index === effectiveStep) {
      return 'active';
    }
    return 'upcoming';
  }

  public getProgress(deployment: EnvironmentDeployment, index: number): number {
    if (!deployment || !deployment.createdAt || !deployment.id) {
      return 0;
    }
    if (deployment.state === 'SUCCESS') {
      return 100;
    }

    const effectiveStep = this.getCurrentEffectiveStepIndex(deployment);
    if (effectiveStep === -1) {
      return 0;
    }

    if (index < effectiveStep) {
      return 100;
    }
    if (index > effectiveStep) {
      return 0;
    }

    const stepKey = this.steps[index];
    const stepStartTime = this.getEffectiveStepStartTime(deployment, stepKey);
    if (!stepStartTime) {
      return 0;
    }

    const elapsedMs = Math.max(0, this.getProgressReferenceTime(deployment) - stepStartTime);
    const estimatedMs = this.getEstimatedTimes(deployment)[stepKey] * 60000;
    const ratio = estimatedMs > 0 ? Math.min(elapsedMs / estimatedMs, 1) : 1;
    return Math.max(0, Math.floor(ratio * 100));
  }

  public getStepTime(deployment: EnvironmentDeployment, index: number): string {
    if (!deployment?.state || !deployment.createdAt) {
      return '';
    }

    const currentIndex = this.getCurrentEffectiveStepIndex(deployment);

    if (this.isErrorState(deployment)) {
      if (currentIndex === -1) {
        return 'Failed';
      }
      if (index < currentIndex) {
        return 'Completed';
      }
      if (index === currentIndex) {
        return 'Failed';
      }
      return '';
    }

    if (this.isSuccessState(deployment)) {
      return 'Completed';
    }

    if (index < currentIndex) {
      return 'Completed';
    }

    const stepKey = this.steps[index];
    const estimatedMinutes = this.getEstimatedTimes(deployment)[stepKey];
    if (index > currentIndex || !this.getEffectiveStepStartTime(deployment, stepKey)) {
      return `~${estimatedMinutes}m 0s\nestimated`;
    }

    const remainingMs = this.getRemainingTimeForCurrentStep(deployment);
    const minutes = Math.floor(remainingMs / 60000);
    const seconds = Math.floor((remainingMs % 60000) / 1000);
    return `${minutes}m ${seconds}s\nremaining`;
  }

  public getTotalRemainingTime(deployment: EnvironmentDeployment): string {
    if (!deployment?.state || !deployment.createdAt) {
      return '';
    }
    if (this.isErrorState(deployment) || this.isSuccessState(deployment)) {
      return '';
    }

    const currentIndex = this.getCurrentEffectiveStepIndex(deployment);
    if (currentIndex < 0) {
      return '';
    }

    let totalRemainingMs = 0;

    for (let i = currentIndex; i < this.steps.length; i++) {
      const stepKey = this.steps[i];
      const estimatedMs = this.getEstimatedTimes(deployment)[stepKey] * 60000;

      if (i === currentIndex && this.getEffectiveStepStartTime(deployment, stepKey)) {
        totalRemainingMs += this.getRemainingTimeForCurrentStep(deployment);
      } else {
        totalRemainingMs += estimatedMs;
      }
    }

    const minutes = Math.floor(totalRemainingMs / 60000);
    const seconds = Math.floor((totalRemainingMs % 60000) / 1000);
    return totalRemainingMs > 0 ? `${minutes}m ${seconds}s` : '';
  }

  public getDeploymentDuration(deployment: EnvironmentDeployment): string {
    if (!deployment || !deployment.createdAt) {
      return '';
    }

    let endTime: number;
    if (['SUCCESS', 'ERROR', 'FAILURE'].includes(deployment.state || '')) {
      endTime = deployment.updatedAt ? new Date(deployment.updatedAt).getTime() : this._currentTime();
    } else {
      endTime = this._currentTime();
    }

    const startTime =
      this.getEffectiveStepStartTime(deployment, 'PENDING') || this.getEffectiveStepStartTime(deployment, 'IN_PROGRESS') || new Date(deployment.createdAt).getTime();
    const elapsedMs = endTime - startTime;
    if (elapsedMs < 0) {
      return '0m 0s';
    }

    const minutes = Math.floor(elapsedMs / 60000);
    const seconds = Math.floor((elapsedMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }

  cleanupOldData(maxAgeInMs: number = 24 * 60 * 60 * 1000): void {
    const now = this._currentTime();
    for (const [deploymentId, data] of this.deploymentTimings.entries()) {
      if (data.terminatedAt && now - data.terminatedAt > maxAgeInMs) {
        this.deploymentTimings.delete(deploymentId);
      }
    }
  }

  private getStepForState(state: string | undefined): DeploymentStep | undefined {
    switch (state) {
      case 'REQUESTED':
      case 'WAITING':
      case 'PENDING':
      case 'QUEUED':
        return 'PENDING';
      case 'IN_PROGRESS':
        return 'IN_PROGRESS';
      default:
        return undefined;
    }
  }

  private shouldStartStepTimer(state: string | undefined): boolean {
    return state === 'IN_PROGRESS';
  }

  private getProgressReferenceTime(deployment: EnvironmentDeployment): number {
    if (this.isErrorState(deployment) && deployment.updatedAt) {
      return new Date(deployment.updatedAt).getTime();
    }
    return this._currentTime();
  }

  private getPersistedStepStartTime(deployment: EnvironmentDeployment, step: DeploymentStep): number | undefined {
    return step === 'PENDING' ? this.parseTime(deployment.deployJobStartedAt) : this.parseTime(deployment.deploymentStartedAt);
  }

  private getEffectiveStepStartTime(deployment: EnvironmentDeployment, step: DeploymentStep): number | undefined {
    return this.getPersistedStepStartTime(deployment, step) ?? this.getTimingData(deployment.id).stepStartTimes.get(step);
  }

  private parseTime(value?: string): number | undefined {
    if (!value) {
      return undefined;
    }

    const parsed = new Date(value).getTime();
    return Number.isNaN(parsed) ? undefined : parsed;
  }
}
