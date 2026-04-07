import type { WorkflowRunLogGroupDto } from '@app/core/modules/openapi/types.gen';
import { describe, expect, it } from 'vitest';
import {
  LOG_VIEW_CLASSES,
  buildFileViews,
  buildGroupViews,
  getLineToneBadgeClass,
  getLineToneFilterClass,
  getLineToneRowClass,
  getLineToneTextClass,
  getLogGroupRowClass,
  getWorkflowRunOutcome,
} from './workflow-run-logs.utils';

describe('workflow-run-logs utils', () => {
  function createGroup(overrides: Partial<WorkflowRunLogGroupDto> = {}): WorkflowRunLogGroupDto {
    return {
      name: 'build',
      steps: [],
      files: [
        {
          path: 'build/system.txt',
          displayName: 'system',
          content: 'log content',
        },
      ],
      ...overrides,
    };
  }

  it('uses backend job conclusion for group status', () => {
    const groupViews = buildGroupViews([createGroup({ jobConclusion: 'success' })]);

    expect(groupViews[0].outcome).toMatchObject({
      icon: 'circle-check',
      label: 'Success',
    });
  });

  it('keeps groups without backend job metadata unknown', () => {
    const groupViews = buildGroupViews([createGroup()]);

    expect(groupViews[0].outcome).toMatchObject({
      icon: 'question-mark',
      label: 'Unknown',
    });
  });

  it('uses backend step conclusion for file status', () => {
    const fileViews = buildFileViews(
      buildGroupViews([
        createGroup({
          files: [
            {
              path: 'build/2_test.txt',
              displayName: 'test',
              stepNumber: 2,
              stepName: 'Test',
              stepStatus: 'completed',
              stepConclusion: 'failure',
              content: 'log content',
            },
          ],
        }),
      ])
    );

    expect(fileViews[0].outcome).toMatchObject({
      icon: 'circle-x',
      label: 'Failure',
    });
  });

  it('keeps files without backend step metadata unknown', () => {
    const fileViews = buildFileViews(buildGroupViews([createGroup()]));

    expect(fileViews[0].outcome).toMatchObject({
      icon: 'question-mark',
      label: 'Unknown',
    });
  });

  it('keeps purple group filters in light mode and switches away from purple in dark mode', () => {
    const filterClass = getLineToneFilterClass('group', true);

    expect(filterClass).toContain('border-violet-200');
    expect(filterClass).toContain('bg-violet-50');
    expect(filterClass).toContain('dark:border-violet-500');
    expect(filterClass).toContain('dark:bg-violet-900/30');
  });

  it('uses a higher-contrast blue for command filters in dark mode', () => {
    const filterClass = getLineToneFilterClass('command', true);

    expect(filterClass).toContain('border-primary-200');
    expect(filterClass).toContain('bg-primary-50');
    expect(filterClass).toContain('dark:border-primary-500');
    expect(filterClass).toContain('dark:bg-primary-900/30');
  });

  it('uses the same orange and red families as the rest of the app for warnings and errors', () => {
    expect(getLineToneFilterClass('warning', true)).toContain('orange');
    expect(getLineToneFilterClass('warning', true)).not.toContain('amber');
    expect(getLineToneFilterClass('error', true)).toContain('red');
    expect(getLineToneFilterClass('error', true)).not.toContain('rose');
    expect(getLineToneBadgeClass('warning')).toContain('bg-orange-50');
    expect(getLineToneBadgeClass('error')).toContain('bg-red-100');
    expect(getLineToneRowClass('warning')).toContain('border-l-orange-500');
    expect(getLineToneRowClass('error')).toContain('border-l-red-500');
    expect(getLineToneTextClass('warning')).toContain('text-orange-900');
    expect(getLineToneTextClass('error')).toContain('text-red-900');
  });

  it('uses the same surface palette for the log chrome and default lines', () => {
    expect(LOG_VIEW_CLASSES.container).toContain('border-surface-200');
    expect(LOG_VIEW_CLASSES.container).not.toContain('slate');
    expect(LOG_VIEW_CLASSES.toolbar).toContain('bg-surface-0/95');
    expect(getLineToneRowClass('default')).toContain('bg-surface-0');
    expect(getLineToneTextClass('default')).toContain('text-surface-900');
  });

  it('keeps the grouped rows on the same purple family as the group filter', () => {
    const groupFilterClass = getLineToneFilterClass('group', true);
    const groupRowClass = getLogGroupRowClass();

    expect(groupFilterClass).toContain('violet');
    expect(groupRowClass).toContain('violet');
  });

  it('uses dark-mode-aware icon colors for workflow outcomes', () => {
    expect(getWorkflowRunOutcome('SUCCESS').iconColorClass).toContain('dark:text-green-400');
    expect(getWorkflowRunOutcome('FAILURE').iconColorClass).toContain('dark:text-red-400');
    expect(getWorkflowRunOutcome('CANCELLED').iconColorClass).toContain('dark:text-surface-400');
    expect(getWorkflowRunOutcome('ACTION_REQUIRED').iconColorClass).toContain('dark:text-orange-400');
    expect(getWorkflowRunOutcome('SUCCESS').badgeClass).toContain('bg-green-100');
    expect(getWorkflowRunOutcome('FAILURE').badgeClass).toContain('bg-red-100');
    expect(getWorkflowRunOutcome('CANCELLED').badgeClass).toContain('bg-surface-100');
    expect(getWorkflowRunOutcome('ACTION_REQUIRED').badgeClass).toContain('bg-orange-50');
  });
});
