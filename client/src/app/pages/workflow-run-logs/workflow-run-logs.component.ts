import { CommonModule, Location } from '@angular/common';
import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import {
  getWorkflowJobStatusOptions,
  getWorkflowJobStatusQueryKey,
  getWorkflowRunLogsOptions,
  getWorkflowRunLogsQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import type { WorkflowJobDto, WorkflowRunLogFileDto, WorkflowRunLogGroupDto, WorkflowRunLogsResponse } from '@app/core/modules/openapi/types.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { AccordionModule } from 'primeng/accordion';
import { Button } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { Divider } from 'primeng/divider';
import { MessageModule } from 'primeng/message';
import { Panel } from 'primeng/panel';
import { ScrollPanel } from 'primeng/scrollpanel';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { Toolbar } from 'primeng/toolbar';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconArrowLeft,
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

type SelectedWorkflowRunLogFile = {
  groupName: string;
  file: WorkflowRunLogFileDto;
};

type WorkflowRunLogLine = {
  number: number;
  content: string;
};

type WorkflowRunLogGroupView = {
  group: WorkflowRunLogGroupDto;
  job: WorkflowJobDto | null;
};

type WorkflowRunConclusion = WorkflowRunLogsResponse['conclusion'];
type WorkflowJobConclusion = WorkflowJobDto['conclusion'];

type WorkflowRunOutcome = {
  icon: 'circle-check' | 'circle-minus' | 'circle-x' | 'question-mark';
  iconColorClass: string;
  label: string;
  tagSeverity: 'success' | 'warn' | 'danger' | 'secondary';
};

@Component({
  selector: 'app-workflow-run-logs',
  imports: [CommonModule, PageHeadingComponent, AccordionModule, Button, CardModule, Divider, MessageModule, Panel, ScrollPanel, SkeletonModule, TagModule, Toolbar, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconArrowLeft,
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
  private hasInitializedExpandedGroups = false;

  forceRefresh = signal(false);
  expandedGroupNames = signal<string[]>([]);
  selectedFilePath = signal<string | null>(null);

  logsQuery = injectQuery(() => ({
    ...getWorkflowRunLogsOptions({
      path: { workflowRunId: this.workflowRunId() },
      query: this.forceRefresh() ? { forceRefresh: true } : undefined,
    }),
    queryKey: getWorkflowRunLogsQueryKey({
      path: { workflowRunId: this.workflowRunId() },
      query: this.forceRefresh() ? { forceRefresh: true } : undefined,
    }),
    retry: false,
  }));

  jobsQuery = injectQuery(() => ({
    ...getWorkflowJobStatusOptions({ path: { runId: this.workflowRunId() } }),
    queryKey: getWorkflowJobStatusQueryKey({ path: { runId: this.workflowRunId() } }),
    retry: false,
  }));

  logs = computed<WorkflowRunLogsResponse | undefined>(() => this.logsQuery.data());
  jobs = computed<WorkflowJobDto[]>(() => this.jobsQuery.data()?.jobs ?? []);

  groups = computed<WorkflowRunLogGroupDto[]>(() => this.logs()?.groups ?? []);
  groupViews = computed<WorkflowRunLogGroupView[]>(() =>
    this.groups().map(group => ({
      group,
      job: this.findJobForGroup(group.name),
    }))
  );

  groupedFiles = computed(() =>
    this.groupViews().flatMap((groupView: WorkflowRunLogGroupView) =>
      groupView.group.files.map((file: WorkflowRunLogFileDto) => ({
        groupName: groupView.group.name,
        file,
      }))
    )
  );

  selectedFile = computed<SelectedWorkflowRunLogFile | null>(() => {
    const selectedFilePath = this.selectedFilePath();
    if (!selectedFilePath) {
      return this.groupedFiles()[0] ?? null;
    }

    return this.groupedFiles().find((entry: SelectedWorkflowRunLogFile) => entry.file.path === selectedFilePath) ?? this.groupedFiles()[0] ?? null;
  });

  hasLogs = computed(() => this.groupedFiles().length > 0);
  activeFilePath = computed(() => this.selectedFile()?.file.path ?? null);
  workflowOutcome = computed<WorkflowRunOutcome>(() => this.getWorkflowRunOutcome(this.logs()?.conclusion));
  selectedFileLines = computed<WorkflowRunLogLine[]>(() => {
    const content = this.selectedFile()?.file.content ?? '';

    return content.split(/\r?\n/).map((line, index) => ({
      number: index + 1,
      content: line,
    }));
  });

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
  }

  selectFile(path: string) {
    this.selectedFilePath.set(path);

    const selectedEntry = this.groupedFiles().find((entry: SelectedWorkflowRunLogFile) => entry.file.path === path);
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

  refreshLogs() {
    this.forceRefresh.set(true);
    this.logsQuery.refetch();
    this.jobsQuery.refetch();
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

  findJobForGroup(groupName: string): WorkflowJobDto | null {
    const normalizedGroupName = this.normalizeComparisonKey(groupName);
    if (!normalizedGroupName) {
      return null;
    }

    return (
      this.jobs().find(job => {
        const normalizedJobName = this.normalizeComparisonKey(job.name);
        return (
          !!normalizedJobName && (normalizedJobName === normalizedGroupName || normalizedJobName.includes(normalizedGroupName) || normalizedGroupName.includes(normalizedJobName))
        );
      }) ?? null
    );
  }

  getJobOutcome(conclusion?: WorkflowJobConclusion): WorkflowRunOutcome {
    return this.getOutcome(this.normalizeOutcomeConclusion(conclusion));
  }

  getSidebarOutcome(job: WorkflowJobDto | null): WorkflowRunOutcome {
    return job ? this.getJobOutcome(job.conclusion) : this.workflowOutcome();
  }

  getSidebarIconContainerClass(job: WorkflowJobDto | null): string {
    const outcome = this.getSidebarOutcome(job);

    if (outcome.icon === 'circle-check') {
      return 'bg-green-100 text-green-700 dark:bg-green-950/40 dark:text-green-300';
    }

    if (outcome.icon === 'circle-minus') {
      return 'bg-orange-100 text-orange-700 dark:bg-orange-950/40 dark:text-orange-300';
    }

    if (outcome.icon === 'circle-x') {
      return 'bg-red-100 text-red-700 dark:bg-red-950/40 dark:text-red-300';
    }

    return 'bg-surface-100 text-surface-600 dark:bg-surface-800 dark:text-surface-300';
  }

  getWorkflowRunOutcome(conclusion?: WorkflowRunConclusion): WorkflowRunOutcome {
    return this.getOutcome(conclusion);
  }

  private getOutcome(conclusion?: WorkflowRunConclusion): WorkflowRunOutcome {
    if (conclusion === 'SUCCESS') {
      return {
        icon: 'circle-check',
        iconColorClass: 'text-green-600',
        label: 'Success',
        tagSeverity: 'success',
      };
    }

    if (conclusion === 'CANCELLED') {
      return {
        icon: 'circle-minus',
        iconColorClass: 'text-orange-600',
        label: 'Cancelled',
        tagSeverity: 'warn',
      };
    }

    if (conclusion && ['ACTION_REQUIRED', 'FAILURE', 'STARTUP_FAILURE', 'TIMED_OUT'].includes(conclusion)) {
      return {
        icon: 'circle-x',
        iconColorClass: 'text-red-600',
        label: 'Failure',
        tagSeverity: 'danger',
      };
    }

    if (conclusion && ['NEUTRAL', 'SKIPPED', 'STALE'].includes(conclusion)) {
      return {
        icon: 'question-mark',
        iconColorClass: 'text-muted-color',
        label: 'Neutral',
        tagSeverity: 'secondary',
      };
    }

    return {
      icon: 'question-mark',
      iconColorClass: 'text-muted-color',
      label: 'Unknown',
      tagSeverity: 'secondary',
    };
  }

  private normalizeOutcomeConclusion(value?: string | null): WorkflowRunConclusion {
    switch (value?.toUpperCase()) {
      case 'SUCCESS':
      case 'CANCELLED':
      case 'ACTION_REQUIRED':
      case 'FAILURE':
      case 'STARTUP_FAILURE':
      case 'TIMED_OUT':
      case 'NEUTRAL':
      case 'SKIPPED':
      case 'STALE':
      case 'UNKNOWN':
        return value.toUpperCase() as WorkflowRunConclusion;
      default:
        return undefined;
    }
  }

  private normalizeComparisonKey(value?: string | null): string {
    return (value ?? '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .trim()
      .replace(/\s+/g, ' ');
  }
}
