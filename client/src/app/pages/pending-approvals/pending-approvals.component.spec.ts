import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { vi } from 'vitest';
import { client } from '@app/core/modules/openapi/client.gen';
import { myPendingApprovalsQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { HANDLED_LOCALLY_HEADER } from '@app/core/utils/error-handling';
import { PendingApprovalsComponent } from './pending-approvals.component';

/** Answers the next API call with the given status and JSON body, like the Helios server would. */
function respondWith(fetchMock: ReturnType<typeof vi.fn>, status: number, body: object) {
  fetchMock.mockResolvedValue(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

describe('PendingApprovalsComponent', () => {
  let fixture: ComponentFixture<PendingApprovalsComponent>;
  let queryClient: QueryClient;
  let fetchMock: ReturnType<typeof vi.fn>;
  let add: ReturnType<typeof vi.spyOn>;

  const row = {
    approvalRequestId: 31,
    deploymentId: 9524,
    workflowRunId: 29863493883,
    environmentName: 'test-server-7',
    repositoryNameWithOwner: 'ls1intum/Helios',
    requestedAt: '2026-07-21T10:00:00Z',
  };

  beforeEach(async () => {
    fetchMock = vi.fn();
    client.setConfig({ baseUrl: 'http://localhost', fetch: fetchMock as unknown as typeof fetch });
    queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false }, mutations: { retry: false } } });
    queryClient.setQueryData(myPendingApprovalsQueryKey(), [row]);

    await TestBed.configureTestingModule({
      imports: [PendingApprovalsComponent],
      providers: [provideZonelessChangeDetection(), provideRouter([]), provideQueryClient(queryClient)],
    }).compileComponents();

    fixture = TestBed.createComponent(PendingApprovalsComponent);
    add = vi.spyOn(fixture.debugElement.injector.get(MessageService), 'add');
    fixture.detectChanges();
  });

  afterEach(() => {
    queryClient.clear();
    client.setConfig({ fetch: undefined as unknown as typeof fetch });
  });

  it('shows one informational notice with the server message when the request is no longer actionable', async () => {
    const message = 'This deployment is no longer waiting for approval on GitHub.';
    respondWith(fetchMock, 409, { status: 409, message });

    await fixture.componentInstance.approveMutation.mutateAsync(9524).catch(() => undefined);

    expect(add).toHaveBeenCalledTimes(1);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'info', summary: 'No longer waiting for approval', detail: message }));
  });

  it('shows one error with the server message for any other failure', async () => {
    const message = 'GitHub refused your decision even though the deployment is still waiting.';
    respondWith(fetchMock, 502, { status: 502, message });

    await fixture.componentInstance.declineMutation.mutateAsync({ deploymentId: 9524, comment: null }).catch(() => undefined);

    expect(add).toHaveBeenCalledTimes(1);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', summary: 'Decline failed', detail: message }));
  });

  it('falls back to a plain-language message when the server sends no details', async () => {
    respondWith(fetchMock, 500, {});

    await fixture.componentInstance.approveMutation.mutateAsync(9524).catch(() => undefined);

    expect(add).toHaveBeenCalledTimes(1);
    const toast = add.mock.calls[0][0] as { severity: string; summary: string; detail: string };
    expect(toast.severity).toBe('error');
    expect(toast.summary).toBe('Approve failed');
    expect(toast.detail).not.toContain('server logs');
  });

  it('suppresses the global error toast for its own calls and refreshes the list afterwards', async () => {
    respondWith(fetchMock, 409, { status: 409, message: 'gone' });
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    await fixture.componentInstance.approveMutation.mutateAsync(9524).catch(() => undefined);

    const sent = fetchMock.mock.calls[0][0] as Request;
    expect(sent.url).toContain('/api/deployments/9524/approve');
    expect(sent.headers.get(HANDLED_LOCALLY_HEADER)).toBe('true');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: myPendingApprovalsQueryKey() });
  });
});
