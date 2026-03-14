import type { WorkflowJobDto, WorkflowRunLogFileDto, WorkflowRunLogGroupDto, WorkflowRunLogsResponse } from '@app/core/modules/openapi/types.gen';

type SelectedWorkflowRunLogFile = {
  groupName: string;
  file: WorkflowRunLogFileDto;
};

export type WorkflowRunLogLineTone = 'default' | 'error' | 'warning' | 'command' | 'group';

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

type WorkflowRunLogLineStats = {
  errorCount: number;
  warningCount: number;
};
type WorkflowRunConclusion = WorkflowRunLogsResponse['conclusion'];
type WorkflowJobConclusion = WorkflowJobDto['conclusion'];

type WorkflowRunOutcome = {
  icon: 'circle-check' | 'circle-minus' | 'circle-x' | 'question-mark';
  iconColorClass: string;
  label: string;
  tagSeverity: 'success' | 'warn' | 'danger' | 'secondary';
};

type WorkflowRunLogGroupView = {
  group: WorkflowRunLogGroupDto;
  job: WorkflowJobDto | null;
  outcome: WorkflowRunOutcome;
  iconContainerClass: string;
};

type SelectedWorkflowRunLogFileView = {
  selectedFile: SelectedWorkflowRunLogFile;
  lineStats: WorkflowRunLogLineStats;
  rows: WorkflowRunLogRow[];
  totalRowCount: number;
};

const ERROR_LOG_LINE_PATTERN = /^\[(?:error)]/i;
const WARNING_LOG_LINE_PATTERN = /^\[(?:warning|notice)]/i;
const GROUP_LOG_LINE_PATTERN = /^\[(?:group)]/i;
const GROUP_END_LOG_LINE_PATTERN = /^\[(?:endgroup)]/i;
const COMMAND_LOG_LINE_PATTERN = /^\[(?:command)]/i;

export const ALL_LOG_LINE_TONES: WorkflowRunLogLineTone[] = ['default', 'command', 'group', 'warning', 'error'];
export const LOG_LINE_FILTER_OPTIONS = [
  { tone: 'default', label: 'Default' },
  { tone: 'command', label: 'Commands' },
  { tone: 'warning', label: 'Warnings' },
  { tone: 'error', label: 'Errors' },
] as const satisfies ReadonlyArray<{ tone: WorkflowRunLogLineTone; label: string }>;
const ALL_LOG_LINE_TONE_SET: ReadonlySet<WorkflowRunLogLineTone> = new Set(ALL_LOG_LINE_TONES);
const DISABLED_LINE_TONE_FILTER_CLASS = 'border-slate-700 bg-slate-900 text-slate-500';
const ENABLED_LINE_TONE_FILTER_CLASSES: Record<WorkflowRunLogLineTone, string> = {
  default: 'border-slate-500 bg-slate-800 text-slate-100',
  command: 'border-blue-600 bg-blue-50 text-blue-900 dark:border-blue-700 dark:bg-blue-950/40 dark:text-blue-100',
  group: 'border-violet-700 bg-violet-950/40 text-violet-100',
  warning: 'border-amber-700 bg-amber-950/40 text-amber-100',
  error: 'border-rose-700 bg-rose-950/40 text-rose-100',
};

const OUTCOME_ICON_CONTAINER_CLASSES: Record<WorkflowRunOutcome['icon'], string> = {
  'circle-check': 'bg-green-100 text-green-700 dark:bg-green-950/40 dark:text-green-300',
  'circle-minus': 'bg-orange-100 text-orange-700 dark:bg-orange-950/40 dark:text-orange-300',
  'circle-x': 'bg-red-100 text-red-700 dark:bg-red-950/40 dark:text-red-300',
  'question-mark': 'bg-surface-100 text-surface-600 dark:bg-surface-800 dark:text-surface-300',
};

const SUCCESS_OUTCOME: WorkflowRunOutcome = {
  icon: 'circle-check',
  iconColorClass: 'text-green-600',
  label: 'Success',
  tagSeverity: 'success',
};

const CANCELLED_OUTCOME: WorkflowRunOutcome = {
  icon: 'circle-minus',
  iconColorClass: 'text-orange-600',
  label: 'Cancelled',
  tagSeverity: 'warn',
};

