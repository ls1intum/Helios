import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivityHistoryDto } from '@app/core/modules/openapi';
import {
  getActivityHistoryByEnvironmentIdQueryKey,
  getEnvironmentByIdQueryKey,
  getRepositoryByIdQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { EnvironmentDeploymentHistoryComponent } from './environment-deployment-history.component';

describe('EnvironmentDeploymentHistoryComponent', () => {
  let fixture: ComponentFixture<EnvironmentDeploymentHistoryComponent>;
  let queryClient: QueryClient;
  const formatDate = vi.fn();
  const alice = { id: 1, name: 'Alice', login: 'alice', avatarUrl: '', htmlUrl: '' };
  const bob = { id: 2, name: 'Bob', login: 'bob', avatarUrl: '', htmlUrl: '' };

  beforeEach(async () => {
    formatDate.mockReset().mockReturnValue('23 October 2026, 10:00 AM');
    queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
    queryClient.setQueryData(getRepositoryByIdQueryKey({ path: { id: 3 } }), { nameWithOwner: 'org/Helios' });
    queryClient.setQueryData(getEnvironmentByIdQueryKey({ path: { id: 7 } }), { id: 7, name: 'staging' });
    await TestBed.configureTestingModule({
      imports: [EnvironmentDeploymentHistoryComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideQueryClient(queryClient),
        { provide: DateService, useValue: { formatDate } },
        { provide: KeycloakService, useValue: { isCurrentUser: () => false } },
      ],
    }).compileComponents();
  });

  afterEach(() => queryClient.clear());

  async function render(history: ActivityHistoryDto[]) {
    queryClient.setQueryData(getActivityHistoryByEnvironmentIdQueryKey({ path: { environmentId: 7 } }), history);
    fixture = TestBed.createComponent(EnvironmentDeploymentHistoryComponent);
    fixture.componentRef.setInput('repositoryId', '3');
    fixture.componentRef.setInput('environmentId', '7');
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the repository, environment, and an actionable empty state', async () => {
    const host = await render([]);
    expect(host.textContent).toContain('org/Helios');
    expect(host.textContent).toContain('Activity History');
    expect(host.textContent).toContain('Environment: staging');
    expect(host.textContent).toContain('There are no activitiy history found for this environment.');
    expect(host.textContent).toContain('Try reloading the page.');
    expect(host.querySelector('p-skeleton')).toBeNull();
  });

  it('renders deployment state, branch, shortened SHA, actor, and timestamp', async () => {
    const host = await render([{ type: 'DEPLOYMENT', state: 'SUCCESS', ref: 'main', sha: 'abcdef1234567890', user: alice, timestamp: '2026-10-23T10:00:00Z' }]);
    const row = host.querySelector('tbody tr')!;
    for (const text of ['DEPLOYMENT', 'SUCCESS', 'main', 'abcdef1', 'Alice', '23 October 2026, 10:00 AM']) expect(row.textContent).toContain(text);
    expect(row.textContent).not.toContain('abcdef1234567890');
    expect(row.querySelector('i-tabler[name="cloud-upload"]')).toBeTruthy();
    expect(formatDate).toHaveBeenCalledWith('2026-10-23T10:00:00Z', 'd MMMM y, h:mm a');
  });

  it('identifies the lock owner for lock events', async () => {
    const host = await render([{ type: 'LOCK_EVENT', user: alice, user2: bob }]);
    const row = host.querySelector('tbody tr')!;
    expect(row.textContent).toContain('Alice');
    expect(row.textContent).not.toContain('Bob');
    expect(row.classList.contains('lock-event')).toBe(true);
    expect(row.querySelector('i-tabler[name="lock"]')).toBeTruthy();
  });

  it('identifies the unlocking user for unlock events', async () => {
    const host = await render([{ type: 'UNLOCK_EVENT', user: alice, user2: bob }]);
    const row = host.querySelector('tbody tr')!;
    expect(row.textContent).toContain('Bob');
    expect(row.textContent).not.toContain('Alice');
    expect(row.classList.contains('lock-event')).toBe(false);
    expect(row.querySelector('i-tabler[name="lock-open"]')).toBeTruthy();
  });

  it('paginates activity history after twenty events', async () => {
    const history: ActivityHistoryDto[] = Array.from({ length: 21 }, (_, id) => ({ id, type: 'DEPLOYMENT', ref: `branch-${id}`, state: 'SUCCESS' }));
    const host = await render(history);
    expect(host.querySelectorAll('tbody tr')).toHaveLength(20);
    expect(host.textContent).not.toContain('branch-20');
    (host.querySelector('.p-paginator-next') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(host.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(host.textContent).toContain('branch-20');
    expect(host.textContent).not.toContain('branch-0');
  });

  it('renders a deployment even when commit and actor data are absent', async () => {
    const host = await render([{ type: 'DEPLOYMENT', state: 'FAILURE' }]);
    expect(host.textContent).toContain('FAILURE');
    expect(host.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(host.textContent).not.toContain('undefined');
  });
});
