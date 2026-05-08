import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { EnvironmentDeployment, EnvironmentDto } from '@app/core/modules/openapi';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { EnvironmentActionsComponent } from './environment-actions.component';

describe('EnvironmentActionsComponent', () => {
  let fixture: ComponentFixture<EnvironmentActionsComponent>;
  let component: EnvironmentActionsComponent;
  let currentGithubId: string;
  let isMaintainer: boolean;

  beforeEach(async () => {
    currentGithubId = '1';
    isMaintainer = false;

    await TestBed.configureTestingModule({
      imports: [EnvironmentActionsComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        {
          provide: KeycloakService,
          useValue: {
            isLoggedIn: () => true,
            getUserGithubId: () => currentGithubId,
          },
        },
        {
          provide: PermissionService,
          useValue: {
            hasWritePermission: () => true,
            isAtLeastMaintainer: () => isMaintainer,
          },
        },
      ],
    })
      .overrideComponent(EnvironmentActionsComponent, {
        set: { template: '' },
      })
      .compileComponents();

    fixture = TestBed.createComponent(EnvironmentActionsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  const deployment = (state: NonNullable<EnvironmentDeployment['state']>, overrides: Partial<EnvironmentDeployment> = {}): EnvironmentDeployment => ({
    id: 1,
    state,
    type: 'HELIOS',
    statusUpdatedAt: new Date().toISOString(),
    workflowRunHtmlUrl: 'https://github.com/ls1intum/Helios/actions/runs/1',
    ...overrides,
  });

  const environment = (latestDeployment?: EnvironmentDeployment): EnvironmentDto => ({
    id: 1,
    name: 'test-server-1',
    locked: true,
    enabled: true,
    type: 'TEST',
    lockedBy: {
      id: 1,
      login: 'test-user',
      avatarUrl: '',
      name: 'Test User',
      htmlUrl: '',
    },
    lockedAt: new Date().toISOString(),
    latestDeployment,
  });

  function setEnvironment(latestDeployment?: EnvironmentDeployment) {
    fixture.componentRef.setInput('environment', environment(latestDeployment));
    fixture.componentRef.setInput('deployable', true);
    fixture.componentRef.setInput('canViewAllEnvironments', false);
    fixture.componentRef.setInput('timeUntilReservationExpires', undefined);
    fixture.detectChanges();
  }

  it.each(['WAITING', 'PENDING', 'REQUESTED', 'QUEUED', 'IN_PROGRESS'] as const)('disables unlock while deployment state is %s', state => {
    setEnvironment(deployment(state));

    expect(component.isDeploymentInProgress()).toBe(true);
    expect(component.canUnlock()).toBe(false);
    expect(component.getUnlockToolTip()).toBe('Cancel the ongoing deployment before unlocking this environment.');
  });

  it('keeps cancel available while an active deployment has a workflow run URL', () => {
    setEnvironment(deployment('IN_PROGRESS'));

    expect(component.canCancelDeployment()).toBe(true);
    expect(component.getCancelDeploymentToolTip()).toBe('This will cancel the ongoing deployment.');
  });

  it('does not block unlock for active pure GitHub deployments without a status update timestamp', () => {
    const activeGitHubDeployment = deployment('IN_PROGRESS', {
      type: 'GITHUB',
    });
    delete activeGitHubDeployment.statusUpdatedAt;
    setEnvironment(activeGitHubDeployment);

    expect(component.isDeploymentInProgress()).toBe(true);
    expect(component.canUnlock()).toBe(true);
    expect(component.getUnlockToolTip()).toBe('Unlock Environment');
  });

  it('blocks unlock for active Helios-backed GitHub deployments with a status update timestamp', () => {
    setEnvironment(
      deployment('IN_PROGRESS', {
        type: 'GITHUB',
      })
    );

    expect(component.isDeploymentInProgress()).toBe(true);
    expect(component.canUnlock()).toBe(false);
    expect(component.getUnlockToolTip()).toBe('Cancel the ongoing deployment before unlocking this environment.');
  });

  it('allows the lock owner to unlock stale active deployments', () => {
    isMaintainer = false;
    currentGithubId = '1';
    setEnvironment(
      deployment('IN_PROGRESS', {
        statusUpdatedAt: new Date(Date.now() - 21 * 60 * 1000).toISOString(),
      })
    );

    expect(component.canUnlock()).toBe(true);
    expect(component.getUnlockToolTip()).toBe('Deployment appears stale. You can unlock this environment.');
  });

  it('allows maintainers to unlock stale active deployments on another user’s lock', () => {
    isMaintainer = true;
    currentGithubId = '2';
    setEnvironment(
      deployment('IN_PROGRESS', {
        statusUpdatedAt: new Date(Date.now() - 21 * 60 * 1000).toISOString(),
      })
    );

    expect(component.canUnlock()).toBe(true);
    expect(component.getUnlockToolTip()).toBe('Deployment appears stale. You can unlock this environment.');
  });

  it('does not disable lock extension when an active deployment blocks unlock', () => {
    setEnvironment(deployment('IN_PROGRESS'));

    expect(component.canUnlock()).toBe(false);
    expect(component.canExtendLock()).toBe(true);
  });
});
