// workflow-jobs-status.component.ts
import { CommonModule, DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, OnInit } from '@angular/core';
import { WorkflowJobDto } from '@app/core/modules/openapi';
import { getWorkflowJobStatusOptions, getWorkflowJobStatusQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCircleCheck, IconCircleDashed, IconCircleX, IconHourglass } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-workflow-jobs-status',
  standalone: true,
  imports: [CommonModule, TablerIconComponent],
  providers: [DatePipe, provideTablerIcons({ IconCircleCheck, IconCircleDashed, IconCircleX, IconHourglass })],
  templateUrl: './workflow-jobs-status.component.html',
})
export class WorkflowJobsStatusComponent implements OnInit {
  // Inputs
  workflowRunId = input.required<number>();

  private datePipe = inject(DatePipe);

  // Control when to poll for job status
  shouldPoll = computed(() => !!this.workflowRunId());

  workflowJobsQuery = injectQuery(() => ({
    ...getWorkflowJobStatusOptions({ path: { runId: this.workflowRunId() } }),
    queryKey: getWorkflowJobStatusQueryKey({ path: { runId: this.workflowRunId() } }),
    enabled: this.shouldPoll(),
    refetchInterval: 2000,
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

  // Track if any jobs failed
  hasFailedJobs = computed(() => {
    return this.jobs().some(job => job.conclusion === 'failure');
  });

  ngOnInit() {
    // Watch for changes to inputs and refresh when needed
    effect(() => {
      if (this.shouldPoll()) {
        this.workflowJobsQuery.refetch();
      }
    });
  }

  // Get CSS class for job status
  getStatusClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'text-green-600 bg-green-50';
    if (conclusion === 'failure') return 'text-red-600 bg-red-50';
    if (conclusion === 'skipped') return 'text-gray-600 bg-gray-50';
    if (conclusion === 'cancelled') return 'text-orange-600 bg-orange-50';

    if (status === 'in_progress') return 'text-blue-600 bg-blue-50';
    if (status === 'queued' || status === 'waiting') return 'text-gray-600 bg-gray-100';

    return 'text-gray-600';
  }

  // Get icon for job status
  getStatusIcon(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'circle-check';
    if (conclusion === 'failure') return 'circle-x';
    if (conclusion === 'skipped' || conclusion === 'cancelled') return 'circle-dashed';

    if (status === 'in_progress') return 'clock';
    if (status === 'queued' || status === 'waiting') return 'hourglass';

    return 'help';
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
}
