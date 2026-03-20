import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WorkflowJobDto, WorkflowRunDto } from '@app/core/modules/openapi';
import {
  cancelWorkflowRunMutation,
  getWorkflowJobStatusOptions,
  getWorkflowJobStatusQueryKey,
  getWorkflowRunByIdOptions,
  getWorkflowRunByIdQueryKey,
  reRunWorkflowMutation,
  reRunFailedJobsMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PermissionService } from '@app/core/services/permission.service';
import { WorkflowJobListComponent } from '@app/components/workflow-job-list/workflow-job-list.component';
import { PipelineTestResultsComponent } from '@app/components/pipeline/test-results/pipeline-test-results.component';
import { PipelineSelector } from '@app/components/pipeline/pipeline.component';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { ToastModule } from 'primeng/toast';
import { SkeletonModule } from 'primeng/skeleton';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconArrowLeft,
  IconBrandGithub,
  IconCircleCheck,
  IconCircleX,
  IconClockHour4,
  IconProgress,
  IconAlertTriangle,
  IconPlayerPlay,
  IconRefresh,
  IconX,
} from 'angular-tabler-icons/icons';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-workflow-run-details',
  standalone: true,
  imports: [
    RouterLink,
    TagModule,
    ButtonModule,
    DividerModule,
    ToastModule,
    SkeletonModule,
    TimeAgoPipe,
    TablerIconComponent,
    TooltipModule,
    WorkflowJobListComponent,
    PipelineTestResultsComponent,
  ],
  providers: [
    MessageService,
    provideTablerIcons({
      IconArrowLeft,
      IconBrandGithub,
      IconCircleCheck,
      IconCircleX,
      IconClockHour4,
      IconProgress,
      IconAlertTriangle,
      IconPlayerPlay,
      IconRefresh,
      IconX,
    }),
  ],
  templateUrl: './workflow-run-details.component.html',
})
export class WorkflowRunDetailsComponent {
  repositoryId = input.required({ transform: numberAttribute });
  runId = input.required({ transform: numberAttribute });

  protected permissions = inject(PermissionService);
  private messageService = inject(MessageService);
  private queryClient = inject(QueryClient);

  // Duration to continue polling after a user action (cancel/rerun) to ensure UI updates promptly.
  private readonly FORCED_POLL_DURATION_MS = 2 * 60 * 1000;

  // Timestamp set when a user action (cancel/rerun) is triggered.
  private actionTriggeredAt = signal<number | null>(null);

  runQuery = injectQuery(() => {
    const actionAt = this.actionTriggeredAt();
    return {
      ...getWorkflowRunByIdOptions({ path: { runId: this.runId() } }),
      refetchInterval: (query: { state?: { data?: WorkflowRunDto } }) => {
        const data = query.state?.data;
        const activeStatuses = ['IN_PROGRESS', 'QUEUED', 'WAITING', 'PENDING', 'REQUESTED'];
        if (data && activeStatuses.includes(data.status)) return 5000;
        if (actionAt !== null && Date.now() - actionAt < this.FORCED_POLL_DURATION_MS) return 5000;
        return 0;
      },
      staleTime: 0,
    };
  });

  run = computed(() => this.runQuery.data() ?? null);

  pipelineSelector = computed<PipelineSelector | null>(() => {
    const r = this.run();
    if (!r || r.label !== 'TEST') return null;
    return { repositoryId: this.repositoryId(), workflowRunId: this.runId() };
  });

  workflowJobsQuery = injectQuery(() => {
    const actionAt = this.actionTriggeredAt();
    return {
      ...getWorkflowJobStatusOptions({ path: { runId: this.runId() } }),
      queryKey: getWorkflowJobStatusQueryKey({ path: { runId: this.runId() } }),
      enabled: this.permissions.hasWritePermission(),
      refetchInterval: (query: { state?: { data?: { jobs?: WorkflowJobDto[] } } }) => {
        const data = query.state?.data;
        const inProgress = (data?.jobs ?? []).some(j => j.status === 'in_progress' || j.status === 'queued' || j.status === 'waiting');
        if (inProgress) return 5000;
        if (actionAt !== null && Date.now() - actionAt < this.FORCED_POLL_DURATION_MS) return 5000;
        return 0;
      },
      staleTime: 0,
    };
  });

  jobs = computed<WorkflowJobDto[]>(() => {
    const response = this.workflowJobsQuery.data();
    if (!response || !response.jobs) return [];
    return response.jobs;
  });

