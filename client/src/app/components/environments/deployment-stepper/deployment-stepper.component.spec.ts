import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { DeploymentTimerDto, EnvironmentDeployment } from '@app/core/modules/openapi';
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

  const timer = (overrides: Partial<DeploymentTimerDto> = {}): DeploymentTimerDto => ({
    title: 'Deployment in Progress',
    headerMode: 'ESTIMATED',
    headerEstimateSeconds: 360,
    showQueuedMessage: false,
    steps: [
      {
        key: 'PRE_DEPLOYMENT',
        label: 'PRE-DEPLOYMENT',
        status: 'active',
        mode: 'ESTIMATED',
        estimateSeconds: 120,
      },
      {
        key: 'DEPLOYMENT',
        label: 'DEPLOYMENT',
        status: 'upcoming',
        mode: 'ESTIMATED',
        estimateSeconds: 240,
      },
    ],
    ...overrides,
  });

  const deployment = (deploymentTimer?: DeploymentTimerDto): EnvironmentDeployment => ({
    id: 1,
    state: 'IN_PROGRESS',
    createdAt: '2026-04-23T10:00:00Z',
    type: 'HELIOS',
    timer: deploymentTimer,
  });

  it('renders the backend-provided title, header, and steps', async () => {
    fixture.componentRef.setInput('deployment', deployment(timer()));
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment in Progress');
    expect(text).toContain('~6m 0s estimated');
    expect(text).toContain('PRE-DEPLOYMENT');
    expect(text).toContain('DEPLOYMENT');
  });

  it('renders the backend-provided queued message flag', async () => {
    fixture.componentRef.setInput(
      'deployment',
      deployment(
        timer({
          title: 'Deployment Queued',
          showQueuedMessage: true,
        })
      )
    );
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment Queued');
    expect(text).toContain('Waiting for GitHub Actions to start this workflow.');
  });

  it('uses backend step statuses for severity and progress', async () => {
    const currentTimer = timer({
      steps: [
        {
          key: 'PRE_DEPLOYMENT',
          label: 'PRE-DEPLOYMENT',
          status: 'completed',
          mode: 'COMPLETED',
          estimateSeconds: 120,
        },
        {
          key: 'DEPLOYMENT',
          label: 'DEPLOYMENT',
          status: 'active',
          mode: 'REMAINING',
          startedAt: new Date().toISOString(),
          estimateSeconds: 240,
        },
      ],
    });

    fixture.componentRef.setInput('deployment', deployment(currentTimer));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.getSeverityFromStepStatus(currentTimer.steps[0])).toBe('success');
    expect(fixture.componentInstance.getSeverityFromStepStatus(currentTimer.steps[1])).toBe('info');
    expect(fixture.componentInstance.getProgress(currentTimer.steps[0])).toBe(100);
  });

  it('falls back when the timer payload is missing during rollout', async () => {
    fixture.componentRef.setInput('deployment', {
      id: 1,
      state: 'QUEUED',
      type: 'HELIOS',
    } satisfies EnvironmentDeployment);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Deployment Queued');
    expect(text).toContain('Waiting for GitHub Actions to start this workflow.');
  });
});
