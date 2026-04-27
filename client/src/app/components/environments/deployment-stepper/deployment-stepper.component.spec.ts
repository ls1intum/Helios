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

  it('keeps non-queued active deployments labeled as in progress', async () => {
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
    expect(text).not.toContain('Deployment Queued');
    expect(text).not.toContain('Waiting for GitHub Actions to start this workflow.');
  });
});
