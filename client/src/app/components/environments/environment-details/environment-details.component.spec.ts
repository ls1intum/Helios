import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { getCommitByRepositoryIdAndNameQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { EnvironmentDetailsComponent } from './environment-details.component';

describe('EnvironmentDetailsComponent', () => {
  let fixture: ComponentFixture<EnvironmentDetailsComponent>;
  let queryClient: QueryClient;
  const environment: EnvironmentDto = { id: 7, name: 'staging', type: 'STAGING', locked: false, enabled: true };
  const deployment = { id: 1, type: 'HELIOS' as const, state: 'SUCCESS' as const, sha: 'abcdef1234567890' };
  const commitQueryKey = () => getCommitByRepositoryIdAndNameQueryKey({ path: { repoId: 0, sha: deployment.sha } });

  beforeEach(async () => {
    queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
    await TestBed.configureTestingModule({
      imports: [EnvironmentDetailsComponent],
      providers: [provideZonelessChangeDetection(), provideQueryClient(queryClient), { provide: DateService, useValue: { formatDate: () => 'Formatted build time' } }],
    }).compileComponents();
    fixture = TestBed.createComponent(EnvironmentDetailsComponent);
  });

  afterEach(() => queryClient.clear());

  async function render(value: EnvironmentDto) {
    fixture.componentRef.setInput('environment', value);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('omits deployment and status sections when the environment has neither', async () => {
    const host = await render(environment);
    expect(host.querySelector('app-environment-deployment-info')).toBeNull();
    expect(host.querySelector('app-environment-status-info')).toBeNull();
    expect(host.textContent?.trim()).toBe('');
  });

  it('renders status independently of deployment information', async () => {
    const host = await render({
      ...environment,
      latestStatus: { id: 1, checkType: 'HTTP_STATUS', success: false, httpStatusCode: 503, checkedAt: new Date().toISOString() },
    });
    expect(host.querySelector('app-environment-deployment-info')).toBeNull();
    expect(host.textContent).toContain('Latest status check');
    expect(host.textContent).toContain('503');
  });

  it.each([undefined, {}])('explains unavailable commit information for %s query data', async commit => {
    if (commit) queryClient.setQueryData(commitQueryKey(), commit);
    const host = await render({ ...environment, latestDeployment: deployment });
    expect(host.textContent).toContain('Commit Information Could Not Be Loaded');
    expect(host.textContent).toContain('Try again later');
    expect(host.querySelector('app-environment-status-info')).toBeNull();
  });

  it.each([true, false])('renders commit author and shortened SHA with author avatar=%s', async hasAvatar => {
    queryClient.setQueryData(commitQueryKey(), {
      message: 'Fix environment deployment',
      authoredAt: '2026-10-23T09:00:00Z',
      author: { name: 'Alice', avatarUrl: hasAvatar ? 'https://example.org/alice.png' : undefined },
    });
    const host = await render({ ...environment, latestDeployment: deployment });
    expect(host.textContent).toContain('Fix environment deployment');
    expect(host.textContent).toContain('Alice authored');
    expect(host.textContent).toContain('abcdef12');
    expect(host.textContent).not.toContain(deployment.sha);
    expect(!!host.querySelector('p-avatar')).toBe(hasAvatar);
    expect(!!host.querySelector('i-tabler[name="user"]')).toBe(!hasAvatar);
  });

  it('renders commit information without an authored date', async () => {
    queryClient.setQueryData(commitQueryKey(), { message: 'Initial deployment' });
    const host = await render({ ...environment, latestDeployment: deployment });
    expect(host.textContent).toContain('Initial deployment');
    expect(host.textContent).not.toContain('Invalid Date');
  });
});
