import { CommonModule, Location } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { getWorkflowRunLogsQueryKey, getWorkflowRunLogsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { getWorkflowRunLogs } from '@app/core/modules/openapi/sdk.gen';
import type { WorkflowRunLogFileDto, WorkflowRunLogGroupDto, WorkflowRunLogsResponse } from '@app/core/modules/openapi/types.gen';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconArrowLeft,
  IconChevronDown,
  IconChevronRight,
  IconCircleCheck,
  IconCircleMinus,
  IconCircleX,
  IconExternalLink,
  IconFileText,
  IconFolder,
  IconQuestionMark,
  IconRefresh,
  IconServerOff,
} from 'angular-tabler-icons/icons';
import { MessageService } from 'primeng/api';
import { AccordionModule } from 'primeng/accordion';
import { Button } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { Divider } from 'primeng/divider';
import { MessageModule } from 'primeng/message';
import { Panel } from 'primeng/panel';
import { SelectButton } from 'primeng/selectbutton';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { Toolbar } from 'primeng/toolbar';
import {
  LOG_LEVEL_FILTER_OPTIONS,
  LOG_VIEW_CLASSES,
  buildFileViews,
  buildGroupViews,
  getAutoExpandedLogGroupIds,
  buildSelectedFileView,
  getLineTone,
  getLineToneBadgeClass,
  getLineToneRowClass,
  getLineToneTextClass,
  getLogGroupRowClass,
  getLogGroupTextClass,
  getRenderedLineContent,
  getWorkflowRunOutcome,
} from './workflow-run-logs.utils';
import type { LogLevelFilter, WorkflowRunLogLineTone } from './workflow-run-logs.utils';

@Component({
  selector: 'app-workflow-run-logs',
  imports: [
    CommonModule,
    FormsModule,
    PageHeadingComponent,
    AccordionModule,
    Button,
    CardModule,
    Divider,
    MessageModule,
    Panel,
    SelectButton,
    SkeletonModule,
    TagModule,
    Toolbar,
    TablerIconComponent,
  ],
  providers: [
    provideTablerIcons({
      IconArrowLeft,
      IconChevronDown,
      IconChevronRight,
      IconCircleCheck,
      IconCircleMinus,
      IconCircleX,
      IconExternalLink,
      IconFileText,
      IconFolder,
      IconQuestionMark,
      IconRefresh,
      IconServerOff,
    }),
  ],
  templateUrl: './workflow-run-logs.component.html',
})
export class WorkflowRunLogsComponent {
  repositoryId = input.required({ transform: numberAttribute });
  workflowRunId = input.required({ transform: numberAttribute });

  private location = inject(Location);
  private messageService = inject(MessageService);
  private queryClient = inject(QueryClient);
  private hasInitializedExpandedGroups = false;

  expandedGroupNames = signal<string[]>([]);
  expandedLogGroupIds = signal<Record<string, boolean>>({});
  selectedFilePath = signal<string | null>(null);
  refreshedLogs = signal<WorkflowRunLogsResponse | null>(null);
  logLevelFilter = signal<LogLevelFilter>('all');
  readonly logLevelFilterOptions = LOG_LEVEL_FILTER_OPTIONS;
  readonly logViewClasses = LOG_VIEW_CLASSES;
  readonly getWorkflowRunOutcome = getWorkflowRunOutcome;
  readonly getLineTone = getLineTone;
  readonly getRenderedLineContent = getRenderedLineContent;

  logsQuery = injectQuery(() => ({
    ...getWorkflowRunLogsOptions({ path: { workflowRunId: this.workflowRunId() } }),
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
  }));

