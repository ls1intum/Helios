import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DeploymentTimerDto, DeploymentTimerStepDto, EnvironmentDeployment } from '@app/core/modules/openapi';
import { DeploymentTimingService } from './deployment-timing.service';

describe('DeploymentTimingService', () => {
  let service: DeploymentTimingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(DeploymentTimingService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  const setNow = (isoTime: string) => {
    service['_currentTime'].set(new Date(isoTime).getTime());
  };

  const step = (overrides: Partial<DeploymentTimerStepDto> = {}): DeploymentTimerStepDto => ({
    key: 'PRE_DEPLOYMENT',
    label: 'PRE-DEPLOYMENT',
    status: 'active',
    mode: 'REMAINING',
    startedAt: '2026-04-23T10:00:00Z',
    estimateSeconds: 120,
    ...overrides,
  });

  const timer = (overrides: Partial<DeploymentTimerDto> = {}): DeploymentTimerDto => ({
    title: 'Deployment in Progress',
    headerMode: 'REMAINING',
    headerStartedAt: '2026-04-23T10:00:00Z',
    headerEstimateSeconds: 360,
    showQueuedMessage: false,
    steps: [
      step(),
      step({
        key: 'DEPLOYMENT',
        label: 'DEPLOYMENT',
        status: 'upcoming',
        mode: 'ESTIMATED',
        startedAt: undefined,
        estimateSeconds: 240,
      }),
    ],
    ...overrides,
  });

  it('formats duration headers from backend-provided timestamps', () => {
    const currentTimer = timer({
      headerMode: 'DURATION',
      headerStartedAt: '2026-04-23T09:58:00Z',
      headerEndedAt: '2026-04-23T10:07:00Z',
    });

    expect(service.getHeaderTimeLabel(currentTimer)).toBe('9m 0s');
  });

  it('formats estimated and remaining headers from timer steps', () => {
    setNow('2026-04-23T10:01:00Z');

    expect(service.getHeaderTimeLabel(timer())).toBe('5m 0s remaining');
    expect(service.getHeaderTimeLabel(timer({ headerMode: 'ESTIMATED' }))).toBe('~6m 0s estimated');
  });

  it('computes progress only from active remaining timer inputs', () => {
    setNow('2026-04-23T10:01:00Z');

    expect(service.getProgress(step())).toBe(50);
    expect(service.getProgress(step({ status: 'completed', mode: 'COMPLETED' }))).toBe(100);
    expect(service.getProgress(step({ status: 'upcoming', mode: 'ESTIMATED', startedAt: undefined }))).toBe(0);
  });

  it('formats step time from backend-provided modes', () => {
    setNow('2026-04-23T10:01:00Z');

    expect(service.getStepTime(step())).toBe('1m 0s\nremaining');
    expect(service.getStepTime(step({ mode: 'ESTIMATED' }))).toBe('~2m 0s\nestimated');
    expect(service.getStepTime(step({ mode: 'COMPLETED' }))).toBe('Completed');
    expect(service.getStepTime(step({ mode: 'FAILED' }))).toBe('Failed');
  });

  it('uses the backend timer when present', () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      type: 'HELIOS',
      state: 'SUCCESS',
      timer: timer({ title: 'Backend title' }),
    };

    expect(service.getTimer(deployment)?.title).toBe('Backend title');
  });

  it('keeps a raw-field fallback while the new timer payload rolls out', () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      type: 'HELIOS',
      state: 'QUEUED',
      estimatedPreDeployDurationSeconds: 120,
      estimatedDeployDurationSeconds: 240,
    };

    const fallbackTimer = service.getTimer(deployment);

    expect(fallbackTimer?.title).toBe('Deployment Queued');
    expect(fallbackTimer?.showQueuedMessage).toBe(true);
    expect(fallbackTimer?.steps[0].status).toBe('active');
  });
});
