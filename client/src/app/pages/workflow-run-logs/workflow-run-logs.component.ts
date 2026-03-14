import { CommonModule, Location } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import {
  getWorkflowJobStatusOptions,
  getWorkflowJobStatusQueryKey,
  getWorkflowRunLogsOptions,
  getWorkflowRunLogsQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { QueryClient, injectQuery } from '@tanstack/angular-query-experimental';
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
import { AccordionModule } from 'primeng/accordion';
import { Button } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { Divider } from 'primeng/divider';
import { MessageModule } from 'primeng/message';
import { Panel } from 'primeng/panel';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { Toolbar } from 'primeng/toolbar';
import {
  ALL_LOG_LINE_TONES,
  LOG_LINE_FILTER_OPTIONS,
  buildGroupViews,
  getAutoExpandedLogGroupIds,
  buildSelectedFileView,
  getLineTone,
  getLineToneFilterClass,
  getRenderedLineContent,
  getWorkflowRunOutcome,
} from './workflow-run-logs.utils';
import type { WorkflowRunLogLineTone } from './workflow-run-logs.utils';

@Component({
  selector: 'app-workflow-run-logs',
  imports: [CommonModule, PageHeadingComponent, AccordionModule, Button, CardModule, Divider, MessageModule, Panel, SkeletonModule, TagModule, Toolbar, TablerIconComponent],
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
  private queryClient = inject(QueryClient);
  private hasInitializedExpandedGroups = false;

  expandedGroupNames = signal<string[]>([]);
  expandedLogGroupIds = signal<Record<string, boolean>>({});
  selectedFilePath = signal<string | null>(null);
  enabledLineTones = signal<WorkflowRunLogLineTone[]>([...ALL_LOG_LINE_TONES]);
  readonly lineFilterOptions = LOG_LINE_FILTER_OPTIONS;
  readonly getWorkflowRunOutcome = getWorkflowRunOutcome;
  readonly getLineTone = getLineTone;
  readonly getRenderedLineContent = getRenderedLineContent;

  logsQuery = injectQuery(() => ({
    ...getWorkflowRunLogsOptions({ path: { workflowRunId: this.workflowRunId() } }),
    queryKey: getWorkflowRunLogsQueryKey({ path: { workflowRunId: this.workflowRunId() } }),
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
  }));

  jobsQuery = injectQuery(() => ({
    ...getWorkflowJobStatusOptions({ path: { runId: this.workflowRunId() } }),
    queryKey: getWorkflowJobStatusQueryKey({ path: { runId: this.workflowRunId() } }),
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
  }));

  logs = computed(() => this.logsQuery.data());
  workflowOutcome = computed(() => getWorkflowRunOutcome(this.logs()?.conclusion));
  groupViews = computed(() => buildGroupViews(this.logs()?.groups ?? [], this.jobsQuery.data()?.jobs ?? [], this.workflowOutcome()));

  groupedFiles = computed(() =>
    this.groupViews().flatMap(groupView =>
      groupView.group.files.map(file => ({
        groupName: groupView.group.name,
        file,
      }))
    )
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
  selectedFileView = computed(() => buildSelectedFileView(this.selectedFile(), new Set(this.enabledLineTones()), this.expandedLogGroupIds()));

  constructor() {
    effect(() => {
      const selectedFile = this.selectedFile();
      if (selectedFile && this.selectedFilePath() !== selectedFile.file.path) {
        this.selectedFilePath.set(selectedFile.file.path);
      }
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

    const selectedEntry = this.groupedFiles().find(entry => entry.file.path === path);
    if (!selectedEntry) {
      return;
    }

    this.expandedGroupNames.update(groupNames => (groupNames.includes(selectedEntry.groupName) ? groupNames : [...groupNames, selectedEntry.groupName]));
  }

  onExpandedGroupsChange(value: string | string[] | number | number[] | null | undefined) {
    if (Array.isArray(value)) {
      this.expandedGroupNames.set(value.map(groupName => String(groupName)));
      return;
    }

    if (value === null || value === undefined) {
      this.expandedGroupNames.set([]);
      return;
    }

    this.expandedGroupNames.set([String(value)]);
  }

  retry() {
    this.logsQuery.refetch();
  }

  toggleLineTone(tone: WorkflowRunLogLineTone) {
    this.enabledLineTones.update(enabledLineTones =>
      enabledLineTones.includes(tone) ? enabledLineTones.filter(enabledTone => enabledTone !== tone) : [...enabledLineTones, tone]
    );
  }

  isLineToneEnabled(tone: WorkflowRunLogLineTone): boolean {
    return this.enabledLineTones().includes(tone);
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

  async refreshLogs() {
    const workflowRunId = this.workflowRunId();
    const refreshedLogs = await this.queryClient.fetchQuery(
      getWorkflowRunLogsOptions({
        path: { workflowRunId },
      })
    );

    this.queryClient.setQueryData(getWorkflowRunLogsQueryKey({ path: { workflowRunId } }), refreshedLogs);
    await this.jobsQuery.refetch();
  }

  goBack() {
    this.location.back();
  }

  openExternalLogs() {
    const htmlUrl = this.logs()?.htmlUrl;
    if (htmlUrl) {
      window.open(htmlUrl, '_blank');
    }
  }

  getLineToneFilterClass(tone: WorkflowRunLogLineTone): string {
    return getLineToneFilterClass(tone, this.isLineToneEnabled(tone));
  }
}
