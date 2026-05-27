import type { WorkflowRunLogFileDto, WorkflowRunLogGroupDto, WorkflowRunLogsResponse } from '@app/core/modules/openapi/types.gen';
import { getStatusColors } from '@app/core/utils/status-colors';
import {
  buildLogEntries,
  buildLogLines,
  buildVisibleLogRows,
  collectGroupIdsContainingTone,
  getLineStats,
  type WorkflowRunLogLineTone,
  type WorkflowRunLogLineStats,
  type WorkflowRunLogRow,
} from './workflow-run-logs.parser';

export type { WorkflowRunLogLineTone } from './workflow-run-logs.parser';
export { getLineTone, getRenderedLineContent } from './workflow-run-logs.parser';

export type LogLevelFilter = 'all' | 'warnings' | 'errors';

export type WorkflowRunLogFileView = {
  groupView: WorkflowRunLogGroupView;
  file: WorkflowRunLogFileDto;
  outcome: WorkflowRunOutcome;
  iconContainerClass: string;
};

type SelectedWorkflowRunLogFileView = {
  selectedFile: WorkflowRunLogFileView;
  lineStats: WorkflowRunLogLineStats;
  rows: WorkflowRunLogRow[];
  totalRowCount: number;
};

const BADGE_BASE_CLASS = 'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold';

export const ALL_LOG_LINE_TONES: WorkflowRunLogLineTone[] = ['default', 'command', 'group', 'warning', 'error'];

export const LOG_LEVEL_FILTER_OPTIONS: { label: string; value: LogLevelFilter }[] = [
  { label: 'All Logs', value: 'all' },
  { label: 'Warnings & Errors', value: 'warnings' },
  { label: 'Errors Only', value: 'errors' },
];

const ALL_LOG_LINE_TONE_SET: ReadonlySet<WorkflowRunLogLineTone> = new Set(ALL_LOG_LINE_TONES);
const WARNINGS_AND_ERRORS_TONE_SET: ReadonlySet<WorkflowRunLogLineTone> = new Set<WorkflowRunLogLineTone>(['warning', 'error', 'group']);
const ERRORS_ONLY_TONE_SET: ReadonlySet<WorkflowRunLogLineTone> = new Set<WorkflowRunLogLineTone>(['error', 'group']);

export function getEnabledTonesForLevel(level: LogLevelFilter): ReadonlySet<WorkflowRunLogLineTone> {
  switch (level) {
    case 'warnings':
      return WARNINGS_AND_ERRORS_TONE_SET;
    case 'errors':
      return ERRORS_ONLY_TONE_SET;
    default:
      return ALL_LOG_LINE_TONE_SET;
  }
}
const LOG_LINE_TONE_STYLES: Record<
  WorkflowRunLogLineTone,
  {
    filterClass: string;
    rowClass: string;
    textClass: string;
  }
> = {
  default: {
    filterClass: 'border-surface-400 bg-surface-100 text-surface-900 dark:border-surface-600 dark:bg-surface-800 dark:text-surface-50',
    rowClass: 'bg-surface-0 border-l-transparent dark:bg-surface-900/60',
    textClass: 'text-surface-900 dark:text-surface-50',
  },
  command: {
    filterClass: 'border-primary-200 bg-primary-50 text-primary-900 dark:border-primary-500 dark:bg-primary-900/30 dark:text-primary-50',
    rowClass: 'bg-primary-50 border-l-primary-400 dark:border-l-primary-300 dark:bg-primary-900/30',
    textClass: 'text-primary-900 dark:text-primary-50',
  },
  group: {
    filterClass: 'border-violet-200 bg-violet-50 text-violet-950 dark:border-violet-500 dark:bg-violet-900/30 dark:text-violet-50',
    rowClass:
      'border-b border-violet-100 border-l-2 border-violet-500 bg-violet-50 hover:bg-violet-100 dark:border-b-violet-950/40 dark:border-violet-400 dark:bg-violet-900/30 dark:hover:bg-violet-800/40',
    textClass: 'text-violet-950 dark:text-violet-50',
  },
  warning: {
    filterClass: 'border-orange-200 bg-orange-50 text-orange-900 dark:border-orange-700 dark:bg-orange-900/30 dark:text-orange-100',
    rowClass: 'bg-orange-50 border-l-orange-500 dark:bg-orange-900/25',
    textClass: 'text-orange-900 dark:text-orange-100',
  },
  error: {
    filterClass: 'border-red-200 bg-red-50 text-red-900 dark:border-red-700 dark:bg-red-900/30 dark:text-red-100',
    rowClass: 'bg-red-50 border-l-red-500 dark:bg-red-900/25',
    textClass: 'text-red-900 dark:text-red-100',
  },
};

