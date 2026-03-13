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
import { QueryClient, injectQuery } from '@tanstack/angular-query-experimental';
import { AccordionModule } from 'primeng/accordion';
import { Button } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { Divider } from 'primeng/divider';
import { MessageModule } from 'primeng/message';
import { Panel } from 'primeng/panel';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { Toolbar } from 'primeng/toolbar';
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

type SelectedWorkflowRunLogFile = {
  groupName: string;
  file: WorkflowRunLogFileDto;
};

type WorkflowRunLogLine = {
  number: number;
  content: string;
  tone: WorkflowRunLogLineTone;
};

type WorkflowRunLogEntry =
  | {
      type: 'line';
      line: WorkflowRunLogLine;
    }
  | {
      type: 'group';
      id: string;
      headerLine: WorkflowRunLogLine;
      title: string;
      entries: WorkflowRunLogEntry[];
    };

type WorkflowRunLogRow =
  | {
      type: 'line';
      level: number;
      line: WorkflowRunLogLine;
    }
  | {
      type: 'group';
      level: number;
      id: string;
      line: WorkflowRunLogLine;
      title: string;
    };

type WorkflowRunLogLineTone = 'default' | 'error' | 'warning' | 'command' | 'group';

type WorkflowRunLogLineStats = {
  errorCount: number;
  warningCount: number;
};

