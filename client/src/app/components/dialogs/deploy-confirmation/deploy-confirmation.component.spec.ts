import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DeployConfirmationComponent } from './deploy-confirmation.component';
import { TestModule } from '@app/test.module';
import { EnvironmentDeploymentReadinessDto, EnvironmentDto, EnvironmentReviewersDto } from '@app/core/modules/openapi';

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
    component.readinessQuery = {
      ...component.readinessQuery,
      data: signal<EnvironmentDeploymentReadinessDto | undefined>(undefined),
      isPending: signal(false),
    } as typeof component.readinessQuery;
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

    component.repoConfirm.set('ls1intum/Helios');
    fixture.detectChanges();

    expect(deployButton.disabled).toBe(false);
  });

  it('shows an exact workflow warning when a required workflow is still running for the selected branch and commit', async () => {
    fixture.componentRef.setInput('sourceRef', { ref: 'main', type: 'branch' });
    fixture.componentRef.setInput('commitSha', 'abcdef1234567890');
    component.readinessQuery = {
      ...component.readinessQuery,
      data: signal<EnvironmentDeploymentReadinessDto>({
        status: 'WAITING',
        workflows: [
          {
            workflowId: 2,
            workflowName: 'Build',
            status: 'WAITING',
            runHtmlUrl: 'https://example.com/runs/2',
            runStatus: 'IN_PROGRESS',
          },
        ],
      }),
      isPending: signal(false),
    } as typeof component.readinessQuery;

    fixture.detectChanges();
    await fixture.whenStable();

    const warning = fixture.nativeElement.querySelector('[data-testid="deployment-readiness-warning"]');

    expect(warning).toBeTruthy();
    expect(warning.textContent).toContain('Required pre-deployment workflows are not ready.');
    expect(warning.textContent).toContain('Deployment may wait for the build to finish.');
    expect(warning.textContent).toContain('In most cases, it is enough if the actions in the PR have finished.');
    expect(warning.textContent).toContain('Some are still running on branch main at commit abcdef1.');
    expect(warning.textContent).toContain('Build');
    expect(warning.textContent).toContain('still in progress');
  });

  it('shows the branch and commit once when matching runs are missing', async () => {
    fixture.componentRef.setInput('sourceRef', { ref: 'main', type: 'branch' });
    fixture.componentRef.setInput('commitSha', 'abcdef1234567890');
    component.readinessQuery = {
      ...component.readinessQuery,
      data: signal<EnvironmentDeploymentReadinessDto>({
        status: 'MISSING_RUN',
        workflows: [
          {
            workflowId: 2,
            workflowName: 'Build',
            status: 'MISSING_RUN',
          },
          {
            workflowId: 3,
            workflowName: 'Test',
            status: 'MISSING_RUN',
          },
        ],
      }),
      isPending: signal(false),
    } as typeof component.readinessQuery;

    fixture.detectChanges();
    await fixture.whenStable();

    const warning = fixture.nativeElement.querySelector('[data-testid="deployment-readiness-warning"]');

    expect(warning).toBeTruthy();
    expect(warning.textContent).toContain('No matching runs were found on branch main at commit abcdef1 for these workflows:');
    expect(warning.textContent).toContain('Build');
    expect(warning.textContent).toContain('Test');
    expect(warning.textContent).not.toContain('No matching run found.');
  });

  it('falls back to the workflow id when a missing workflow has no name', async () => {
    fixture.componentRef.setInput('sourceRef', { ref: 'main', type: 'branch' });
    fixture.componentRef.setInput('commitSha', 'abcdef1234567890');
    component.readinessQuery = {
      ...component.readinessQuery,
      data: signal<EnvironmentDeploymentReadinessDto>({
        status: 'MISSING_RUN',
        workflows: [
          {
            workflowId: 7,
            status: 'MISSING_RUN',
          },
        ],
      }),
      isPending: signal(false),
    } as typeof component.readinessQuery;

    fixture.detectChanges();
    await fixture.whenStable();

    const warning = fixture.nativeElement.querySelector('[data-testid="deployment-readiness-warning"]');

    expect(warning).toBeTruthy();
    expect(warning.textContent).toContain('Workflow #7');
  });
});
