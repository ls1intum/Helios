import { Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';

type DeploymentState = 'SUCCESS' | 'WAITING' | 'PENDING' | 'IN_PROGRESS' | 'QUEUED' | 'ERROR' | 'FAILURE' | 'INACTIVE' | 'UNKNOWN' | 'NEVER_DEPLOYED' | 'REPLACED';

@Component({
  selector: 'app-deployment-state-tag',
  standalone: true,
  imports: [TagModule, IconsModule, TooltipModule],
  templateUrl: './deployment-state-tag.component.html',
})
export class DeploymentStateTagComponent {
  state = input.required<DeploymentState | undefined>();
  verbose = input(false);
  showLatestDeployment = input(false);
  latestDeployment = input<{ releaseCandidateName?: string; ref?: string } | null | undefined>(null);

  rounded = computed(() => !this.verbose());
  internalState = computed(() => this.state() || 'UNKNOWN');

  getSeverity() {
    const severityMap: Record<DeploymentState, Severity> = {
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
    };
    return severityMap[this.internalState()];
  }

  getIcon() {
    const iconMap: Record<DeploymentState, string> = {
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
      REPLACED: 'progress',
    };
    return iconMap[this.internalState()];
  }

  getIconClass() {
    const spinStates: DeploymentState[] = ['WAITING', 'PENDING', 'IN_PROGRESS', 'QUEUED', 'REPLACED'];
    return `!size-5 ${spinStates.includes(this.internalState()) ? 'animate-spin' : ''}`;
  }

  getValue() {
    const valueMap: Record<DeploymentState, string> = {
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
    };
    return valueMap[this.internalState()];
  }

  getTooltip() {
    const tooltipMap: Record<DeploymentState, string> = {
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
    };
    return tooltipMap[this.internalState()];
  }
}

type Severity = 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast' | undefined;
