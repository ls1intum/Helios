import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { DeploymentStepperComponent } from './deployment-stepper.component';

describe('DeploymentStepperComponent', () => {
  let fixture: ComponentFixture<DeploymentStepperComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeploymentStepperComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(DeploymentStepperComponent);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('shows queued as an exposed waiting state in the existing pre-deployment step', async () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      state: 'QUEUED',
      createdAt: new Date().toISOString(),
      type: 'HELIOS',
    };

    fixture.componentRef.setInput('deployment', deployment);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment Queued');
    expect(text).toContain('Waiting for GitHub Actions to start this workflow. Deployment will continue automatically once a runner is available.');
    expect(text).toContain('PRE-DEPLOYMENT');
    expect(text).toContain('DEPLOYMENT');
  });

  it('labels requested deployments before action progress as requested', async () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      state: 'REQUESTED',
      createdAt: new Date().toISOString(),
      prName: 'Example PR',
      type: 'HELIOS',
    };

    fixture.componentRef.setInput('deployment', deployment);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment Requested');
    expect(text).toContain('~6m 0s estimated');
    expect(text).not.toContain('Deployment in Progress');
  });

  it('labels pending deployments with a pre-deployment start as in progress', async () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      state: 'PENDING',
      createdAt: '2026-04-23T10:00:00Z',
      workflowStartedAt: '2026-04-23T10:01:00Z',
      estimatedPreDeployDurationSeconds: 120,
      estimatedDeployDurationSeconds: 300,
      type: 'HELIOS',
    };

    fixture.componentRef.setInput('deployment', deployment);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment in Progress');
    expect(text).not.toContain('Deployment Requested');
  });

  it('labels workflow progress before deployment start as in progress', async () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      state: 'IN_PROGRESS',
      createdAt: new Date().toISOString(),
      type: 'HELIOS',
    };

    fixture.componentRef.setInput('deployment', deployment);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment in Progress');
    expect(text).toContain('remaining');
    expect(text).not.toContain('Deployment Queued');
    expect(text).not.toContain('Waiting for GitHub Actions to start this workflow.');
  });

  it('labels deployments as in progress after the deployment phase starts', async () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      state: 'IN_PROGRESS',
      createdAt: new Date().toISOString(),
      deployJobStartedAt: new Date().toISOString(),
      type: 'HELIOS',
    };

    fixture.componentRef.setInput('deployment', deployment);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment in Progress');
  });

  it('activates the deployment step when deploy job timing exists before the raw state changes', async () => {
    const deployment: EnvironmentDeployment = {
      id: 1,
      state: 'PENDING',
      createdAt: '2026-04-23T10:00:00Z',
      deployJobStartedAt: '2026-04-23T10:01:00Z',
      estimatedDeployDurationSeconds: 300,
      type: 'HELIOS',
    };

    fixture.componentRef.setInput('deployment', deployment);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment in Progress');
    expect(text).toContain('DEPLOYMENT');
    expect(fixture.componentInstance.getStepStatus(0)).toBe('completed');
    expect(fixture.componentInstance.getStepStatus(1)).toBe('active');
  });
});
