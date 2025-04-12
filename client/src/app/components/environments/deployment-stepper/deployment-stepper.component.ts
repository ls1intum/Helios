import { Component, inject, OnInit, signal, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { ProgressBarModule } from 'primeng/progressbar';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { TooltipModule } from 'primeng/tooltip';
import { DeploymentTimingService } from '@app/core/services/deployment-timing.service';
import { TagModule } from 'primeng/tag';
import { BadgeModule } from 'primeng/badge';

interface EstimatedTimes {
  REQUESTED: number;
  PENDING: number;
  IN_PROGRESS: number;
}

@Component({
  selector: 'app-deployment-stepper',
  imports: [CommonModule, IconsModule, BadgeModule, TagModule, ProgressBarModule, TooltipModule],
  templateUrl: './deployment-stepper.component.html',
})
export class DeploymentStepperComponent implements OnInit {
  private _deployment = signal<EnvironmentDeployment | undefined>(undefined);
  private timingService = inject(DeploymentTimingService);

  readonly deployment = input<EnvironmentDeployment | undefined, EnvironmentDeployment | undefined>(undefined, {
    transform: (value: EnvironmentDeployment | undefined) => {
      this._deployment.set(value);

      if (value?.id && value?.state) {
        this.timingService.updateDeploymentState(value);
      }

      return value;
    },
  });

  steps = this.timingService.steps;

  estimatedTimes = this.timingService.timeAwareComputed<EstimatedTimes>(() => {
    const deployment = this._deployment();
    if (!deployment) return { REQUESTED: 0, PENDING: 0, IN_PROGRESS: 0 };
    return this.timingService.getEstimatedTimes(deployment);
  });

  ngOnInit(): void {
    // Run cleanup for old timing data
    this.timingService.cleanupOldData();
  }

  get currentEffectiveStepIndex(): number {
    const deployment = this._deployment();
    if (!deployment) return 0;
    return this.timingService.getCurrentEffectiveStepIndex(deployment);
  }

  isErrorState(): boolean {
    const deployment = this._deployment();
    if (!deployment) return false;
    return this.timingService.isErrorState(deployment);
  }

  isUnknownState(): boolean {
    const deployment = this._deployment();
    if (!deployment) return false;
    return this.timingService.isUnknownState(deployment);
  }

  isSuccessState(): boolean {
    const deployment = this._deployment();
    if (!deployment) return false;
    return this.timingService.isSuccessState(deployment);
  }

  // Methods that need to be called from the template with arguments
  getStepStatus = this.timingService.createTimeAwareFunction((index: number): string => {
    const deployment = this._deployment();
    if (!deployment) return 'unknown';
    return this.timingService.getStepStatus(deployment, index);
  });

  getSeverityFromStepStatus = (index: number) => {
    switch (this.getStepStatus(index)) {
      case 'completed':
        return 'success';
      case 'active':
        return 'info';
      case 'error':
        return 'danger';
      case 'upcoming':
      case 'unknown':
      case 'inactive':
        return 'secondary';
      default:
        return 'secondary';
    }
  };

  getProgress = this.timingService.createTimeAwareFunction((index: number): number => {
    const deployment = this._deployment();
    if (!deployment) return 0;
    return this.timingService.getProgress(deployment, index);
  });

  getRemainingTimeForCurrentStep = this.timingService.timeAwareComputed(() => {
    const deployment = this._deployment();
    if (!deployment) return 0;
    return this.timingService.getRemainingTimeForCurrentStep(deployment);
  });

  getTotalRemainingTime = this.timingService.timeAwareComputed(() => {
    const deployment = this._deployment();
    if (!deployment) return '';
    return this.timingService.getTotalRemainingTime(deployment);
  });

  getStepTime = this.timingService.createTimeAwareFunction((index: number): string => {
    const deployment = this._deployment();
    if (!deployment) return '';
    return this.timingService.getStepTime(deployment, index);
  });

  getStepDisplayName = this.timingService.createTimeAwareFunction((step: string): string => {
    return this.timingService.getStepDisplayName(step);
  });

  getDeploymentDuration = this.timingService.timeAwareComputed(() => {
    const deployment = this._deployment();
    if (!deployment) return '';
    return this.timingService.getDeploymentDuration(deployment);
  });
}