type WorkflowRunLogLineFilterOption = {
  tone: WorkflowRunLogLineTone;
  label: string;
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

const ERROR_LOG_LINE_PATTERN = /^\[(?:error)]/i;
const WARNING_LOG_LINE_PATTERN = /^\[(?:warning|notice)]/i;
const GROUP_LOG_LINE_PATTERN = /^\[(?:group)]/i;
const GROUP_END_LOG_LINE_PATTERN = /^\[(?:endgroup)]/i;
const COMMAND_LOG_LINE_PATTERN = /^\[(?:command)]/i;
const ALL_LOG_LINE_TONES: WorkflowRunLogLineTone[] = ['default', 'command', 'group', 'warning', 'error'];
const LOG_LINE_FILTER_OPTIONS: WorkflowRunLogLineFilterOption[] = [
  { tone: 'default', label: 'Default' },
  { tone: 'command', label: 'Commands' },
  { tone: 'warning', label: 'Warnings' },
  { tone: 'error', label: 'Errors' },
];

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
  lineFilterOptions = LOG_LINE_FILTER_OPTIONS;

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
    if (content.length === 0) {
      return [];
    }

    return content.split(/\r?\n/).map((line, index) => ({
      number: index + 1,
      content: line,
      tone: this.getLineTone(line),
    }));
  });
  selectedFileLineStats = computed<WorkflowRunLogLineStats>(() =>
    this.selectedFileLines().reduce(
      (stats, line) => ({
        errorCount: stats.errorCount + (line.tone === 'error' ? 1 : 0),
        warningCount: stats.warningCount + (line.tone === 'warning' ? 1 : 0),
      }),
      {
        errorCount: 0,
        warningCount: 0,
      }
    )
  );
  selectedFileEntries = computed<WorkflowRunLogEntry[]>(() => this.buildLogEntries(this.selectedFileLines()));
  visibleSelectedFileRows = computed<WorkflowRunLogRow[]>(() => this.buildVisibleLogRows(this.selectedFileEntries(), new Set(this.enabledLineTones())));
  selectedFileTotalRowCount = computed<number>(() => this.buildVisibleLogRows(this.selectedFileEntries(), new Set(ALL_LOG_LINE_TONES), 0, true).length);

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

  toggleLineTone(tone: WorkflowRunLogLineTone) {
    this.enabledLineTones.update(enabledLineTones =>
      enabledLineTones.includes(tone)
        ? enabledLineTones.filter(enabledTone => enabledTone !== tone)
        : [...enabledLineTones, tone]
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

  getLineTone(content: string): WorkflowRunLogLineTone {
    const trimmedContent = content.trim();
    if (!trimmedContent) {
      return 'default';
    }

    if (ERROR_LOG_LINE_PATTERN.test(trimmedContent)) {
      return 'error';
    }

    if (WARNING_LOG_LINE_PATTERN.test(trimmedContent)) {
      return 'warning';
    }

    if (COMMAND_LOG_LINE_PATTERN.test(trimmedContent)) {
      return 'command';
    }

    if (GROUP_LOG_LINE_PATTERN.test(trimmedContent)) {
      return 'group';
    }

    return 'default';
  }

  getRenderedLineContent(line: WorkflowRunLogLine): string {
    if (line.tone === 'group') {
      return this.getGroupTitle(line.content);
    }

    if (line.tone === 'command') {
      return line.content.trim().replace(COMMAND_LOG_LINE_PATTERN, '').trim();
    }

    return line.content;
  }

  getLineToneFilterClass(tone: WorkflowRunLogLineTone): string {
    const isEnabled = this.isLineToneEnabled(tone);

    switch (tone) {
      case 'command':
        return isEnabled
          ? 'border-blue-600 bg-blue-50 text-blue-900 dark:border-blue-700 dark:bg-blue-950/40 dark:text-blue-100'
          : 'border-slate-700 bg-slate-900 text-slate-500';
      case 'group':
        return isEnabled
          ? 'border-violet-700 bg-violet-950/40 text-violet-100'
          : 'border-slate-700 bg-slate-900 text-slate-500';
      case 'warning':
        return isEnabled
          ? 'border-amber-700 bg-amber-950/40 text-amber-100'
          : 'border-slate-700 bg-slate-900 text-slate-500';
      case 'error':
        return isEnabled
          ? 'border-rose-700 bg-rose-950/40 text-rose-100'
          : 'border-slate-700 bg-slate-900 text-slate-500';
      default:
        return isEnabled
          ? 'border-slate-500 bg-slate-800 text-slate-100'
          : 'border-slate-700 bg-slate-900 text-slate-500';
    }
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

  private buildLogEntries(lines: WorkflowRunLogLine[]): WorkflowRunLogEntry[] {
    const rootEntries: WorkflowRunLogEntry[] = [];
    const groupStack: Extract<WorkflowRunLogEntry, { type: 'group' }>[] = [];

    for (const line of lines) {
      if (this.isGroupEndLine(line.content)) {
        if (groupStack.length > 0) {
          groupStack.pop();
        }
        continue;
      }

      const currentGroup = groupStack[groupStack.length - 1];
      const targetEntries = currentGroup?.entries ?? rootEntries;
      if (line.tone === 'group') {
        const groupEntry: Extract<WorkflowRunLogEntry, { type: 'group' }> = {
          type: 'group',
          id: `group-${line.number}`,
          headerLine: line,
          title: this.getGroupTitle(line.content),
          entries: [],
        };
        targetEntries.push(groupEntry);
        groupStack.push(groupEntry);
        continue;
      }

      targetEntries.push({
        type: 'line',
        line,
      });
    }

    return rootEntries;
  }

  private buildVisibleLogRows(
    entries: WorkflowRunLogEntry[],
    enabledLineTones: ReadonlySet<WorkflowRunLogLineTone>,
    level = 0,
    expandAllGroups = false
  ): WorkflowRunLogRow[] {
    const rows: WorkflowRunLogRow[] = [];
    const showGroups = enabledLineTones.has('group');

    for (const entry of entries) {
      if (entry.type === 'line') {
        if (enabledLineTones.has(entry.line.tone)) {
          rows.push({
            type: 'line',
            level,
            line: entry.line,
          });
        }
        continue;
      }

      const isExpanded = expandAllGroups || !showGroups || this.isLogGroupExpanded(entry.id);
      const childLevel = showGroups ? level + 1 : level;

      if (showGroups) {
        rows.push({
          type: 'group',
          level,
          id: entry.id,
          line: entry.headerLine,
          title: entry.title,
        });
      }

      if (isExpanded) {
        rows.push(...this.buildVisibleLogRows(entry.entries, enabledLineTones, childLevel, expandAllGroups));
      }
    }

    return rows;
  }

  private isGroupEndLine(content: string): boolean {
    return GROUP_END_LOG_LINE_PATTERN.test(content.trim());
  }

  private getGroupTitle(content: string): string {
    return content.trim().replace(GROUP_LOG_LINE_PATTERN, '').trim();
  }
}
