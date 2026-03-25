import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { QueryClient, provideQueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { ReleaseCandidateDeploymentTableComponent } from './release-candidate-deployment-table.component';
import { DeployConfirmationComponent } from '@app/components/dialogs/deploy-confirmation/deploy-confirmation.component';
import { TestModule } from '@app/test.module';
import { EnvironmentDto, ReleaseInfoDetailsDto } from '@app/core/modules/openapi';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';

describe('ReleaseCandidateDeploymentTableComponent', () => {
  let component: ReleaseCandidateDeploymentTableComponent;
  let fixture: ComponentFixture<ReleaseCandidateDeploymentTableComponent>;

  const environment: EnvironmentDto = {
    id: 7,
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

  const releaseCandidate: ReleaseInfoDetailsDto = {
    name: 'rc-1',
    commit: {
      sha: 'abcdef1234567',
    },
    branch: {
      name: 'release/1.2.3',
      commitSha: 'abcdef1234567',
    },
    deployments: [],
    evaluations: [],
    createdAt: '2026-03-09T10:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReleaseCandidateDeploymentTableComponent],
      providers: [
        importProvidersFrom(TestModule),
        provideQueryClient(new QueryClient()),
        MessageService,
        {
          provide: KeycloakService,
          useValue: {
            isLoggedIn: () => true,
          },
        },
        {
          provide: PermissionService,
          useValue: {
            isAdmin: signal(true),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReleaseCandidateDeploymentTableComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('releaseCandidate', releaseCandidate);
    component.environmentQuery = {
      ...component.environmentQuery,
      data: signal([environment]),
      isPending: signal(false),
      isError: signal(false),
    } as typeof component.environmentQuery;
    component.deployToEnvironment = {
      ...component.deployToEnvironment,
      isPending: signal(false),
    } as typeof component.deployToEnvironment;
  });

  it('passes the release candidate branch to the deploy confirmation dialog', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const deployButton = fixture.debugElement.queryAll(By.css('button')).find(button => button.nativeElement.textContent.includes('Deploy'))?.nativeElement as HTMLButtonElement;
    deployButton.click();
    fixture.detectChanges();

    const dialog = fixture.debugElement.query(By.directive(DeployConfirmationComponent)).componentInstance as DeployConfirmationComponent;

    expect(component.deployDialogVisible()).toBe(true);
    expect(dialog.sourceRef()).toEqual({ ref: 'release/1.2.3', type: 'branch' });
  });

  it('falls back to the release candidate name as a tag when no branch is present', async () => {
    fixture.componentRef.setInput('releaseCandidate', {
      ...releaseCandidate,
      branch: undefined,
    });
    fixture.detectChanges();
    await fixture.whenStable();

    component.deployReleaseCandidate(environment);
    fixture.detectChanges();

    const dialog = fixture.debugElement.query(By.directive(DeployConfirmationComponent)).componentInstance as DeployConfirmationComponent;

    expect(dialog.sourceRef()).toEqual({ ref: 'rc-1', type: 'tag' });
  });
});
