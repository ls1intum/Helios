import type { WorkflowRunLogGroupDto } from '@app/core/modules/openapi/types.gen';
import { describe, expect, it } from 'vitest';
import {
  LOG_VIEW_CLASSES,
  buildFileViews,
  buildGroupViews,
  getEnabledTonesForLevel,
  getLineToneBadgeClass,
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

  it('returns all five tones for the "all" log level filter', () => {
    const tones = getEnabledTonesForLevel('all');
    expect(tones).toContain('default');
    expect(tones).toContain('command');
    expect(tones).toContain('group');
    expect(tones).toContain('warning');
    expect(tones).toContain('error');
    expect(tones.size).toBe(5);
  });

  it('returns only warning, error, and group tones for the "warnings" log level filter', () => {
    const tones = getEnabledTonesForLevel('warnings');
    expect(tones).toContain('warning');
    expect(tones).toContain('error');
    expect(tones).toContain('group');
    expect(tones).not.toContain('default');
    expect(tones).not.toContain('command');
    expect(tones.size).toBe(3);
  });

  it('returns only error and group tones for the "errors" log level filter', () => {
    const tones = getEnabledTonesForLevel('errors');
    expect(tones).toContain('error');
    expect(tones).toContain('group');
    expect(tones).not.toContain('default');
    expect(tones).not.toContain('command');
    expect(tones).not.toContain('warning');
    expect(tones.size).toBe(2);
  });

  it('uses the same orange and red families as the rest of the app for warnings and errors', () => {
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

  it('keeps the grouped rows on the same purple family', () => {
    const groupRowClass = getLogGroupRowClass();
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
