import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { queueApi, type AlertRuleDto } from './queue.api';

/**
 * Regression guard: the backend derives the WRITE role from the X-Repository-Id header (not the URL
 * path) and returns no authorities when it is absent, so every alert-rule write 403s without it.
 * These tests assert the header is sent and matches the path repo for create/update/delete.
 */
describe('queueApi alert-rule writes', () => {
  let httpMock: HttpTestingController;
  let api: ReturnType<typeof queueApi>;

  const body: AlertRuleDto = {
    id: null,
    kind: 'QUEUE_P95_OVER',
    thresholdSeconds: 600,
    windowMinutes: 5,
    repositoryId: null,
    labelSetHash: null,
    channels: ['EMAIL'],
    enabled: true,
    quietWindow: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    api = TestBed.runInInjectionContext(() => queueApi());
  });

  afterEach(() => httpMock.verify());

  it('sends X-Repository-Id matching the path repo on createRule', () => {
    void api.createRule(7, body);
    const req = httpMock.expectOne('/api/queue/repos/7/alerts/rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('X-Repository-Id')).toBe('7');
    req.flush(body);
  });

  it('sends X-Repository-Id matching the path repo on updateRule', () => {
    void api.updateRule(7, 42, body);
    const req = httpMock.expectOne('/api/queue/repos/7/alerts/rules/42');
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('X-Repository-Id')).toBe('7');
    req.flush(body);
  });

  it('sends X-Repository-Id matching the path repo on deleteRule', () => {
    void api.deleteRule(7, 42);
    const req = httpMock.expectOne('/api/queue/repos/7/alerts/rules/42');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.headers.get('X-Repository-Id')).toBe('7');
    req.flush(null);
  });
});
