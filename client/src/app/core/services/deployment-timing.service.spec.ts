import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
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

  const deployment = (overrides: Partial<EnvironmentDeployment> = {}): EnvironmentDeployment => ({
    id: 1,
    type: 'HELIOS',
    state: 'PENDING',
    createdAt: '2026-04-23T10:00:00Z',
    estimatedBuildDurationSeconds: 120,
    estimatedDeployDurationSeconds: 300,
    ...overrides,
  });

  const setNow = (isoTime: string) => {
    service['_currentTime'].set(new Date(isoTime).getTime());
  };

  it('keeps the existing pre-deployment and deployment steps', () => {
    expect(service.steps).toEqual(['PENDING', 'IN_PROGRESS']);
    expect(service.getStepDisplayName('PENDING')).toBe('PRE-DEPLOYMENT');
    expect(service.getStepDisplayName('IN_PROGRESS')).toBe('DEPLOYMENT');
  });

  it('activates the pre-deployment step for requested, pending, waiting, and queued states', () => {
    for (const state of ['REQUESTED', 'PENDING', 'WAITING', 'QUEUED'] as const) {
      const currentDeployment = deployment({ state });

      expect(service.getCurrentEffectiveStepIndex(currentDeployment)).toBe(0);
      expect(service.getStepStatus(currentDeployment, 0)).toBe('active');
      expect(service.getStepStatus(currentDeployment, 1)).toBe('upcoming');
    }
  });

  it('keeps the pre-deployment timer stopped until the deploy job has started', () => {
    const currentDeployment = deployment({ state: 'PENDING' });

    service.updateDeploymentState(deployment({ state: 'QUEUED' }));
    service.updateDeploymentState(currentDeployment);

    expect(service.getStepStartTime(1, 'PENDING')).toBeUndefined();
    expect(service.getCurrentEffectiveStepIndex(currentDeployment)).toBe(0);
    expect(service.getProgress(currentDeployment, 0)).toBe(0);
    expect(service.getStepTime(currentDeployment, 0)).toContain('estimated');
    expect(service.getTotalRemainingTime(currentDeployment)).toBe('7m 0s');
  });

  it('activates deployment once the deployment is in progress', () => {
    const startedDeployment = deployment({ state: 'IN_PROGRESS' });

    expect(service.getCurrentEffectiveStepIndex(startedDeployment)).toBe(1);
    expect(service.getStepStatus(startedDeployment, 0)).toBe('completed');
    expect(service.getStepStatus(startedDeployment, 1)).toBe('active');
  });

  it('marks success as completed and failure on the last known active step', () => {
    const startedDeployment = deployment({ state: 'IN_PROGRESS' });
    service.updateDeploymentState(startedDeployment);

    const failedDeployment = deployment({ state: 'FAILURE' });
    service.updateDeploymentState(failedDeployment);

    expect(service.getStepStatus(deployment({ state: 'SUCCESS' }), 0)).toBe('completed');
    expect(service.getStepStatus(deployment({ state: 'SUCCESS' }), 1)).toBe('completed');
    expect(service.getStepStatus(failedDeployment, 0)).toBe('completed');
    expect(service.getStepStatus(failedDeployment, 1)).toBe('error');
  });

  it('uses the persisted deploy job start as the pre-deployment timer anchor', () => {
    setNow('2026-04-23T10:01:00Z');
    const currentDeployment = deployment({
      state: 'PENDING',
      deployJobStartedAt: '2026-04-23T10:00:00Z',
    });

    expect(service.getCurrentEffectiveStepIndex(currentDeployment)).toBe(0);
    expect(service.getProgress(currentDeployment, 0)).toBe(50);
    expect(service.getStepTime(currentDeployment, 0)).toBe('1m 0s\nremaining');
  });

  it('switches to the deployment step when the persisted deployment phase has started', () => {
    setNow('2026-04-23T10:04:00Z');
    const currentDeployment = deployment({
      state: 'IN_PROGRESS',
      deployJobStartedAt: '2026-04-23T10:00:00Z',
      deploymentStartedAt: '2026-04-23T10:02:00Z',
    });

    expect(service.getCurrentEffectiveStepIndex(currentDeployment)).toBe(1);
    expect(service.getProgress(currentDeployment, 0)).toBe(100);
    expect(service.getProgress(currentDeployment, 1)).toBe(40);
    expect(service.getStepTime(currentDeployment, 1)).toBe('3m 0s\nremaining');
  });

  it('measures the finished deployment duration from the deploy job start when available', () => {
    const finishedDeployment = deployment({
      state: 'SUCCESS',
      createdAt: '2026-04-23T09:50:00Z',
      deployJobStartedAt: '2026-04-23T10:00:00Z',
      deploymentStartedAt: '2026-04-23T10:02:00Z',
      updatedAt: '2026-04-23T10:07:00Z',
    });

    expect(service.getDeploymentDuration(finishedDeployment)).toBe('7m 0s');
  });

  it('measures the finished deployment duration from creation when deploy job start is unavailable', () => {
    const finishedDeployment = deployment({
      state: 'SUCCESS',
      createdAt: '2026-04-23T09:50:00Z',
      deploymentStartedAt: '2026-04-23T10:02:00Z',
      updatedAt: '2026-04-23T10:07:00Z',
    });

    expect(service.getDeploymentDuration(finishedDeployment)).toBe('17m 0s');
  });
});
