import { DatePipe } from '@angular/common';
import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter, Router } from '@angular/router';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { getEnvironmentReviewersQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { AccordionModule } from 'primeng/accordion';
import { EnvironmentAccordionComponent } from './environment-accordion.component';

@Component({
  imports: [AccordionModule, EnvironmentAccordionComponent],
  template: `<p-accordion [value]="7">
    <app-environment-accordion
      [environment]="environment()"
      [deployable]="true"
      [canViewAllEnvironments]="true"
      [isLockedByCurrentUser]="lockedByMe()"
      (deploy)="deploy($event)"
      (lock)="lock($event)"
      (unlock)="unlock($event)"
      (extend)="extend($event)"
      (cancelDeployment)="cancel($event)"
    />
  </p-accordion>`,
})
class AccordionHostComponent {
  environment = signal<EnvironmentDto>({
    id: 7,
    name: 'staging',
    displayName: 'Staging environment',
    type: 'TEST',
    enabled: true,
    locked: false,
    serverUrl: 'staging.example.org',
    installedApps: ['Helios', 'Artemis'],
    repository: { id: 3, name: 'Helios', nameWithOwner: 'org/Helios', htmlUrl: '', updatedAt: '' },
  });
  lockedByMe = signal(false);
  deploy = vi.fn();
  lock = vi.fn();
  unlock = vi.fn();
  extend = vi.fn();
  cancel = vi.fn();
}

describe('EnvironmentAccordionComponent', () => {
  let fixture: ComponentFixture<AccordionHostComponent>;
  let queryClient: QueryClient;
  let open: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
    queryClient.setQueryData(getEnvironmentReviewersQueryKey({ path: { environmentId: 7 } }), { reviewers: [] });
    await TestBed.configureTestingModule({
      imports: [AccordionHostComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideQueryClient(queryClient),
        provideRouter([]),
        DatePipe,
        { provide: DateService, useValue: { formatDate: () => 'Formatted date', timeUntilExpire: () => '01:00:00' } },
        { provide: KeycloakService, useValue: { isLoggedIn: () => true, getUserGithubId: () => '1', getPreferredUsername: () => 'alice', isCurrentUser: () => true } },
        { provide: PermissionService, useValue: { hasWritePermission: () => true, isAtLeastMaintainer: () => true } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AccordionHostComponent);
    open = vi.spyOn(window, 'open').mockReturnValue(null);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    queryClient.clear();
    open.mockRestore();
  });

  const host = () => fixture.nativeElement as HTMLElement;
  const buttonWithText = (text: string) => Array.from(host().querySelectorAll<HTMLElement>('button, [role="button"]')).find(button => button.textContent?.trim() === text)!;
  const iconButton = (icon: string) => host().querySelector(`i-tabler[name="${icon}"]`)!.closest('button')!;
  const updateEnvironment = async (update: Partial<EnvironmentDto>) => {
    fixture.componentInstance.environment.update(environment => ({ ...environment, ...update }));
    fixture.detectChanges();
    await fixture.whenStable();
  };

  it('renders display name, lock state, installed apps, and activity/edit links', () => {
    expect(host().textContent).toContain('Staging environment');
    expect(host().textContent).toContain('Helios');
    expect(host().textContent).toContain('Artemis');
    expect(host().querySelector('a[href="/repo/3/environment/7/history"]')?.textContent).toContain('View Activity History');
    expect(host().querySelector('a[href="/repo/3/environment/7/edit"]')).toBeTruthy();
  });

  it('shows the internal environment name when no display name is configured', async () => {
    await updateEnvironment({ displayName: undefined });
    expect(buttonWithText('staging')).toBeTruthy();
  });

  it.each([
    ['staging.example.org', 'http://staging.example.org'],
    ['http://staging.example.org', 'http://staging.example.org'],
    ['https://staging.example.org', 'https://staging.example.org'],
  ])('opens the configured environment URL %s without closing the panel', async (serverUrl, expectedUrl) => {
    await updateEnvironment({ serverUrl });
    const header = host().querySelector('p-accordion-header')!;
    const onHeaderClick = vi.fn();
    header.addEventListener('click', onHeaderClick);
    buttonWithText('Staging environment').click();
    expect(open).toHaveBeenCalledWith(expectedUrl, '_blank');
    expect(onHeaderClick).not.toHaveBeenCalled();
  });

  it('does not open a window when no environment URL is configured', async () => {
    await updateEnvironment({ serverUrl: undefined });
    buttonWithText('Staging environment').click();
    expect(open).not.toHaveBeenCalled();
  });

  it('switches between the rendered deployment and details sections', async () => {
    expect(host().querySelector('app-deployment-stepper')).toBeTruthy();
    expect(host().querySelector('app-environment-details')).toBeNull();
    buttonWithText('Details').click();
    await fixture.whenStable();
    expect(host().querySelector('app-environment-details')).toBeTruthy();
    expect(host().querySelector('app-deployment-stepper')).toBeNull();
    buttonWithText('Deployment').click();
    await fixture.whenStable();
    expect(host().querySelector('app-deployment-stepper')).toBeTruthy();
  });

  it('forwards deploy and lock actions with the environment while stopping header clicks', () => {
    const onHeaderClick = vi.fn();
    host().querySelector('p-accordion-header')!.addEventListener('click', onHeaderClick);
    buttonWithText('Deploy').click();
    iconButton('lock').click();
    expect(fixture.componentInstance.deploy).toHaveBeenCalledWith(fixture.componentInstance.environment());
    expect(fixture.componentInstance.lock).toHaveBeenCalledWith(fixture.componentInstance.environment());
    expect(onHeaderClick).not.toHaveBeenCalled();
  });

  it('forwards unlock and extension actions with the original events and environment', async () => {
    await updateEnvironment({ locked: true, lockedBy: { id: 1, login: 'alice', name: 'Alice', avatarUrl: '', htmlUrl: '' } });
    iconButton('lock-open').click();
    iconButton('lock-plus').click();
    expect(fixture.componentInstance.unlock).toHaveBeenCalledWith({ event: expect.any(MouseEvent), environment: fixture.componentInstance.environment() });
    expect(fixture.componentInstance.extend).toHaveBeenCalledWith({ event: expect.any(MouseEvent), environment: fixture.componentInstance.environment() });
  });

  it('marks an environment locked by the current user', async () => {
    fixture.componentInstance.lockedByMe.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(host().textContent).toContain('Locked by you');
    expect(host().querySelector('app-lock-tag')).toBeNull();
  });

  it('navigates from deployed branches and pull requests to their repository pages', async () => {
    await updateEnvironment({ latestDeployment: { id: 1, type: 'HELIOS', state: 'SUCCESS', ref: 'feature/environment', pullRequestNumber: 12 } });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const branchTag = Array.from(host().querySelectorAll('p-tag')).find(tag => tag.textContent?.trim() === 'feature/environment') as HTMLElement;
    branchTag.click();
    expect(router.serializeUrl(navigate.mock.calls[0][0] as Parameters<Router['serializeUrl']>[0])).toBe('/repo/3/ci-cd/branch/feature%2Fenvironment');
    const prTag = Array.from(host().querySelectorAll('p-tag')).find(tag => tag.textContent?.trim() === '#12') as HTMLElement;
    prTag.click();
    expect(router.serializeUrl(navigate.mock.calls[1][0] as Parameters<Router['serializeUrl']>[0])).toBe('/repo/3/ci-cd/pr/12');
  });

  it.each([
    ['v1.2.3', undefined],
    ['1.2.3', undefined],
    ['release/next', ['v1.2.3-rc1']],
  ])('marks release deployments as tags without branch navigation: %s', async (ref, releaseCandidateNames) => {
    await updateEnvironment({ latestDeployment: { id: 1, type: 'HELIOS', state: 'SUCCESS', ref, releaseCandidateNames } });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const tag = Array.from(host().querySelectorAll('p-tag')).find(item => item.textContent?.trim() === ref) as HTMLElement;
    expect(tag.querySelector('i-tabler[name="tag"]')).toBeTruthy();
    tag.click();
    expect(navigate).not.toHaveBeenCalled();
  });
});
