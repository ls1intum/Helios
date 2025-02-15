import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { ProgressBarModule } from 'primeng/progressbar';
import { EnvironmentDeployment } from '@app/core/modules/openapi';

@Component({
  selector: 'app-deployment-stepper',
  imports: [CommonModule, IconsModule, ProgressBarModule],
  templateUrl: './deployment-stepper.component.html',
})
export class DeploymentStepperComponent implements OnInit, OnDestroy {
  @Input() deployment: EnvironmentDeployment | undefined;

  // Define the four steps. (Note: estimatedTimes are defined only for the first three.)
  steps: ('WAITING' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS')[] = ['WAITING', 'PENDING', 'IN_PROGRESS', 'SUCCESS'];
  stepDescriptions: {
    [key in 'WAITING' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'ERROR' | 'FAILURE']: string;
  } = {
    WAITING: 'Waiting for approval',
    PENDING: 'Pending deployment',
    IN_PROGRESS: 'Deployment in progress',
    SUCCESS: 'Deployment successful',
    ERROR: 'Deployment error',
    FAILURE: 'Deployment failed',
  };

  // Estimated times in minutes for each non-terminal step.
  estimatedTimes = {
    WAITING: 1,
    PENDING: 1,
    IN_PROGRESS: 5,
  };

  // A timer updated every second to drive the dynamic progress.
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

  /**
   * Calculates a virtual step index based solely on elapsed time.
   * - If elapsed time is less than WAITING's estimated time, returns 0.
   * - If elapsed time is between WAITING and WAITING+PENDING, returns 1.
   * - If between WAITING+PENDING and WAITING+PENDING+IN_PROGRESS, returns 2.
   * - Otherwise, returns 3 (i.e. SUCCESS).
   */
  get virtualStepIndex(): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMinutes = (this.time - start) / 60000;
    if (elapsedMinutes >= this.estimatedTimes.WAITING + this.estimatedTimes.PENDING + this.estimatedTimes.IN_PROGRESS) {
      return 3;
    } else if (elapsedMinutes >= this.estimatedTimes.WAITING + this.estimatedTimes.PENDING) {
      return 2;
    } else if (elapsedMinutes >= this.estimatedTimes.WAITING) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   * Determines the effective step index.
   * If the deployment state is terminal (SUCCESS, ERROR, FAILURE), use that state.
   * Otherwise, use the virtual (elapsed-time based) step.
   */
  get currentEffectiveStepIndex(): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    // if (['SUCCESS', 'ERROR', 'FAILURE'].includes(this.deployment.state || '')) {
    const index = this.steps.indexOf(this.deployment.state as 'WAITING' | 'PENDING' | 'IN_PROGRESS' | 'SUCCESS');
    return index !== -1 ? index : 3;
    // }
    // return this.virtualStepIndex;
  }

  get isErrorState(): boolean {
    return ['ERROR', 'FAILURE'].includes(this.deployment?.state || '');
  }

  /**
   * Returns a status string for a given step index.
   * - "completed" if the step index is less than the current effective step.
   * - "active" if it matches the current effective step (or "error" if in error state).
   * - "upcoming" otherwise.
   */
  getStepStatus(index: number): string {
    const effectiveStep = this.currentEffectiveStepIndex;
    if (index < effectiveStep) return 'completed';
    if (index === effectiveStep) return this.isErrorState ? 'error' : 'active';
    return 'upcoming';
  }

  /**
   * Computes overall progress in a piecewise manner.
   * Each segment (WAITING, PENDING, IN_PROGRESS) is allotted 25% of the bar.
   * For the current segment, progress is calculated as a ratio of its elapsed time;
   * if a segment is already completed (i.e. virtualStepIndex > segment index), that segment is full.
   * When the virtual step reaches 3, the progress returns 100%.
   */
  get dynamicProgress(): number {
    if (!this.deployment || !this.deployment.createdAt) return 0;
    // If the server indicates a terminal state, show full progress.
    if (['SUCCESS', 'ERROR', 'FAILURE'].includes(this.deployment.state || '')) {
      return 100;
    }
    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMs = this.time - start;
    // If the virtual step is 3, show 100%.
    if (this.currentEffectiveStepIndex === 3) return 100;

    let progress = 0;
    let cumulativeEstimateMs = 0;
    // There are three segments: 0 (0–25%), 1 (25–50%), 2 (50–75%).
    for (let i = 0; i < 3; i++) {
      const segStart = i * 25;
      const segEnd = (i + 1) * 25;
      const estimatedMs = this.estimatedTimes[this.steps[i] as keyof typeof this.estimatedTimes] * 60000;
      // If this step is fully complete, assign the full segment.
      if (this.currentEffectiveStepIndex > i) {
        progress = segEnd;
        cumulativeEstimateMs += estimatedMs;
      }
      // If we're in the middle of this segment, compute the ratio.
      else if (this.currentEffectiveStepIndex === i) {
        const elapsedForSegmentMs = elapsedMs - cumulativeEstimateMs;
        let ratio = elapsedForSegmentMs / estimatedMs;
        console.log('elapsed for segment ', elapsedForSegmentMs);
        console.log('estimated ms ', estimatedMs);
        if (ratio > 1) {
          ratio = 1;
        }
        progress = segStart + ratio * 25;
        console.log('progress ', progress);
        break;
      } else {
        break;
      }
    }
    return Math.floor(progress);
  }

  /**
   * Returns the estimated time remaining (in minutes) for a given step.
   * It subtracts the elapsed time from the cumulative estimated time up to that step.
   */
  getTimeEstimate(index: number): string {
    if (!this.deployment || !this.deployment.createdAt) return '';
    const start = new Date(this.deployment.createdAt).getTime();
    const elapsedMinutes = (this.time - start) / 60000;
    let cumulative = 0;
    for (let i = 0; i < 3; i++) {
      const est = this.estimatedTimes[this.steps[i] as keyof typeof this.estimatedTimes];
      cumulative += est;
      if (i === index) {
        if (this.getStepStatus(i) === 'completed') {
          return '0m 0s';
        }
        const remaining = cumulative - elapsedMinutes;
        const minutes = Math.floor(remaining);
        const seconds = Math.floor((remaining - minutes) * 60);
        return remaining > 0 ? `${minutes}m ${seconds}s` : '0m 0s';
      }
    }
    return '';
  }
}
