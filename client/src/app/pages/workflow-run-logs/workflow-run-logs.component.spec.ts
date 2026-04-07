import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom } from '@angular/core';
import { getWorkflowRunLogsQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import * as workflowRunLogsApi from '@app/core/modules/openapi/sdk.gen';
import type { WorkflowRunLogsResponse } from '@app/core/modules/openapi/types.gen';
import { TestModule } from '@app/test.module';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkflowRunLogsComponent } from './workflow-run-logs.component';
import { getAutoExpandedLogGroupIds } from './workflow-run-logs.utils';

describe('Integration Test Workflow Run Logs Page', () => {
  let component: WorkflowRunLogsComponent;
  let fixture: ComponentFixture<WorkflowRunLogsComponent>;
  let queryClient: QueryClient;

  const workflowRunId = 42;
  const repositoryId = 1;

  function createLogsResponse(overrides: Partial<WorkflowRunLogsResponse> = {}): WorkflowRunLogsResponse {
    return {
      workflowRunId,
      workflowName: 'deploy',
      displayTitle: 'Deploy preview',
      conclusion: 'FAILURE',
      htmlUrl: 'https://github.com/example/repo/actions/runs/42',
      cacheHit: true,
      downloadedAt: '2026-03-14T10:00:00Z',
      totalFileCount: 3,
      groups: [
        {
          name: 'build',
          jobName: 'build',
          jobStatus: 'completed',
          jobConclusion: 'success',
          steps: [
            {
              number: 1,
              name: 'Checkout',
              status: 'completed',
              conclusion: 'success',
              startedAt: '2026-03-14T10:00:00Z',
              completedAt: '2026-03-14T10:00:30Z',
            },
            {
              number: 2,
              name: 'Test',
              status: 'completed',
              conclusion: 'failure',
              startedAt: '2026-03-14T10:00:30Z',
              completedAt: '2026-03-14T10:02:00Z',
            },
          ],
          files: [
            {
              path: 'build/1_build.txt',
              displayName: 'build',
              stepNumber: 1,
              stepName: 'Checkout',
              stepStatus: 'completed',
              stepConclusion: 'success',
              stepStartedAt: '2026-03-14T10:00:00Z',
              stepCompletedAt: '2026-03-14T10:00:30Z',
              content: '[group]Build\n[error]Process completed with exit code 1\n[endgroup]',
            },
            {
              path: 'build/2_test.txt',
              displayName: 'test',
              stepNumber: 2,
              stepName: 'Test',
              stepStatus: 'completed',
              stepConclusion: 'failure',
              stepStartedAt: '2026-03-14T10:00:30Z',
              stepCompletedAt: '2026-03-14T10:02:00Z',
              content: '[error]Tests failed',
            },
          ],
        },
        {
          name: 'deploy',
          steps: [],
          files: [
            {
              path: 'deploy/system.txt',
              displayName: 'system',
              content: 'Post job cleanup.',
            },
          ],
        },
      ],
      ...overrides,
    };
  }

  async function createComponent({
    logs = createLogsResponse(),
  }: {
    logs?: WorkflowRunLogsResponse;
  } = {}) {
    queryClient.setQueryData(getWorkflowRunLogsQueryKey({ path: { workflowRunId } }), logs);

    fixture = TestBed.createComponent(WorkflowRunLogsComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('repositoryId', repositoryId);
    fixture.componentRef.setInput('workflowRunId', workflowRunId);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunLogsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();
    queryClient = TestBed.inject(QueryClient);
    queryClient.clear();
    await createComponent();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should return a success icon for successful workflow runs', () => {
    expect(component.getWorkflowRunOutcome('SUCCESS')).toMatchObject({
      icon: 'circle-check',
      label: 'Success',
    });
  });

  it('should return a failure icon for failed workflow runs', () => {
    expect(component.getWorkflowRunOutcome('FAILURE')).toMatchObject({
      icon: 'circle-x',
      label: 'Failure',
    });
  });

  it('should return a cancelled icon for cancelled workflow runs', () => {
    expect(component.getWorkflowRunOutcome('CANCELLED')).toMatchObject({
      icon: 'circle-minus',
      label: 'Cancelled',
    });
  });

  it('should classify normalized GitHub error lines for log highlighting', () => {
    expect(component.getLineTone('[error]Process completed with exit code 1')).toBe('error');
  });

  it('should classify only explicit GitHub square bracket markers', () => {
    expect(component.getLineTone('[command]/usr/bin/git status')).toBe('command');
    expect(component.getLineTone('[warning]Using a deprecated flag')).toBe('warning');
    expect(component.getLineTone('[group]Run actions/checkout@v4')).toBe('group');
    expect(component.getLineTone('Post job cleanup.')).toBe('default');
    expect(component.getLineTone('Complete job name: build')).toBe('default');
  });

  it('should render group headers without the GitHub marker prefix', () => {
    expect(
      component.getRenderedLineContent({
        number: 1,
        content: '[group]Runner Image Provisioner',
        tone: 'group',
      })
    ).toBe('Runner Image Provisioner');
  });

  it('should render command lines without the GitHub command marker prefix', () => {
    expect(
      component.getRenderedLineContent({
        number: 2,
        content: '[command]/usr/bin/git status',
        tone: 'command',
      })
    ).toBe('/usr/bin/git status');
  });

  it('should toggle line tone filters', () => {
    expect(component.isLineToneEnabled('error')).toBe(true);
    component.toggleLineTone('error');
    expect(component.isLineToneEnabled('error')).toBe(false);
    component.toggleLineTone('error');
    expect(component.isLineToneEnabled('error')).toBe(true);
  });

  it('should keep log groups collapsed by default and toggle them on demand', () => {
    expect(component.isLogGroupExpanded('group-12')).toBe(false);
    component.toggleLogGroup('group-12');
    expect(component.isLogGroupExpanded('group-12')).toBe(true);
    component.toggleLogGroup('group-12');
    expect(component.isLogGroupExpanded('group-12')).toBe(false);
  });

  it('should auto-expand log groups that contain error lines', () => {
    const autoExpandedGroups = getAutoExpandedLogGroupIds(
      ['[group]Build', 'setup', '[group]Compile', '[error]Process completed with exit code 1', '[endgroup]', '[endgroup]'].join('\n'),
      'error'
    );

    expect(autoExpandedGroups).toEqual({
      'group-1': true,
      'group-3': true,
    });
  });

  it('should keep an exactly matched successful group green even when the workflow failed', () => {
    expect(component.groupViews()[0].group.jobName).toBe('build');
    expect(component.groupViews()[0].outcome).toMatchObject({
      icon: 'circle-check',
      label: 'Success',
    });
    expect(component.groupViews()[1].group.jobName).toBeUndefined();
    expect(component.groupViews()[1].outcome).toMatchObject({
      icon: 'question-mark',
      label: 'Unknown',
    });
  });

  it('should expose the selected file step metadata without rendering the step panel', () => {
    expect(component.selectedStep()?.name).toBe('Checkout');
    expect(fixture.nativeElement.textContent).toContain('GitHub step: Checkout');
    expect(fixture.nativeElement.textContent).not.toContain('GitHub Steps');
  });

  it('should derive file status from the matched GitHub step number', () => {
    expect(component.groupedFiles()[0].file.stepNumber).toBe(1);
    expect(component.groupedFiles()[0].outcome).toMatchObject({
      icon: 'circle-check',
      label: 'Success',
    });
    expect(component.groupedFiles()[1].file.stepNumber).toBe(2);
    expect(component.groupedFiles()[1].outcome).toMatchObject({
      icon: 'circle-x',
      label: 'Failure',
    });
    expect(component.groupedFiles()[2].file.stepNumber).toBeUndefined();
  });

  it('should show step duration badges in the left panel for mapped files', () => {
    expect(fixture.nativeElement.textContent).toContain('30s');
    expect(fixture.nativeElement.textContent).toContain('1m 30s');
  });

  it('should apply a readable dark-mode text color to the selected file entry', () => {
    const selectedFileButton = fixture.nativeElement.querySelector('p-accordion-content button');

    expect(selectedFileButton).toBeTruthy();
    expect(selectedFileButton.className).toContain('dark:text-primary-50');
  });

  it('should not show inferred step metadata for an unmatched group', async () => {
    component.selectFile('deploy/system.txt');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.selectedStep()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('GitHub step:');
  });

  it('should open external logs in a safe new window', () => {
    const openedWindow = { opener: window } as Window;
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(openedWindow);

    component.openExternalLogs();

    expect(openSpy).toHaveBeenCalledWith('https://github.com/example/repo/actions/runs/42', '_blank', 'noopener,noreferrer');
    expect(openedWindow.opener).toBeNull();

    openSpy.mockRestore();
  });

  it('should force refresh logs and replace the cached query data', async () => {
    const refreshedLogs = createLogsResponse({
      cacheHit: false,
      downloadedAt: '2026-03-14T10:05:00Z',
    });
    const getWorkflowRunLogsSpy = vi.spyOn(workflowRunLogsApi, 'getWorkflowRunLogs').mockResolvedValue({
      data: refreshedLogs,
    } as Awaited<ReturnType<typeof workflowRunLogsApi.getWorkflowRunLogs>>);

    await component.refreshLogs();
    fixture.detectChanges();

    expect(getWorkflowRunLogsSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        path: { workflowRunId },
        query: { forceRefresh: true },
        throwOnError: true,
      })
    );
    expect(queryClient.getQueryData(getWorkflowRunLogsQueryKey({ path: { workflowRunId } }))).toEqual(refreshedLogs);
    expect(component.logsResponse()?.cacheHit).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Downloaded fresh logs');

    getWorkflowRunLogsSpy.mockRestore();
  });
});
