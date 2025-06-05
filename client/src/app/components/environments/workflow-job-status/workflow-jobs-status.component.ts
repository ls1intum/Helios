import { CommonModule, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { EnvironmentDeployment, WorkflowJobDto } from '@app/core/modules/openapi';
import { getWorkflowJobStatusOptions, getWorkflowJobStatusQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PermissionService } from '@app/core/services/permission.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconBrandGithub,
  IconCircleCheck,
  IconCircleMinus,
  IconCircleX,
  IconClock,
  IconExternalLink,
  IconProgress,
  IconChevronDown,
  IconChevronRight,
} from 'angular-tabler-icons/icons';
import { Button } from 'primeng/button';

@Component({
  selector: 'app-workflow-jobs-status',
  standalone: true,
  imports: [CommonModule, TablerIconComponent, Button],
  providers: [
    DatePipe,
    provideTablerIcons({
      IconClock,
      IconProgress,
      IconCircleMinus,
      IconCircleCheck,
      IconCircleX,
      IconBrandGithub,
      IconExternalLink,
      IconChevronDown,
      IconChevronRight,
    }),
  ],
  templateUrl: './workflow-jobs-status.component.html',
})
export class WorkflowJobsStatusComponent {
  permissions = inject(PermissionService);
  latestDeployment = input.required<EnvironmentDeployment | undefined>();

  workflowRunId = input.required<number>();

  private datePipe = inject(DatePipe);

  private extraRefetchStarted = signal(false);
  private extraRefetchCompleted = signal(false);

  expandedJobs = signal<Record<string, boolean>>({});

  toggleJobExpansion(jobId: number) {
    this.expandedJobs.update(state => ({
      ...state,
      [jobId]: !state[jobId],
    }));
  }

  isJobExpanded(jobId: number): boolean {
    return !!this.expandedJobs()[jobId];
  }

  // Control when to poll for job status - during active deployment or limited extra fetches
  shouldPoll = computed(() => {
    if (!this.permissions.hasWritePermission()) {
      return false;
    }

    if (!!this.workflowRunId() && !!this.deploymentInProgress()) {
      // Always poll if the deployment is in progress
      return true;
    }

    // Poll for additional fetches after failure to display job status
    if (this.extraRefetchStarted() && !this.extraRefetchCompleted()) {
      return true;
    }

    return false;
  });

  workflowJobsQuery = injectQuery(() => ({
    ...getWorkflowJobStatusOptions({ path: { runId: this.workflowRunId() } }),
    queryKey: getWorkflowJobStatusQueryKey({ path: { runId: this.workflowRunId() } }),
    enabled: this.shouldPoll(),
    refetchInterval: this.extraRefetchStarted() ? 10000 : 5000, // Slower interval for extra fetches
    staleTime: 0,
  }));

  // Extract jobs data for the template - now properly typed
  jobs = computed<WorkflowJobDto[]>(() => {
    const response = this.workflowJobsQuery.data();
    if (!response || !response.jobs) return [];
    return response.jobs;
  });

  // Check if all jobs are completed
  allJobsCompleted = computed(() => {
    const jobs = this.jobs();
    if (!jobs.length) return false;
    return jobs.every(job => job.status === 'completed');
  });

  deploymentInProgress = computed(() => {
    return ['IN_PROGRESS', 'WAITING', 'REQUESTED', 'PENDING', 'QUEUED'].includes(this.latestDeployment()?.state || '') && this.latestDeployment()?.workflowRunHtmlUrl;
  });

  deploymentSuccessful = computed(() => {
    return ['SUCCESS'].includes(this.latestDeployment()?.state || '') && this.latestDeployment()?.workflowRunHtmlUrl;
  });

  deploymentUnsuccessful = computed(() => {
    return ['ERROR', 'FAILURE'].includes(this.latestDeployment()?.state || '') && this.latestDeployment()?.workflowRunHtmlUrl;
  });

  // Track if any jobs failed
  hasFailedJobs = computed(() => {
    return this.jobs().some(job => job.conclusion === 'failure');
  });

