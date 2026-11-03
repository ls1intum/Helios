import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import type { WorkflowRunDto } from '@app/core/modules/openapi';
import {
  getLatestWorkflowRunsByBranchAndHeadCommitQueryKey,
  getLatestWorkflowRunsByPullRequestIdAndHeadCommitQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { WorkflowRunStatusComponent } from './workflow-run-status.component';

function workflow(overrides: Partial<WorkflowRunDto> = {}): WorkflowRunDto {
  return {
    id: 1,
    name: 'Build',
    displayTitle: 'Build',
    status: 'COMPLETED',
    conclusion: 'SUCCESS',
    workflowId: 10,
    htmlUrl: 'https://github.com/org/repo/actions/runs/1',
    label: 'TEST',
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    ...overrides,
  };
}

describe('WorkflowRunStatusComponent', () => {
  const branchKey = getLatestWorkflowRunsByBranchAndHeadCommitQueryKey({ query: { branch: 'staging' } });
  const pullRequestKey = getLatestWorkflowRunsByPullRequestIdAndHeadCommitQueryKey({ path: { pullRequestId: 1217 } });
  let queryClient: QueryClient;
  let fixture: ComponentFixture<WorkflowRunStatusComponent>;
  let component: WorkflowRunStatusComponent;

  beforeEach(async () => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
    queryClient.setQueryData(branchKey, []);
    queryClient.setQueryData(pullRequestKey, []);
    await TestBed.configureTestingModule({
      imports: [WorkflowRunStatusComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations(), provideQueryClient(queryClient)],
    }).compileComponents();
    fixture = TestBed.createComponent(WorkflowRunStatusComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('selector', { type: 'branch', branchName: 'staging' });
    fixture.componentRef.setInput('reFetchInterval', 0);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    fixture.destroy();
    queryClient.clear();
  });

  async function setBranchRuns(runs: WorkflowRunDto[]) {
    queryClient.setQueryData(branchKey, runs);
    await vi.waitFor(() => expect(component.workflowRuns()).toEqual(runs));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('shows an unknown state instead of claiming success for an empty branch', () => {
    expect(component.workflowStatus()).toMatchObject({ icon: 'question-mark', tooltip: 'No Workflows or Unknown Status' });
    expect(fixture.nativeElement.querySelector('i-tabler').classList.contains('text-muted-color')).toBe(true);
    expect(fixture.nativeElement.querySelector('p-skeleton')).toBeNull();
  });

  it('shows a successful summary only when every run completed successfully', async () => {
    await setBranchRuns([workflow(), workflow({ id: 2 })]);

    expect(component.workflowStatus()).toMatchObject({ icon: 'check', tooltip: 'All Workflows Passed:\n- 2 successful' });
    expect(fixture.nativeElement.querySelector('i-tabler').classList.contains('text-green-600')).toBe(true);
  });

  it.each(['IN_PROGRESS', 'QUEUED', 'WAITING', 'PENDING', 'REQUESTED'] as const)('keeps a %s run visibly in progress even when another workflow has failed', async status => {
    await setBranchRuns([workflow({ conclusion: 'FAILURE' }), workflow({ id: 2, status, conclusion: undefined })]);

    expect(component.workflowStatus()).toMatchObject({ icon: 'clock', tooltip: 'Workflow In Progress' });
    expect(fixture.nativeElement.querySelector('i-tabler').classList.contains('text-yellow-500')).toBe(true);
  });

  it.each(['FAILURE', 'CANCELLED', 'TIMED_OUT'] as const)('does not present completed %s runs as successful', async conclusion => {
    await setBranchRuns([workflow({ conclusion })]);

    expect(component.workflowStatus()).toMatchObject({ icon: 'x', tooltip: 'Some Workflows Failed:\n- 1 failing' });
    expect(fixture.nativeElement.querySelector('i-tabler').classList.contains('text-red-600')).toBe(true);
  });

  it('includes all nonempty result categories in a mixed failure summary', async () => {
    await setBranchRuns([
      workflow({ conclusion: 'FAILURE' }),
      workflow({ id: 2, conclusion: 'SUCCESS' }),
      workflow({ id: 3, conclusion: 'SKIPPED' }),
      workflow({ id: 4, conclusion: 'NEUTRAL' }),
    ]);

    expect(component.workflowStatus().tooltip).toBe('Some Workflows Failed:\n- 1 failing\n- 1 successful\n- 1 skipped\n- 1 neutral');
  });

  it.each(['NEUTRAL', 'SKIPPED', 'UNKNOWN'] as const)('does not imply success when the only conclusion is %s', async conclusion => {
    await setBranchRuns([workflow({ conclusion })]);

    expect(component.workflowStatus().icon).toBe('question-mark');
  });

  it('switches from branch results to the selected pull request without leaking branch failures', async () => {
    await setBranchRuns([workflow({ conclusion: 'FAILURE' })]);
    queryClient.setQueryData(pullRequestKey, [workflow({ id: 42 })]);

    fixture.componentRef.setInput('selector', { type: 'pullRequest', pullRequestId: 1217 });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.isLoading()).toBe(false);
    expect(component.isError()).toBe(false);
    expect(component.workflowRuns().map(run => run.id)).toEqual([42]);
    expect(component.workflowStatus().icon).toBe('check');
  });

  it('renders a loading placeholder while the selected query has no result yet', async () => {
    queryClient.getQueryCache().find({ queryKey: branchKey })!.setState({ data: undefined, status: 'pending', fetchStatus: 'fetching' });
    await vi.waitFor(() => expect(component.isLoading()).toBe(true));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('p-skeleton')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('i-tabler')).toBeNull();
  });

  it('renders an error indicator instead of cached success when fetching fails', async () => {
    const error = new Error('Workflow service unavailable');
    queryClient.getQueryCache().find({ queryKey: branchKey })!.setState({ error, status: 'error', fetchStatus: 'idle' });
    await vi.waitFor(() => expect(component.isError()).toBe(true));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('p-skeleton')).toBeNull();
    expect(fixture.nativeElement.querySelector('i-tabler').classList.contains('text-red-500')).toBe(true);
  });

  it('counts completed conclusions and excludes queued or unrecognized outcomes', () => {
    const runs = [
      workflow(),
      workflow({ conclusion: 'FAILURE' }),
      workflow({ conclusion: 'CANCELLED' }),
      workflow({ conclusion: 'TIMED_OUT' }),
      workflow({ conclusion: 'SKIPPED' }),
      workflow({ conclusion: 'NEUTRAL' }),
      workflow({ conclusion: undefined }),
      workflow({ status: 'QUEUED', conclusion: 'SUCCESS' }),
    ];

    expect(component.countStatuses(runs)).toEqual({ success: 1, failure: 3, skipped: 1, neutral: 1 });
  });
});
