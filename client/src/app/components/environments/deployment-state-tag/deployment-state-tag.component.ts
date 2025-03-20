import { Component, inject, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { DeploymentTimingService } from '@app/core/services/deployment-timing.service';

type BaseDeploymentState = NonNullable<EnvironmentDeployment['state']>;
type ExtendedDeploymentState = BaseDeploymentState | 'NEVER_DEPLOYED' | 'REPLACED';

@Component({
  selector: 'app-deployment-state-tag',
  standalone: true,
  imports: [TagModule, IconsModule, TooltipModule],
  templateUrl: './deployment-state-tag.component.html',
})
export class DeploymentStateTagComponent {
  private timingService = inject(DeploymentTimingService);

  state = input.required<ExtendedDeploymentState | undefined>();
  verbose = input(false);
  showLatestDeployment = input(false);
  latestDeployment = input<{ releaseCandidateName?: string; ref?: string } | null | undefined>(null);
  deployment = input<EnvironmentDeployment | null | undefined>(null);

  rounded = this.timingService.timeAwareComputed(() => !this.verbose());
  internalState = this.timingService.timeAwareComputed(() => this.state() || 'UNKNOWN');

  severity = this.timingService.timeAwareComputed(() => {
    const severityMap: Record<ExtendedDeploymentState, Severity> = {
      SUCCESS: 'success',
      WAITING: 'warn',
      PENDING: 'warn',
      IN_PROGRESS: 'info',
      QUEUED: 'info',
      ERROR: 'danger',
      FAILURE: 'danger',
      INACTIVE: 'secondary',
      UNKNOWN: 'secondary',
      NEVER_DEPLOYED: 'secondary',
      REPLACED: 'contrast',
      REQUESTED: 'warn',
    };
    return severityMap[this.internalState()];
  });

  icon = this.timingService.timeAwareComputed(() => {
    const iconMap: Record<ExtendedDeploymentState, string> = {
      SUCCESS: 'check',
      WAITING: 'clock',
      PENDING: 'progress',
      IN_PROGRESS: 'progress',
      QUEUED: 'progress',
      ERROR: 'exclamation-circle',
      FAILURE: 'exclamation-mark',
      INACTIVE: 'time-duration-off',
      UNKNOWN: 'question-mark',
      NEVER_DEPLOYED: 'question-mark',
      REPLACED: 'repeat',
      REQUESTED: 'progress',
    };
    return iconMap[this.internalState()];
  });

  iconClass = this.timingService.timeAwareComputed(() => {
    const spinStates: ExtendedDeploymentState[] = ['REQUESTED', 'PENDING', 'IN_PROGRESS', 'QUEUED'];
    return `!size-5 ${spinStates.includes(this.internalState()) ? 'animate-spin' : ''}`;
  });

  value = this.timingService.timeAwareComputed(() => {
    const valueMap: Record<ExtendedDeploymentState, string> = {
      SUCCESS: 'success',
      WAITING: 'waiting',
      PENDING: 'pending',
      IN_PROGRESS: 'in progress',
      QUEUED: 'queued',
      ERROR: 'failed',
      FAILURE: 'failed',
      INACTIVE: 'inactive',
      UNKNOWN: 'unknown',
      NEVER_DEPLOYED: 'never deployed',
      REPLACED: 'replaced',
      REQUESTED: 'requested',
    };
    return valueMap[this.internalState()];
  });

  tooltip = this.timingService.timeAwareComputed(() => {
    const tooltipMap: Record<ExtendedDeploymentState, string> = {
      SUCCESS: 'Latest Deployment Successful',
      WAITING: 'Waiting for approval',
      PENDING: 'Deployment pending',
      IN_PROGRESS: 'Deployment in progress',
      QUEUED: 'Deployment queued',
      ERROR: 'Latest deployment failed',
      FAILURE: 'Latest deployment failed',
      INACTIVE: 'Deployment inactive',
      UNKNOWN: 'Deployment state unknown',
      NEVER_DEPLOYED: 'Never deployed',
      REPLACED: 'Deployment was replaced',
      REQUESTED: 'Deployment requested',
    };
    return tooltipMap[this.internalState()];
  });

  shouldShowRemainingTime = this.timingService.timeAwareComputed(() => {
    const inProgressStates: ExtendedDeploymentState[] = ['REQUESTED', 'PENDING', 'IN_PROGRESS', 'QUEUED'];
    return inProgressStates.includes(this.internalState()) && !!this.deployment();
  });

  getRemainingTime = this.timingService.timeAwareComputed(() => {
    const deployment = this.deployment();
    if (!deployment) return '';
    return this.timingService.getTotalRemainingTime(deployment);
  });
}

type Severity = 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast' | undefined;
