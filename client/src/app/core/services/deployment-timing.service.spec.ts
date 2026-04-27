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

  const deployment = (state: EnvironmentDeployment['state']): EnvironmentDeployment => ({
    id: 1,
    state,
    createdAt: new Date().toISOString(),
    type: 'HELIOS',
  });

  it('keeps the existing pre-deployment and deployment steps', () => {
    expect(service.steps).toEqual(['PENDING', 'IN_PROGRESS']);
    expect(service.getStepDisplayName('PENDING')).toBe('PRE-DEPLOYMENT');
    expect(service.getStepDisplayName('IN_PROGRESS')).toBe('DEPLOYMENT');
  });

  it('activates the pre-deployment step for requested, pending, waiting, and queued states', () => {
    for (const state of ['REQUESTED', 'PENDING', 'WAITING', 'QUEUED'] as const) {
      expect(service.getCurrentEffectiveStepIndex(deployment(state))).toBe(0);
      expect(service.getStepStatus(deployment(state), 0)).toBe('active');
      expect(service.getStepStatus(deployment(state), 1)).toBe('upcoming');
    }
  });

  it('does not start the pre-deployment timer while queued', () => {
    service.updateDeploymentState(deployment('QUEUED'));
    expect(service.getStepStartTime(1, 'PENDING')).toBeUndefined();
    expect(service.getProgress(deployment('QUEUED'), 0)).toBe(0);

    service.updateDeploymentState(deployment('PENDING'));
    expect(service.getStepStartTime(1, 'PENDING')).toBeDefined();
  });

  it('activates deployment once the deployment is in progress', () => {
    const startedDeployment = deployment('IN_PROGRESS');
    expect(service.getCurrentEffectiveStepIndex(startedDeployment)).toBe(1);
    expect(service.getStepStatus(startedDeployment, 0)).toBe('completed');
    expect(service.getStepStatus(startedDeployment, 1)).toBe('active');
  });

  it('marks success as completed and failure on the last known active step', () => {
    const startedDeployment = deployment('IN_PROGRESS');
    service.updateDeploymentState(startedDeployment);

    const failedDeployment = deployment('FAILURE');
    service.updateDeploymentState(failedDeployment);

    expect(service.getStepStatus(deployment('SUCCESS'), 0)).toBe('completed');
    expect(service.getStepStatus(deployment('SUCCESS'), 1)).toBe('completed');
    expect(service.getStepStatus(failedDeployment, 0)).toBe('completed');
    expect(service.getStepStatus(failedDeployment, 1)).toBe('error');
  });
});
