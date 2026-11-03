import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { EnvironmentDeployment, WorkflowJobDto } from '@app/core/modules/openapi';
import { getWorkflowJobStatusQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { client } from '@app/core/modules/openapi/client.gen';
import { PermissionService } from '@app/core/services/permission.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { WorkflowJobsStatusComponent } from './workflow-jobs-status.component';

describe('WorkflowJobsStatusComponent', () => {
  let fixture: ComponentFixture<WorkflowJobsStatusComponent>;
  let queryClient: QueryClient;
  let canWrite: boolean;
  const fetchJobs = vi.fn();
  const originalConfig = client.getConfig();
  const jobs: WorkflowJobDto[] = [{ id: 1, name: 'Deploy application', status: 'in_progress' }];
  const deployment = (state: EnvironmentDeployment['state'], overrides: Partial<EnvironmentDeployment> = {}): EnvironmentDeployment => ({
    id: 1,
    type: 'HELIOS',
    state,
    workflowRunHtmlUrl: 'https://github.com/org/repo/actions/runs/123',
    ...overrides,
  });

  beforeEach(async () => {
    canWrite = true;
    fetchJobs.mockReset().mockImplementation(async () => new Response(JSON.stringify({ jobs }), { headers: { 'Content-Type': 'application/json' } }));
    client.setConfig({ baseUrl: 'http://localhost', fetch: fetchJobs });
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
    queryClient.setQueryData(getWorkflowJobStatusQueryKey({ path: { runId: 123 } }), { jobs });
    await TestBed.configureTestingModule({
      imports: [WorkflowJobsStatusComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideQueryClient(queryClient),
        { provide: PermissionService, useValue: { hasWritePermission: () => canWrite } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(WorkflowJobsStatusComponent);
  });

  afterEach(() => {
    fixture.destroy();
    queryClient.clear();
    client.setConfig({ baseUrl: originalConfig.baseUrl, fetch: originalConfig.fetch });
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  async function render(value?: EnvironmentDeployment) {
    fixture.componentRef.setInput('latestDeployment', value);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it.each(['WAITING', 'REQUESTED', 'PENDING', 'QUEUED', 'IN_PROGRESS'] as const)('loads and displays jobs during %s deployments', async state => {
    const host = await render(deployment(state));
    expect(host.textContent).toContain('Job Status');
    expect(host.textContent).toContain('Deploy application');
    expect(fixture.componentInstance.shouldPoll()).toBe(true);
    await vi.waitFor(() => expect(fetchJobs).toHaveBeenCalled());
    expect((fetchJobs.mock.calls[0][0] as Request).url).toBe('http://localhost/api/deployments/workflowJobStatus/123');
    expect(host.textContent).not.toContain('Some jobs failed');
  });

  it('does not load jobs when the user lacks write permission', async () => {
    canWrite = false;
    const host = await render(deployment('IN_PROGRESS'));
    expect(fixture.componentInstance.shouldPoll()).toBe(false);
    expect(fetchJobs).not.toHaveBeenCalled();
    expect(host.textContent).toContain('Deploy application');
  });

  it.each([undefined, '', 'https://github.com/org/repo/actions/runs/not-a-number'])('does not poll when the workflow run URL is unusable: %s', async workflowRunHtmlUrl => {
    const host = await render(deployment('IN_PROGRESS', { workflowRunHtmlUrl }));
    expect(fixture.componentInstance.shouldPoll()).toBe(false);
    expect(fetchJobs).not.toHaveBeenCalled();
    expect(host.textContent?.trim()).toBe('');
  });

  it('renders no job section when there is no deployment', async () => {
    const host = await render();
    expect(host.textContent?.trim()).toBe('');
    expect(fetchJobs).not.toHaveBeenCalled();
  });

  it('hides cached jobs after a successful deployment', async () => {
    const host = await render(deployment('SUCCESS'));
    expect(host.textContent?.trim()).toBe('');
    expect(fetchJobs).not.toHaveBeenCalled();
  });

  it.each(['ERROR', 'FAILURE'] as const)('shows failed jobs and their workflow run link for %s', state => {
    vi.useFakeTimers();
    fixture.componentRef.setInput('latestDeployment', deployment(state));
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Deploy application');
    expect(host.textContent).toContain('Some jobs failed. Please check the details below.');
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const link = host.querySelector('button')!;
    expect(link.textContent).toContain('Open Workflow Run on GitHub');
    link.click();
    expect(open).toHaveBeenCalledWith('https://github.com/org/repo/actions/runs/123', '_blank');
    open.mockRestore();
    expect(fixture.componentInstance.shouldPoll()).toBe(true);
  });

  it('does not show a job section for an empty job response', async () => {
    queryClient.setQueryData(getWorkflowJobStatusQueryKey({ path: { runId: 123 } }), { jobs: [] });
    canWrite = false;
    const host = await render(deployment('IN_PROGRESS'));
    expect(host.textContent?.trim()).toBe('');
  });

  it('stops additional polling one minute after a failed deployment', () => {
    vi.useFakeTimers();
    fixture.componentRef.setInput('latestDeployment', deployment('FAILURE'));
    fixture.detectChanges();
    expect(fixture.componentInstance.shouldPoll()).toBe(true);
    vi.advanceTimersByTime(59_999);
    fixture.detectChanges();
    expect(fixture.componentInstance.shouldPoll()).toBe(true);
    vi.advanceTimersByTime(1);
    fixture.detectChanges();
    expect(fixture.componentInstance.shouldPoll()).toBe(false);
  });
});