  constructor() {
    // Watch for changes to inputs and refresh when needed
    effect(() => {
      if (this.shouldPoll()) {
        this.workflowJobsQuery.refetch();
      }
    });
    effect(() => {
      if (this.deploymentUnsuccessful() && !this.extraRefetchStarted()) {
        // Deployment just completed, start extra fetches
        console.debug('Deployment completed, starting extra refetches');
        this.extraRefetchStarted.set(true);

        // Schedule the end of extra refetches
        setTimeout(() => {
          console.debug('Extra refetches completed');
          this.extraRefetchCompleted.set(true);
        }, 60 * 1000); // Stop after 1 minute
      }
    });
  }
  // Get CSS class for job status
  getStatusClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'text-green-600 bg-green-100 dark:text-green-400 dark:bg-green-900/30';
    if (conclusion === 'failure') return 'text-red-600 bg-red-100 dark:text-red-400 dark:bg-red-900/30';
    if (conclusion === 'skipped') return 'text-gray-600 bg-gray-100 dark:text-gray-400 dark:bg-gray-900';
    if (conclusion === 'cancelled') return 'text-orange-600 bg-orange-50 dark:text-orange-400 dark:bg-orange-900/30';

    if (status === 'in_progress') return 'text-blue-600 bg-blue-50 dark:text-blue-400 dark:bg-blue-900/30';
    if (status === 'queued' || status === 'waiting') return 'text-gray-600 bg-gray-100 dark:text-gray-400 dark:bg-gray-900';

    return 'text-gray-600 dark:text-gray-400';
  }

  getStatusIndicatorClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'bg-green-500 dark:bg-green-400';
    if (conclusion === 'failure') return 'bg-red-500 dark:bg-red-400';
    if (conclusion === 'skipped') return 'bg-gray-400 dark:bg-gray-500';
    if (conclusion === 'cancelled') return 'bg-orange-500 dark:bg-orange-400';

    if (status === 'in_progress') return 'bg-blue-500 dark:bg-blue-400';
    if (status === 'queued' || status === 'waiting') return 'bg-gray-300 dark:bg-gray-600';

    return 'bg-gray-300 dark:bg-gray-600';
  }

  // Get icon for job status
  getStatusIcon(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'circle-check';
    if (conclusion === 'failure') return 'circle-x';
    if (conclusion === 'skipped' || conclusion === 'cancelled') return 'circle-minus';

    if (status === 'in_progress') return 'progress';
    if (status === 'queued' || status === 'waiting') return 'clock';

    return 'help';
  }

  getIconColorClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'text-green-600 dark:text-green-400';
    if (conclusion === 'failure') return 'text-red-600 dark:text-red-400';
    if (conclusion === 'skipped') return 'text-gray-600 dark:text-gray-400';
    if (conclusion === 'cancelled') return 'text-orange-600 dark:text-orange-400';

    if (status === 'in_progress') return 'text-blue-600 dark:text-blue-400 animate-spin';
    if (status === 'queued' || status === 'waiting') return 'text-gray-600 dark:text-gray-400';

    return 'text-gray-600 dark:text-gray-400';
  }

  // Get status text for display
  getStatusText(status: string | null | undefined, conclusion: string | null | undefined): string {
    return conclusion || status || 'Unknown';
  }

  // Format timestamp to readable format
  formatTime(timestamp: string | null | undefined): string {
    if (!timestamp) return '';
    return this.datePipe.transform(timestamp, 'HH:mm:ss') || '';
  }

  // Calculate duration between start and end time
  getDuration(startTime: string | undefined, endTime: string | undefined): string {
    if (!startTime) return '';

    const start = new Date(startTime).getTime();
    const end = endTime ? new Date(endTime).getTime() : Date.now();

    const durationMs = end - start;
    const seconds = Math.floor(durationMs / 1000);

    if (seconds < 60) {
      return `${seconds}s`;
    } else if (seconds < 3600) {
      const minutes = Math.floor(seconds / 60);
      const remainingSeconds = seconds % 60;
      return `${minutes}m ${remainingSeconds}s`;
    } else {
      const hours = Math.floor(seconds / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      return `${hours}h ${minutes}m`;
    }
  }

  openLink(url: string | undefined) {
    if (url) {
      window.open(url, '_blank');
    }
  }
}
