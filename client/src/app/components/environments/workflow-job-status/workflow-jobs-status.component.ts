import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { EnvironmentDeployment, WorkflowJobDto } from '@app/core/modules/openapi';
import { getWorkflowJobStatusOptions, getWorkflowJobStatusQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PermissionService } from '@app/core/services/permission.service';
import { WorkflowJobListComponent } from '@app/components/workflow-job-list/workflow-job-list.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconBrandGithub } from 'angular-tabler-icons/icons';
import { Button } from 'primeng/button';
import { getStatusColors } from '@app/core/utils/status-colors';

@Component({
  selector: 'app-workflow-jobs-status',
  standalone: true,
  imports: [TablerIconComponent, Button, WorkflowJobListComponent],
  providers: [
    provideTablerIcons({
      IconBrandGithub,
    }),
  ],
  templateUrl: './workflow-jobs-status.component.html',
})
export class WorkflowJobsStatusComponent {
  permissions = inject(PermissionService);

  /**
   * The latest deployment to monitor. When undefined, the component renders nothing.
   * The workflowRunId is derived from the deployment's workflowRunHtmlUrl.
   */
  latestDeployment = input<EnvironmentDeployment | undefined>();

  workflowRunId = computed(() => {
    const url = this.latestDeployment()?.workflowRunHtmlUrl;
    const match = url?.match(/\/runs\/(\d+)$/);
    return match ? parseInt(match[1], 10) : undefined;
  });

  private extraRefetchStarted = signal(false);
  private extraRefetchCompleted = signal(false);

  deploymentInProgress = computed(() => {
    return ['IN_PROGRESS', 'WAITING', 'REQUESTED', 'PENDING', 'QUEUED'].includes(this.latestDeployment()?.state || '') && this.latestDeployment()?.workflowRunHtmlUrl;
  });

  deploymentSuccessful = computed(() => {
    return ['SUCCESS'].includes(this.latestDeployment()?.state || '') && this.latestDeployment()?.workflowRunHtmlUrl;
  });

  deploymentUnsuccessful = computed(() => {
    return ['ERROR', 'FAILURE'].includes(this.latestDeployment()?.state || '') && this.latestDeployment()?.workflowRunHtmlUrl;
  });

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
    ...getWorkflowJobStatusOptions({ path: { runId: this.workflowRunId() ?? 0 } }),
    queryKey: getWorkflowJobStatusQueryKey({ path: { runId: this.workflowRunId() ?? 0 } }),
    enabled: this.shouldPoll(),
    refetchInterval: () => (this.extraRefetchStarted() ? 10000 : 5000),
    staleTime: 0,
  }));

  jobs = computed<WorkflowJobDto[]>(() => {
    const response = this.workflowJobsQuery.data();
    if (!response || !response.jobs) return [];
    return response.jobs;
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
    return getStatusColors(conclusion, status).badge;
  }

  getStatusIndicatorClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    return getStatusColors(conclusion, status).indicator;
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
    const { icon } = getStatusColors(conclusion, status);
    return status === 'in_progress' ? `${icon} animate-spin` : icon;
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