const FAILURE_OUTCOME: WorkflowRunOutcome = {
  icon: 'circle-x',
  iconColorClass: 'text-red-600',
  label: 'Failure',
  tagSeverity: 'danger',
};

const NEUTRAL_OUTCOME: WorkflowRunOutcome = {
  icon: 'question-mark',
  iconColorClass: 'text-muted-color',
  label: 'Neutral',
  tagSeverity: 'secondary',
};

const UNKNOWN_OUTCOME: WorkflowRunOutcome = {
  icon: 'question-mark',
  iconColorClass: 'text-muted-color',
  label: 'Unknown',
  tagSeverity: 'secondary',
};

const FAILURE_CONCLUSIONS = new Set<WorkflowRunConclusion>(['ACTION_REQUIRED', 'FAILURE', 'STARTUP_FAILURE', 'TIMED_OUT']);
const NEUTRAL_CONCLUSIONS = new Set<WorkflowRunConclusion>(['NEUTRAL', 'SKIPPED', 'STALE']);
const LINE_TONE_MATCHERS: ReadonlyArray<readonly [Exclude<WorkflowRunLogLineTone, 'default'>, RegExp]> = [
  ['error', ERROR_LOG_LINE_PATTERN],
  ['warning', WARNING_LOG_LINE_PATTERN],
  ['command', COMMAND_LOG_LINE_PATTERN],
  ['group', GROUP_LOG_LINE_PATTERN],
];

export function getWorkflowRunOutcome(conclusion?: WorkflowRunConclusion): WorkflowRunOutcome {
  if (conclusion === 'SUCCESS') {
    return SUCCESS_OUTCOME;
  }

  if (conclusion === 'CANCELLED') {
    return CANCELLED_OUTCOME;
  }

  if (conclusion && FAILURE_CONCLUSIONS.has(conclusion)) {
    return FAILURE_OUTCOME;
  }

  if (conclusion && NEUTRAL_CONCLUSIONS.has(conclusion)) {
    return NEUTRAL_OUTCOME;
  }

  return UNKNOWN_OUTCOME;
}

function getJobOutcome(conclusion?: WorkflowJobConclusion): WorkflowRunOutcome {
  return getWorkflowRunOutcome(normalizeOutcomeConclusion(conclusion));
}

export function buildGroupViews(groups: WorkflowRunLogGroupDto[], jobs: WorkflowJobDto[], workflowOutcome: WorkflowRunOutcome): WorkflowRunLogGroupView[] {
  return groups.map(group => {
    const job = findJobForGroup(group.name, jobs);
    const outcome = job ? getJobOutcome(job.conclusion) : workflowOutcome;

    return {
      group,
      job,
      outcome,
      iconContainerClass: OUTCOME_ICON_CONTAINER_CLASSES[outcome.icon],
    };
  });
}

export function buildSelectedFileView(
  selectedFile: SelectedWorkflowRunLogFile | null,
  enabledLineTones: ReadonlySet<WorkflowRunLogLineTone>,
  expandedLogGroupIds: Readonly<Record<string, boolean>>
): SelectedWorkflowRunLogFileView | null {
  if (!selectedFile) {
    return null;
  }

  const lines = buildLogLines(selectedFile.file.content);
  const entries = buildLogEntries(lines);

  return {
    selectedFile,
    lineStats: getLineStats(lines),
    rows: buildVisibleLogRows(entries, enabledLineTones, expandedLogGroupIds),
    totalRowCount: buildVisibleLogRows(entries, ALL_LOG_LINE_TONE_SET, expandedLogGroupIds, 0, true).length,
  };
}

export function getAutoExpandedLogGroupIds(content: string, tone: Extract<WorkflowRunLogLineTone, 'error' | 'warning' | 'command'>): Record<string, boolean> {
  const entries = buildLogEntries(buildLogLines(content));
  const expandedGroupIds = new Set<string>();
  collectGroupIdsContainingTone(entries, tone, expandedGroupIds);

  return Object.fromEntries(Array.from(expandedGroupIds, groupId => [groupId, true]));
}