  refreshLogsMutation = injectMutation(() => ({
    mutationFn: async (): Promise<WorkflowRunLogsResponse> => {
      const { data } = await getWorkflowRunLogs({
        path: { workflowRunId: this.workflowRunId() },
        query: { forceRefresh: true },
        throwOnError: true,
      });
      return data;
    },
    onSuccess: data => {
      this.refreshedLogs.set(data);
      this.queryClient.setQueryData(getWorkflowRunLogsQueryKey({ path: { workflowRunId: this.workflowRunId() } }), data);
    },
    onError: () => {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to refresh workflow logs.',
      });
    },
  }));

  logsResponse = computed(() => this.refreshedLogs() ?? this.logsQuery.data() ?? null);
  workflowOutcome = computed(() => getWorkflowRunOutcome(this.logsResponse()?.conclusion));
  groupViews = computed(() => buildGroupViews(this.logsResponse()?.groups ?? []));
  groupedFiles = computed(() => buildFileViews(this.groupViews()));
  groupedFilesByGroup = computed(() =>
    this.groupedFiles().reduce<Record<string, ReturnType<typeof this.groupedFiles>>>((groupedFilesByGroup, fileView) => {
      const groupName = fileView.groupView.group.name;
      groupedFilesByGroup[groupName] = [...(groupedFilesByGroup[groupName] ?? []), fileView];
      return groupedFilesByGroup;
    }, {})
  );

  selectedFile = computed(() => {
    const selectedFilePath = this.selectedFilePath();
    if (!selectedFilePath) {
      return this.groupedFiles()[0] ?? null;
    }

    return this.groupedFiles().find(entry => entry.file.path === selectedFilePath) ?? this.groupedFiles()[0] ?? null;
  });

  hasLogs = computed(() => this.groupedFiles().length > 0);
  activeFilePath = computed(() => this.selectedFile()?.file.path ?? null);
  selectedStep = computed(() => {
    const selectedFile = this.selectedFile();
    if (!selectedFile) {
      return null;
    }

    return selectedFile.groupView.group.steps.find(step => step.number === selectedFile.file.stepNumber && step.name === selectedFile.file.stepName) ?? null;
  });
  selectedFileView = computed(() => buildSelectedFileView(this.selectedFile(), this.logLevelFilter(), this.expandedLogGroupIds()));
  hasFilterableContent = computed(() => {
    const view = this.selectedFileView();
    if (!view) return false;
    return view.lineStats.errorCount > 0 || view.lineStats.warningCount > 0;
  });

  constructor() {
    effect(() => {
      this.workflowRunId();
      this.refreshedLogs.set(null);
    });

    effect(() => {
      const firstGroupName = this.groupViews()[0]?.group.name;
      if (firstGroupName && !this.hasInitializedExpandedGroups) {
        this.expandedGroupNames.set([firstGroupName]);
        this.hasInitializedExpandedGroups = true;
      }
    });

    effect(() => {
      const selectedFile = this.selectedFile();
      if (!selectedFile) {
        return;
      }

      const errorGroupIds = getAutoExpandedLogGroupIds(selectedFile.file.content, 'error');
      if (Object.keys(errorGroupIds).length === 0) {
        return;
      }

      this.expandedLogGroupIds.update(expandedLogGroupIds => ({
        ...expandedLogGroupIds,
        ...errorGroupIds,
      }));
    });
  }

  selectFile(path: string) {
    this.selectedFilePath.set(path);
    this.logLevelFilter.set('all');

    const selectedEntry = this.groupedFiles().find(entry => entry.file.path === path);
    if (!selectedEntry) {
      return;
    }

    this.expandedGroupNames.update(groupNames => (groupNames.includes(selectedEntry.groupView.group.name) ? groupNames : [...groupNames, selectedEntry.groupView.group.name]));
  }

  onExpandedGroupsChange(value: string | string[] | number | number[] | null | undefined) {
    if (value == null) {
      this.expandedGroupNames.set([]);
      return;
    }

    const values = Array.isArray(value) ? value : [value];
    this.expandedGroupNames.set(values.map(v => String(v)));
  }

  retry() {
    this.logsQuery.refetch();
  }

  toggleLogGroup(groupId: string) {
    this.expandedLogGroupIds.update(expandedLogGroupIds => ({
      ...expandedLogGroupIds,
      [groupId]: !this.isLogGroupExpanded(groupId),
    }));
  }

  isLogGroupExpanded(groupId: string): boolean {
    return this.expandedLogGroupIds()[groupId] ?? false;
  }

  refreshLogs() {
    return this.refreshLogsMutation.mutateAsync();
  }

  goBack() {
    this.location.back();
  }

  openExternalLogs() {
    const htmlUrl = this.logsResponse()?.htmlUrl;
    if (htmlUrl) {
      const newWindow = window.open(htmlUrl, '_blank', 'noopener,noreferrer');
      if (newWindow) {
        newWindow.opener = null;
      }
    }
  }

  getLineToneBadgeClass(tone: Extract<WorkflowRunLogLineTone, 'warning' | 'error'>): string {
    return getLineToneBadgeClass(tone);
  }

  getLineToneRowClass(tone: WorkflowRunLogLineTone): string {
    return getLineToneRowClass(tone);
  }

  getLineToneTextClass(tone: WorkflowRunLogLineTone): string {
    return getLineToneTextClass(tone);
  }

  getLogGroupRowClass(): string {
    return getLogGroupRowClass();
  }

  getLogGroupTextClass(): string {
    return getLogGroupTextClass();
  }

  getFileViewsForGroup(groupName: string) {
    return this.groupedFilesByGroup()[groupName] ?? [];
  }

  getJobDisplayName(group: WorkflowRunLogGroupDto): string {
    return group.jobName || group.name;
  }

  getStepDisplayName(file: WorkflowRunLogFileDto): string {
    return file.stepName || file.displayName;
  }

  getStepLabel(file: WorkflowRunLogFileDto): string {
    return file.stepName ? 'Step' : 'Log';
  }

  getStepLogCountLabel(count: number): string {
    return `${count} ${count === 1 ? 'step log' : 'step logs'}`;
  }

  getJobCountLabel(count: number): string {
    return `${count} ${count === 1 ? 'job' : 'jobs'}`;
  }

  getStepDuration(startTime: string | undefined, endTime: string | undefined): string {
    if (!startTime || !endTime) {
      return '';
    }

    const seconds = Math.max(0, Math.floor((new Date(endTime).getTime() - new Date(startTime).getTime()) / 1000));
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    if (minutes === 0) return `${seconds}s`;
    return remainingSeconds === 0 ? `${minutes}m` : `${minutes}m ${remainingSeconds}s`;
  }
}
