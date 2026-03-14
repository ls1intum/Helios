import type { WorkflowRunLogFileDto, WorkflowRunLogGroupDto, WorkflowRunLogsResponse } from '@app/core/modules/openapi/types.gen';
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

type WorkflowRunConclusion = WorkflowRunLogsResponse['conclusion'];

export type WorkflowRunOutcome = {
  icon: 'circle-check' | 'circle-minus' | 'circle-x' | 'question-mark';
  iconColorClass: string;
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

const CONCLUSION_OUTCOME_MAP: Partial<Record<NonNullable<WorkflowRunConclusion>, WorkflowRunOutcome>> = {
  SUCCESS: SUCCESS_OUTCOME,
  CANCELLED: CANCELLED_OUTCOME,
  ACTION_REQUIRED: FAILURE_OUTCOME,
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
      iconContainerClass: OUTCOME_ICON_CONTAINER_CLASSES[outcome.icon],
    };
  });
}

export function buildSelectedFileView(
  selectedFile: WorkflowRunLogFileView | null,
  enabledLineTones: ReadonlySet<WorkflowRunLogLineTone>,
  expandedLogGroupIds: Readonly<Record<string, boolean>>
): SelectedWorkflowRunLogFileView | null {
  if (!selectedFile) {
    return null;
  }

  const lines = buildLogLines(selectedFile.file.content);
  const entries = buildLogEntries(lines);
  const allRows = buildVisibleLogRows(entries, ALL_LOG_LINE_TONE_SET, expandedLogGroupIds, 0, true);

  return {
    selectedFile,
    lineStats: getLineStats(lines),
    rows: buildVisibleLogRows(entries, enabledLineTones, expandedLogGroupIds),
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
        iconContainerClass: OUTCOME_ICON_CONTAINER_CLASSES[outcome.icon],
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

export function getLineToneFilterClass(tone: WorkflowRunLogLineTone, isEnabled: boolean): string {
  return isEnabled ? ENABLED_LINE_TONE_FILTER_CLASSES[tone] : DISABLED_LINE_TONE_FILTER_CLASS;
}

function normalizeOutcomeConclusion(value?: string | null): WorkflowRunConclusion {
  const upper = value?.toUpperCase();
  return upper && VALID_CONCLUSION_VALUES.has(upper) ? (upper as WorkflowRunConclusion) : undefined;
}