export function getLineTone(content: string): WorkflowRunLogLineTone {
  const trimmedContent = content.trim();
  if (!trimmedContent) {
    return 'default';
  }

  for (const [tone, pattern] of LINE_TONE_MATCHERS) {
    if (pattern.test(trimmedContent)) {
      return tone;
    }
  }

  return 'default';
}

export function getRenderedLineContent(line: WorkflowRunLogLine): string {
  if (line.tone === 'group') {
    return getGroupTitle(line.content);
  }

  if (line.tone === 'command') {
    return line.content.trim().replace(COMMAND_LOG_LINE_PATTERN, '').trim();
  }

  return line.content;
}

function findJobForGroup(groupName: string, jobs: WorkflowJobDto[]): WorkflowJobDto | null {
  const normalizedGroupName = normalizeComparisonKey(groupName);
  if (!normalizedGroupName) {
    return null;
  }

  return (
    jobs.find(job => {
      const normalizedJobName = normalizeComparisonKey(job.name);
      return (
        !!normalizedJobName && (normalizedJobName === normalizedGroupName || normalizedJobName.includes(normalizedGroupName) || normalizedGroupName.includes(normalizedJobName))
      );
    }) ?? null
  );
}

export function getLineToneFilterClass(tone: WorkflowRunLogLineTone, isEnabled: boolean): string {
  return isEnabled ? ENABLED_LINE_TONE_FILTER_CLASSES[tone] : DISABLED_LINE_TONE_FILTER_CLASS;
}

function buildLogLines(content: string): WorkflowRunLogLine[] {
  if (!content) {
    return [];
  }

  return content.split(/\r?\n/).map((line, index) => ({
    number: index + 1,
    content: line,
    tone: getLineTone(line),
  }));
}

function getLineStats(lines: WorkflowRunLogLine[]): WorkflowRunLogLineStats {
  return lines.reduce(
    (stats, line) => ({
      errorCount: stats.errorCount + (line.tone === 'error' ? 1 : 0),
      warningCount: stats.warningCount + (line.tone === 'warning' ? 1 : 0),
    }),
    {
      errorCount: 0,
      warningCount: 0,
    }
  );
}

function buildLogEntries(lines: WorkflowRunLogLine[]): WorkflowRunLogEntry[] {
  const rootEntries: WorkflowRunLogEntry[] = [];
  const groupStack: Extract<WorkflowRunLogEntry, { type: 'group' }>[] = [];

  for (const line of lines) {
    if (isGroupEndLine(line.content)) {
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
        title: getGroupTitle(line.content),
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

function buildVisibleLogRows(
  entries: WorkflowRunLogEntry[],
  enabledLineTones: ReadonlySet<WorkflowRunLogLineTone>,
  expandedLogGroupIds: Readonly<Record<string, boolean>>,
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

    const isExpanded = expandAllGroups || !showGroups || expandedLogGroupIds[entry.id] === true;
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
      rows.push(...buildVisibleLogRows(entry.entries, enabledLineTones, expandedLogGroupIds, childLevel, expandAllGroups));
    }
  }

  return rows;
}

function collectGroupIdsContainingTone(
  entries: WorkflowRunLogEntry[],
  tone: Extract<WorkflowRunLogLineTone, 'error' | 'warning' | 'command'>,
  expandedGroupIds: Set<string>
): boolean {
  let containsTone = false;

  for (const entry of entries) {
    if (entry.type === 'line') {
      containsTone ||= entry.line.tone === tone;
      continue;
    }

    const childGroupContainsTone = collectGroupIdsContainingTone(entry.entries, tone, expandedGroupIds);
    if (childGroupContainsTone) {
      expandedGroupIds.add(entry.id);
      containsTone = true;
    }
  }

  return containsTone;
}

function normalizeOutcomeConclusion(value?: string | null): WorkflowRunConclusion {
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

function normalizeComparisonKey(value?: string | null): string {
  return (value ?? '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

function isGroupEndLine(content: string): boolean {
  return GROUP_END_LOG_LINE_PATTERN.test(content.trim());
}

function getGroupTitle(content: string): string {
  return content.trim().replace(GROUP_LOG_LINE_PATTERN, '').trim();
}
