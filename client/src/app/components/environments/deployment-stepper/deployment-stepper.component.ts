import { Component, computed, inject, input, signal } from '@angular/core';
import { DeploymentTimerStepDto, EnvironmentDeployment } from '@app/core/modules/openapi';
import { DeploymentTimingService } from '@app/core/services/deployment-timing.service';
import { IconCheck, IconClock, IconProgress, IconX } from 'angular-tabler-icons/icons';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { MessageModule } from 'primeng/message';
import { ProgressBarModule } from 'primeng/progressbar';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-deployment-stepper',
  imports: [TablerIconComponent, TagModule, MessageModule, ProgressBarModule, TooltipModule],
  providers: [
    provideTablerIcons({
      IconClock,
      IconCheck,
      IconProgress,
      IconX,
    }),
  ],
  templateUrl: './deployment-stepper.component.html',
})
export class DeploymentStepperComponent {
  private _deployment = signal<EnvironmentDeployment | undefined>(undefined);
  private timingService = inject(DeploymentTimingService);

  readonly deployment = input<EnvironmentDeployment | undefined, EnvironmentDeployment | undefined>(undefined, {
    transform: (value: EnvironmentDeployment | undefined) => {
      this._deployment.set(value);
      return value;
    },
  });

  timer = computed(() => this.timingService.getTimer(this._deployment()));
  steps = computed(() => this.timer()?.steps ?? []);

  getProgressTitle(): string {
    return this.timer()?.title ?? '';
  }

  shouldShowQueuedMessage(): boolean {
    return this.timer()?.showQueuedMessage ?? false;
  }

  getSeverityFromStepStatus = (step: DeploymentTimerStepDto) => {
    switch (step.status) {
      case 'completed':
        return 'success';
      case 'active':
        return 'info';
      case 'error':
        return 'danger';
      case 'upcoming':
      case 'unknown':
        return 'secondary';
      default:
        return 'secondary';
    }
  };

  getProgress = this.timingService.createTimeAwareFunction((step: DeploymentTimerStepDto): number => {
    return this.timingService.getProgress(step);
  });

  getHeaderTimeLabel = this.timingService.timeAwareComputed(() => {
    return this.timingService.getHeaderTimeLabel(this.timer());
  });

  getStepTime = this.timingService.createTimeAwareFunction((step: DeploymentTimerStepDto): string => {
    return this.timingService.getStepTime(step);
  });
}