export const LOG_VIEW_CLASSES = {
  container: 'max-h-[70vh] overflow-auto rounded-lg border border-surface-200 bg-surface-0 dark:border-surface-800 dark:bg-surface-950',
  toolbar:
    'sticky top-0 z-10 flex flex-wrap gap-2 border-b border-surface-200 bg-surface-0/95 px-4 py-3 text-xs text-surface-600 backdrop-blur dark:border-surface-800 dark:bg-surface-950/95 dark:text-surface-300',
  content: 'font-mono text-sm leading-6 text-surface-900 dark:text-surface-50',
  emptyState: 'px-4 py-6 text-sm text-surface-500 dark:text-surface-400',
  baseRow: 'grid grid-cols-[4rem_minmax(0,1fr)] gap-4 border-b border-surface-100 px-4 dark:border-surface-900',
  lineNumber: 'select-none border-r border-surface-200 py-0.5 pr-4 text-right text-surface-500 dark:border-surface-800 dark:text-surface-500',
  groupRow: 'grid w-full grid-cols-[4rem_minmax(0,1fr)] gap-4 px-4 text-left transition',
} as const;

const LINE_TONE_BADGE_CLASSES = {
  warning: `${BADGE_BASE_CLASS} ${getStatusColors('action_required').badge}`,
  error: `${BADGE_BASE_CLASS} ${getStatusColors('failure').badge}`,
} as const;

type WorkflowRunConclusion = WorkflowRunLogsResponse['conclusion'];

export type WorkflowRunOutcome = {
  icon: 'circle-check' | 'circle-minus' | 'circle-x' | 'question-mark';
  iconContainerClass: string;
  iconColorClass: string;
  badgeClass: string;
  label: string;
  tagSeverity: 'success' | 'warn' | 'danger' | 'secondary';
};

export type WorkflowRunLogGroupView = {
  group: WorkflowRunLogGroupDto;
  outcome: WorkflowRunOutcome;
  iconContainerClass: string;
};

const SUCCESS_OUTCOME: WorkflowRunOutcome = {
  icon: 'circle-check',
  iconContainerClass: getStatusColors('success').badge,
  iconColorClass: getStatusColors('success').icon,
  badgeClass: `${BADGE_BASE_CLASS} ${getStatusColors('success').badge}`,
  label: 'Success',
  tagSeverity: 'success',
};

const CANCELLED_OUTCOME: WorkflowRunOutcome = {
  icon: 'circle-minus',
  iconContainerClass: getStatusColors('cancelled').badge,
  iconColorClass: getStatusColors('cancelled').icon,
  badgeClass: `${BADGE_BASE_CLASS} ${getStatusColors('cancelled').badge}`,
  label: 'Cancelled',
  tagSeverity: 'secondary',
};

const WARNING_OUTCOME: WorkflowRunOutcome = {
  icon: 'circle-minus',
  iconContainerClass: getStatusColors('action_required').badge,
  iconColorClass: getStatusColors('action_required').icon,
  badgeClass: `${BADGE_BASE_CLASS} ${getStatusColors('action_required').badge}`,
  label: 'Action Required',
  tagSeverity: 'warn',
};

const FAILURE_OUTCOME: WorkflowRunOutcome = {
  icon: 'circle-x',
  iconContainerClass: getStatusColors('failure').badge,
  iconColorClass: getStatusColors('failure').icon,
  badgeClass: `${BADGE_BASE_CLASS} ${getStatusColors('failure').badge}`,
  label: 'Failure',
  tagSeverity: 'danger',
};

const NEUTRAL_OUTCOME: WorkflowRunOutcome = {
  icon: 'question-mark',
  iconContainerClass: getStatusColors('cancelled').badge,
  iconColorClass: getStatusColors('cancelled').icon,
  badgeClass: `${BADGE_BASE_CLASS} ${getStatusColors('cancelled').badge}`,
  label: 'Neutral',
  tagSeverity: 'secondary',
};

const UNKNOWN_OUTCOME: WorkflowRunOutcome = {
  icon: 'question-mark',
  iconContainerClass: getStatusColors().badge,
  iconColorClass: getStatusColors().icon,
  badgeClass: `${BADGE_BASE_CLASS} ${getStatusColors().badge}`,
  label: 'Unknown',
  tagSeverity: 'secondary',
};

