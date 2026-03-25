import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DeployConfirmationComponent } from './deploy-confirmation.component';
import { TestModule } from '@app/test.module';
import { EnvironmentDto, EnvironmentReviewersDto } from '@app/core/modules/openapi';

describe('DeployConfirmationComponent', () => {
  let component: DeployConfirmationComponent;
  let fixture: ComponentFixture<DeployConfirmationComponent>;

  const environment: EnvironmentDto = {
    id: 1,
    name: 'production',
    displayName: 'Production',
    type: 'PRODUCTION',
    repository: {
      id: 1,
      name: 'Helios',
      nameWithOwner: 'ls1intum/Helios',
      htmlUrl: 'https://github.com/ls1intum/Helios',
      updatedAt: '2026-03-09T10:00:00Z',
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeployConfirmationComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(DeployConfirmationComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('isVisible', true);
    fixture.componentRef.setInput('environment', environment);
    component.query = {
      ...component.query,
      data: signal<EnvironmentReviewersDto>({ reviewers: [] }),
      isPending: signal(false),
    } as typeof component.query;
  });

  function getAlertMessage(): string {
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert).toBeTruthy();
    return alert.textContent;
  }

  it('shows the source ref as branch when type is branch', async () => {
    fixture.componentRef.setInput('sourceRef', { ref: 'main', type: 'branch' });
    fixture.detectChanges();
    await fixture.whenStable();

    const deployMessage = getAlertMessage();

    expect(deployMessage).toContain('You are about to deploy to production');
    expect(deployMessage).toContain('from branch');
    expect(deployMessage).toContain('main');
  });

  it('shows the source ref as tag when type is tag', async () => {
    fixture.componentRef.setInput('sourceRef', { ref: 'v3.0.0', type: 'tag' });
    fixture.detectChanges();
    await fixture.whenStable();

    const deployMessage = getAlertMessage();

    expect(deployMessage).toContain('from tag');
    expect(deployMessage).toContain('v3.0.0');
  });

  it('omits the source ref block when no source ref is provided', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const deployMessage = getAlertMessage();

    expect(deployMessage).toContain('You are about to deploy to production');
    expect(deployMessage).not.toContain('from branch');
    expect(deployMessage).not.toContain('from tag');
  });

  it('keeps the deploy button disabled until repository confirmation matches', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const buttons = fixture.debugElement.queryAll(By.css('button'));
    const deployButton = buttons.find(button => button.nativeElement.textContent.includes('Deploy'))?.nativeElement as HTMLButtonElement;

    expect(deployButton.disabled).toBe(true);

    component.repoConfirm = 'ls1intum/Helios';
    fixture.detectChanges();

    expect(deployButton.disabled).toBe(false);
  });
});
