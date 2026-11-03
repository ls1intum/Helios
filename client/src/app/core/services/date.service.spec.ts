import { DatePipe } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { DateService } from './date.service';
import {
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsQueryKey,
  getEnvironmentsByUserLockingQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';

describe('DateService', () => {
  const now = new Date('2026-10-23T12:00:00Z');
  let service: DateService;
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), { provide: DatePipe, useValue: new DatePipe('en-US', 'UTC') }, { provide: QueryClient, useValue: queryClient }],
    });
    service = TestBed.inject(DateService);
  });

  afterEach(() => queryClient.clear());

  it('formats strings, Date objects and numeric timestamps with the requested format', () => {
    expect(service.formatDate('2026-10-23T12:00:00Z')).toBe('23. October 2026');
    expect(service.formatDate(now, 'yyyy-MM-dd HH:mm')).toBe('2026-10-23 12:00');
    expect(service.formatDate(now.getTime(), 'yyyy-MM-dd')).toBe('2026-10-23');
    expect(service.formatDate()).toBeNull();
  });

  it('does not display elapsed time for missing or future locks', () => {
    expect(service.timeSinceLocked(undefined, now)).toBe('');
    expect(service.timeSinceLocked('2026-10-23T12:00:01Z', now)).toBe('');
  });

  it.each([
    [0, '00:00:00'],
    [999, '00:00:00'],
    [1000, '00:00:01'],
    [65000, '00:01:05'],
    [3661000, '01:01:01'],
    [86399000, '23:59:59'],
    [86400000, '1:00:00'],
    [93784000, '1:02:03'],
  ])('formats a lock elapsed by %i milliseconds as %s', (elapsed, expected) => {
    expect(service.timeSinceLocked(new Date(now.getTime() - elapsed).toISOString(), now)).toBe(expected);
  });

  it('shows the remaining reservation time without invalidating live data', () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    expect(service.timeUntilExpire(now, '2026-10-23T13:02:03Z')).toBe('01:02:03');
    expect(service.timeUntilExpire(now, undefined)).toBe('');
    expect(invalidate).not.toHaveBeenCalled();
  });

  it.each(['2026-10-23T11:59:59Z', '2026-10-23T12:00:00Z'])('refreshes all environment lists when a reservation expires at %s', expiration => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    expect(service.timeUntilExpire(now, expiration)).toBe('00:00:00');
    expect(invalidate).toHaveBeenCalledTimes(3);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: getAllEnabledEnvironmentsQueryKey() });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: getAllEnvironmentsQueryKey() });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: getEnvironmentsByUserLockingQueryKey() });
  });
});
