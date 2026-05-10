import { Component, computed, effect, inject, input, numberAttribute } from '@angular/core';
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
import { WorkflowRunWebSocketService } from '@app/core/services/workflow-run-websocket/workflow-run-websocket.service';
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
import { GithubLinkButtonComponent } from '@app/components/github-link-button/github-link-button.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconCircleCheck,
  IconCircleX,
  IconClockHour4,
  IconFileText,
  IconPlayerPlay,
  IconProgress,
  IconRefresh,
  IconX,
} from 'angular-tabler-icons/icons';
import { TooltipModule } from 'primeng/tooltip';
import { getStatusColors, getStatusIconClasses } from '@app/core/utils/status-colors';

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
    GithubLinkButtonComponent,
  ],
  providers: [
    MessageService,
    provideTablerIcons({
      IconArrowLeft,
      IconCircleCheck,
      IconCircleX,
      IconClockHour4,
      IconProgress,
      IconAlertTriangle,
      IconFileText,
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
  private wsService = inject(WorkflowRunWebSocketService);

  runQuery = injectQuery(() => ({
    ...getWorkflowRunByIdOptions({ path: { runId: this.runId() } }),
    staleTime: 0,
  }));

  run = computed(() => this.runQuery.data() ?? null);

  pipelineSelector = computed<PipelineSelector | null>(() => {
    const r = this.run();
    if (!r || r.label !== 'TEST') return null;
    return { repositoryId: this.repositoryId(), workflowRunId: this.runId() };
  });

  workflowJobsQuery = injectQuery(() => ({
    ...getWorkflowJobStatusOptions({ path: { runId: this.runId() } }),
    queryKey: getWorkflowJobStatusQueryKey({ path: { runId: this.runId() } }),
    enabled: this.permissions.hasWritePermission(),
    staleTime: 0,
  }));

  private wsEffect = effect(onCleanup => {
    const runId = this.runId();
    const repositoryId = this.repositoryId();
    if (!runId || !repositoryId) return;

    const sub = this.wsService.subscribe(runId, repositoryId).subscribe(msg => {
      if (msg.type === 'workflow-run-updated') {
        this.queryClient.setQueryData<WorkflowRunDto>(getWorkflowRunByIdQueryKey({ path: { runId } }), msg.run);
      } else if (msg.type === 'workflow-jobs-invalidated') {
        this.queryClient.invalidateQueries({
          queryKey: getWorkflowJobStatusQueryKey({ path: { runId } }),
        });
      }
    });

    onCleanup(() => sub.unsubscribe());
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
    // Safety net: if the WS push races with the mutation response, this guarantees a refetch.
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
    if (!run) return getStatusColors().icon;
    return getStatusIconClasses(run.conclusion, run.status);
  }
}