  reRunMutation = injectMutation(() => ({
    ...reRunWorkflowMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Re-run triggered', detail: 'All jobs have been queued for re-run.' });
      this.invalidateRunQueries();
    },
    onError: () => {
      this.messageService.add({ severity: 'error', summary: 'Re-run failed', detail: 'Could not trigger re-run. Please try again.' });
    },
  }));

  reRunFailedMutation = injectMutation(() => ({
    ...reRunFailedJobsMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Re-run triggered', detail: 'Failed jobs have been queued for re-run.' });
      this.invalidateRunQueries();
    },
    onError: () => {
      this.messageService.add({ severity: 'error', summary: 'Re-run failed', detail: 'Could not trigger re-run of failed jobs. Please try again.' });
    },
  }));

  cancelMutation = injectMutation(() => ({
    ...cancelWorkflowRunMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Cancellation requested', detail: 'The workflow run has been requested to cancel.' });
      this.invalidateRunQueries();
    },
    onError: () => {
      this.messageService.add({ severity: 'error', summary: 'Cancellation failed', detail: 'Could not cancel the workflow run. Please try again.' });
    },
  }));

  private invalidateRunQueries(): void {
    const runId = this.runId();
    // Set the timestamp to trigger more frequent polling for a short duration to ensure UI updates promptly after user actions.
    this.actionTriggeredAt.set(Date.now());
    this.queryClient.invalidateQueries({ queryKey: getWorkflowRunByIdQueryKey({ path: { runId } }) });
    this.queryClient.invalidateQueries({ queryKey: getWorkflowJobStatusQueryKey({ path: { runId } }) });
  }

  canReRunFailed = computed(() => {
    const r = this.run();
    return ['FAILURE', 'STARTUP_FAILURE', 'TIMED_OUT'].includes(r?.conclusion ?? '');
  });

  isRunCompleted = computed(() => {
    const r = this.run();
    return r?.conclusion != null;
  });

  isRunActive = computed(() => {
    const r = this.run();
    return ['IN_PROGRESS', 'QUEUED', 'WAITING', 'PENDING', 'REQUESTED'].includes(r?.status ?? '');
  });

  onReRun(): void {
    this.reRunMutation.mutate({ path: { runId: this.runId() } });
  }

  onReRunFailed(): void {
    this.reRunFailedMutation.mutate({ path: { runId: this.runId() } });
  }

  onCancel(): void {
    this.cancelMutation.mutate({ path: { runId: this.runId() } });
  }

  formatExactDate(dateStr: string | null | undefined): string | undefined {
    if (!dateStr) return undefined;
    return new Date(dateStr).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }

  getDuration(run: WorkflowRunDto | null): string {
    if (!run?.runStartedAt) return '—';
    const start = new Date(run.runStartedAt).getTime();
    const end = run.updatedAt ? new Date(run.updatedAt).getTime() : Date.now();
    const seconds = Math.floor((end - start) / 1000);
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
  }

  openRunExternal(): void {
    const r = this.run();
    if (r?.htmlUrl) {
      window.open(r.htmlUrl, '_blank');
    }
  }

  getWorkflowStatusIcon(run: WorkflowRunDto | null): string {
    if (!run) return 'help';
    if (run.conclusion === 'SUCCESS') return 'circle-check';
    if (['FAILURE', 'STARTUP_FAILURE', 'TIMED_OUT'].includes(run.conclusion ?? '')) return 'circle-x';
    if (run.conclusion === 'CANCELLED') return 'circle-x';
    if (run.status === 'IN_PROGRESS') return 'progress';
    if (['QUEUED', 'WAITING', 'PENDING', 'REQUESTED'].includes(run.status)) return 'clock-hour-4';
    if (run.status === 'ACTION_REQUIRED' || run.conclusion === 'ACTION_REQUIRED') return 'alert-triangle';
    return 'circle-x';
  }

  getWorkflowStatusClass(run: WorkflowRunDto | null): string {
    if (!run) return 'text-surface-500';
    if (run.conclusion === 'SUCCESS') return 'text-green-500';
    if (['FAILURE', 'STARTUP_FAILURE', 'TIMED_OUT'].includes(run.conclusion ?? '')) return 'text-red-500';
    if (run.conclusion === 'CANCELLED') return 'text-surface-500';
    if (run.status === 'IN_PROGRESS') return 'text-blue-500 animate-spin';
    if (['QUEUED', 'WAITING', 'PENDING', 'REQUESTED'].includes(run.status)) return 'text-amber-500';
    if (run.status === 'ACTION_REQUIRED' || run.conclusion === 'ACTION_REQUIRED') return 'text-orange-500';
    return 'text-surface-500';
  }
}
