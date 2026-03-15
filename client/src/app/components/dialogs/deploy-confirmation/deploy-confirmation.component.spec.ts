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

  it('shows the source ref when provided', async () => {
    fixture.componentRef.setInput('sourceRef', 'main');
    fixture.detectChanges();
    await fixture.whenStable();

    const sourceRef = fixture.nativeElement.querySelector('[data-testid="deploy-source-ref"]');

    expect(sourceRef).toBeTruthy();
    expect(sourceRef.textContent).toContain('Branch to deploy:');
    expect(sourceRef.textContent).toContain('main');
  });

  it('omits the source ref block when no source ref is provided', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[data-testid="deploy-source-ref"]')).toBeNull();
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