const CONCLUSION_OUTCOME_MAP: Partial<Record<NonNullable<WorkflowRunConclusion>, WorkflowRunOutcome>> = {
  SUCCESS: SUCCESS_OUTCOME,
  CANCELLED: CANCELLED_OUTCOME,
  ACTION_REQUIRED: WARNING_OUTCOME,
  FAILURE: FAILURE_OUTCOME,
  STARTUP_FAILURE: FAILURE_OUTCOME,
  TIMED_OUT: FAILURE_OUTCOME,
  NEUTRAL: NEUTRAL_OUTCOME,
  SKIPPED: NEUTRAL_OUTCOME,
  STALE: NEUTRAL_OUTCOME,
};

const VALID_CONCLUSION_VALUES = new Set(['SUCCESS', 'CANCELLED', 'ACTION_REQUIRED', 'FAILURE', 'STARTUP_FAILURE', 'TIMED_OUT', 'NEUTRAL', 'SKIPPED', 'STALE', 'UNKNOWN']);

export function getWorkflowRunOutcome(conclusion?: WorkflowRunConclusion): WorkflowRunOutcome {
  return (conclusion && CONCLUSION_OUTCOME_MAP[conclusion]) || UNKNOWN_OUTCOME;
}

export function getJobOutcome(conclusion?: string | null): WorkflowRunOutcome {
  return getWorkflowRunOutcome(normalizeOutcomeConclusion(conclusion));
}

export function buildGroupViews(groups: WorkflowRunLogGroupDto[]): WorkflowRunLogGroupView[] {
  return groups.map(group => {
    const outcome = group.jobConclusion ? getJobOutcome(group.jobConclusion) : UNKNOWN_OUTCOME;

    return {
      group,
      outcome,
      iconContainerClass: outcome.iconContainerClass,
    };
  });
}

export function buildSelectedFileView(
  selectedFile: WorkflowRunLogFileView | null,
  logLevelFilter: LogLevelFilter,
  expandedLogGroupIds: Readonly<Record<string, boolean>>
): SelectedWorkflowRunLogFileView | null {
  if (!selectedFile) {
    return null;
  }

  const lines = buildLogLines(selectedFile.file.content);
  const entries = buildLogEntries(lines);
  const allRows = buildVisibleLogRows(entries, ALL_LOG_LINE_TONE_SET, expandedLogGroupIds, 0, true);
  const enabledTones = getEnabledTonesForLevel(logLevelFilter);

  return {
    selectedFile,
    lineStats: getLineStats(lines),
    rows: buildVisibleLogRows(entries, enabledTones, expandedLogGroupIds),
    totalRowCount: allRows.length,
  };
}

export function buildFileViews(groupViews: WorkflowRunLogGroupView[]): WorkflowRunLogFileView[] {
  return groupViews.flatMap(groupView =>
    groupView.group.files.map((file: WorkflowRunLogFileDto) => {
      const outcome = file.stepConclusion ? getJobOutcome(file.stepConclusion) : UNKNOWN_OUTCOME;

      return {
        groupView,
        file,
        outcome,
        iconContainerClass: outcome.iconContainerClass,
      };
    })
  );
}

export function getAutoExpandedLogGroupIds(content: string, tone: Extract<WorkflowRunLogLineTone, 'error' | 'warning' | 'command'>): Record<string, boolean> {
  const entries = buildLogEntries(buildLogLines(content));
  const expandedGroupIds = new Set<string>();
  collectGroupIdsContainingTone(entries, tone, expandedGroupIds);

  return Object.fromEntries(Array.from(expandedGroupIds, groupId => [groupId, true]));
}

export function getLineToneRowClass(tone: WorkflowRunLogLineTone): string {
  return `${LOG_VIEW_CLASSES.baseRow} ${LOG_LINE_TONE_STYLES[tone].rowClass}`;
}

export function getLineToneTextClass(tone: WorkflowRunLogLineTone): string {
  return LOG_LINE_TONE_STYLES[tone].textClass;
}

export function getLogGroupRowClass(): string {
  return `${LOG_VIEW_CLASSES.groupRow} ${LOG_LINE_TONE_STYLES.group.rowClass}`;
}

export function getLogGroupTextClass(): string {
  return LOG_LINE_TONE_STYLES.group.textClass;
}

export function getLineToneBadgeClass(tone: Extract<WorkflowRunLogLineTone, 'warning' | 'error'>): string {
  return LINE_TONE_BADGE_CLASSES[tone];
}

function normalizeOutcomeConclusion(value?: string | null): WorkflowRunConclusion {
  const upper = value?.toUpperCase();
  return upper && VALID_CONCLUSION_VALUES.has(upper) ? (upper as WorkflowRunConclusion) : undefined;
}
