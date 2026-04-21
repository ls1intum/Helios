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

  it('shows queued as a label in the existing pre-deployment step', async () => {
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
    expect(text).toContain('PRE-DEPLOYMENT');
    expect(text).toContain('Queued');
    expect(text).toContain('DEPLOYMENT');
  });
});
