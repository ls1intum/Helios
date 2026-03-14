import type { WorkflowRunLogGroupDto } from '@app/core/modules/openapi/types.gen';
import { describe, expect, it } from 'vitest';
import { buildFileViews, buildGroupViews } from './workflow-run-logs.utils';

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
});
