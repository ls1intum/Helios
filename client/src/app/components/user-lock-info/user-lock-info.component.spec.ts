import { DatePipe } from '@angular/common';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EnvironmentLockHistoryDto } from '@app/core/modules/openapi';
import { client } from '@app/core/modules/openapi/client.gen';
import { getEnvironmentsByUserLockingQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { UserLockInfoComponent } from './user-lock-info.component';

describe('UserLockInfoComponent', () => {
  let fixture: ComponentFixture<UserLockInfoComponent>;
  let queryClient: QueryClient;
  const loggedIn = signal(true);
  const now = new Date('2026-10-23T10:00:00Z');
  const originalConfig = client.getConfig();
  const fetch = vi.fn();
  const key = () => getEnvironmentsByUserLockingQueryKey();
  const lock = (expiresAt?: string): EnvironmentLockHistoryDto => ({
    id: 1,
    lockedAt: '2026-10-23T09:30:00Z',
    environment: {
      id: 8,
      name: 'Shared staging',
      repository: { id: 7, name: 'Helios', nameWithOwner: 'ls1intum/Helios', htmlUrl: 'https://github.com/ls1intum/Helios', updatedAt: now.toISOString() },
      lockWillExpireAt: expiresAt,
    },
  });

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(now);
    loggedIn.set(true);
    fetch.mockReset().mockImplementation(async () => new Response('{}', { headers: { 'Content-Type': 'application/json' } }));
    client.setConfig({ baseUrl: 'http://localhost', fetch });
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } } });
    await TestBed.configureTestingModule({
      imports: [UserLockInfoComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideQueryClient(queryClient),
        DatePipe,
        DateService,
        { provide: KeycloakService, useValue: { isLoggedIn: loggedIn } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    queryClient.clear();
    client.setConfig({ baseUrl: originalConfig.baseUrl, fetch: originalConfig.fetch });
    vi.useRealTimers();
  });

  const render = (value: EnvironmentLockHistoryDto) => {
    queryClient.setQueryData(key(), value);
    fixture = TestBed.createComponent(UserLockInfoComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('hides cached reservations and disables fetching when the user is logged out', () => {
    loggedIn.set(false);
    const host = render(lock());
    expect(host.querySelector('a')).toBeNull();
    expect(host.textContent?.trim()).toBe('');
    expect(fetch).not.toHaveBeenCalled();
  });

  it.each([{ id: 1 }, { id: 1, lockedAt: null }])('does not render a reservation when the API has no locked timestamp: %j', value => {
    const host = render(value);
    expect(host.querySelector('a')).toBeNull();
  });

  it('renders an unlimited lock with navigation to that repository environment list', () => {
    const host = render(lock());
    expect(host.textContent).toContain('Unlimited');
    expect(host.querySelector('a')?.getAttribute('href')).toBe('/repo/7/environment/list');
    expect(host.querySelector('i-tabler[name="lock"]')).not.toBeNull();
  });

  it('updates the rendered countdown once per second using the actual date service', () => {
    const host = render(lock('2026-10-23T10:02:03Z'));
    expect(host.textContent).toContain('00:02:03');
    vi.advanceTimersByTime(1_000);
    fixture.detectChanges();
    expect(host.textContent).toContain('00:02:02');
    vi.advanceTimersByTime(2_000);
    fixture.detectChanges();
    expect(host.textContent).toContain('00:02:00');
  });

  it('removes the lock when the user logs out without waiting for a query refresh', () => {
    const host = render(lock());
    expect(host.querySelector('a')).not.toBeNull();
    loggedIn.set(false);
    fixture.detectChanges();
    expect(host.querySelector('a')).toBeNull();
    expect(host.textContent?.trim()).toBe('');
  });

  it('stops scheduling countdown updates when destroyed', () => {
    render(lock('2026-10-23T10:02:03Z'));
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    fixture.destroy();
    queryClient.clear();
    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(3_000);
    expect(fetch).not.toHaveBeenCalled();
  });
});
