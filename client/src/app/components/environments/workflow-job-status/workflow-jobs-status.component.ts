import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { EnvironmentDeployment, WorkflowJobDto } from '@app/core/modules/openapi';
import { getWorkflowJobStatusOptions, getWorkflowJobStatusQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PermissionService } from '@app/core/services/permission.service';
import { WorkflowJobListComponent } from '@app/components/workflow-job-list/workflow-job-list.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconBrandGithub } from 'angular-tabler-icons/icons';
import { Button } from 'primeng/button';

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

  openLink(url: string | undefined) {
    if (url) {
      window.open(url, '_blank');
    }
  }
}
