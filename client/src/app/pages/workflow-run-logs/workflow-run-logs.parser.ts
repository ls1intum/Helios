export type WorkflowRunLogLineTone = 'default' | 'error' | 'warning' | 'command' | 'group';

export type WorkflowRunLogLine = {
  number: number;
  content: string;
  tone: WorkflowRunLogLineTone;
};

export type WorkflowRunLogEntry =
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

export type WorkflowRunLogRow =
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

export type WorkflowRunLogLineStats = {
  errorCount: number;
  warningCount: number;
};

const ERROR_LOG_LINE_PATTERN = /^\[(?:error)]/i;
const WARNING_LOG_LINE_PATTERN = /^\[(?:warning|notice)]/i;
const GROUP_LOG_LINE_PATTERN = /^\[(?:group)]/i;
const GROUP_END_LOG_LINE_PATTERN = /^\[(?:endgroup)]/i;
const COMMAND_LOG_LINE_PATTERN = /^\[(?:command)]/i;

const LINE_TONE_MATCHERS: ReadonlyArray<readonly [Exclude<WorkflowRunLogLineTone, 'default'>, RegExp]> = [
  ['error', ERROR_LOG_LINE_PATTERN],
  ['warning', WARNING_LOG_LINE_PATTERN],
  ['command', COMMAND_LOG_LINE_PATTERN],
  ['group', GROUP_LOG_LINE_PATTERN],
];

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

export function buildLogLines(content: string): WorkflowRunLogLine[] {
  if (!content) {
    return [];
  }

  return content.split(/\r?\n/).map((line, index) => ({
    number: index + 1,
    content: line,
    tone: getLineTone(line),
  }));
}

export function getLineStats(lines: WorkflowRunLogLine[]): WorkflowRunLogLineStats {
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

export function buildLogEntries(lines: WorkflowRunLogLine[]): WorkflowRunLogEntry[] {
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

export function buildVisibleLogRows(
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

    if (!showGroups) {
      // Groups hidden — just inline children when expanded
      if (isExpanded) {
        rows.push(...buildVisibleLogRows(entry.entries, enabledLineTones, expandedLogGroupIds, childLevel, expandAllGroups));
      }
      continue;
    }

    // Compute child rows first so we can skip groups with no matching content
    const childRows = isExpanded ? buildVisibleLogRows(entry.entries, enabledLineTones, expandedLogGroupIds, childLevel, expandAllGroups) : [];
    const hasMatchingContent = childRows.length > 0 || groupHasMatchingLines(entry, enabledLineTones);

    if (hasMatchingContent) {
      rows.push({
        type: 'group',
        level,
        id: entry.id,
        line: entry.headerLine,
        title: entry.title,
      });
      rows.push(...childRows);
    }
  }

  return rows;
}

export function collectGroupIdsContainingTone(
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

function groupHasMatchingLines(group: Extract<WorkflowRunLogEntry, { type: 'group' }>, enabledLineTones: ReadonlySet<WorkflowRunLogLineTone>): boolean {
  for (const entry of group.entries) {
    if (entry.type === 'line' && enabledLineTones.has(entry.line.tone)) {
      return true;
    }
    if (entry.type === 'group' && groupHasMatchingLines(entry, enabledLineTones)) {
      return true;
    }
  }
  return false;
}

function isGroupEndLine(content: string): boolean {
  return GROUP_END_LOG_LINE_PATTERN.test(content.trim());
}

function getGroupTitle(content: string): string {
  return content.trim().replace(GROUP_LOG_LINE_PATTERN, '').trim();
}
