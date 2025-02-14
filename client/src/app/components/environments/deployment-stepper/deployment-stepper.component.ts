import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { EnvironmentDeployment } from '@app/core/modules/openapi';

@Component({
  selector: 'app-deployment-stepper',
  imports: [CommonModule, IconsModule],
  templateUrl: './deployment-stepper.component.html',
})
export class DeploymentStepperComponent {
  @Input() deployment: EnvironmentDeployment | undefined;

  steps: ('WAITING' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS')[] = ['WAITING', 'PENDING', 'IN_PROGRESS', 'SUCCESS'];
  stepDescriptions: { [key in 'WAITING' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS']: string } = {
    WAITING: 'Waiting for approval',
    PENDING: 'Pending deployment',
    IN_PROGRESS: 'Deployment in progress',
    SUCCESS: 'Deployment successful',
  };

  estimatedTimes = {
    WAITING: 1, // minutes
    PENDING: 2,
    IN_PROGRESS: 5,
  };

  get currentStepIndex(): number {
    if (!this.deployment?.state) return -1;
    const state = this.deployment.state as 'WAITING' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS';
    return this.steps.indexOf(state);
  }

  get isErrorState(): boolean {
    return ['ERROR', 'FAILURE'].includes(this.deployment?.state || '');
  }

  getStepStatus(index: number): string {
    if (index === 3 && this.currentStepIndex === 3) return 'completed';
    if (this.currentStepIndex < 0) return 'upcoming';
    if (index < this.currentStepIndex) return 'completed';
    if (index === this.currentStepIndex) return this.isErrorState ? 'error' : 'active';
    return 'upcoming';
  }

  get progressPercentage(): number {
    if (this.currentStepIndex < 0) return 0;
    // Calculate percentage based on completed steps
    return (this.currentStepIndex / (this.steps.length - 1)) * 100;
  }

  getTimeEstimate(index: number): string {
    // Show estimates for current and upcoming steps only
    if (index < this.currentStepIndex) return '';

    const step = this.steps[index];
    const estimate = this.estimatedTimes[step as keyof typeof this.estimatedTimes];

    // For completed steps, show actual duration if available
    if (index === this.currentStepIndex && this.deployment?.updatedAt) {
      const duration = this.calculateStepDuration();
      if (duration) return `${duration}m`;
    }

    return estimate ? `${estimate}m` : '';
  }

  private calculateStepDuration(): number | null {
    if (!this.deployment?.createdAt) return null;

    const created = new Date(this.deployment.createdAt).getTime();
    const now = Date.now();
    const minutes = Math.round((now - created) / 60000);

    return minutes;
  }
}
