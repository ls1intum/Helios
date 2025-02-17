import { Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import { EnvironmentDeployment } from '@app/core/modules/openapi';

type BaseDeploymentState = NonNullable<EnvironmentDeployment['state']>;
type ExtendedDeploymentState = BaseDeploymentState | 'NEVER_DEPLOYED' | 'REPLACED';

@Component({
  selector: 'app-deployment-state-tag',
  standalone: true,
  imports: [TagModule, IconsModule, TooltipModule],
  templateUrl: './deployment-state-tag.component.html',
})
export class DeploymentStateTagComponent {
  state = input.required<ExtendedDeploymentState | undefined>();
  verbose = input(false);
  showLatestDeployment = input(false);
  latestDeployment = input<{ releaseCandidateName?: string; ref?: string } | null | undefined>(null);

  rounded = computed(() => !this.verbose());
  internalState = computed(() => this.state() || 'UNKNOWN');
  severity = computed(() => {
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

  icon = computed(() => {
    const iconMap: Record<ExtendedDeploymentState, string> = {
      SUCCESS: 'check',
      WAITING: 'progress',
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

  iconClass = computed(() => {
    const spinStates: ExtendedDeploymentState[] = ['WAITING', 'PENDING', 'IN_PROGRESS', 'QUEUED'];
    return `!size-5 ${spinStates.includes(this.internalState()) ? 'animate-spin' : ''}`;
  });

  value = computed(() => {
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

  tooltip = computed(() => {
    const tooltipMap: Record<ExtendedDeploymentState, string> = {
      SUCCESS: 'Latest Deployment Successful',
      WAITING: 'Waiting deployment',
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
}

type Severity = 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast' | undefined;
