import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { getAllEnabledEnvironmentsQueryKey, getAllEnvironmentsQueryKey, getEnvironmentReviewersQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { ConfirmationService, MessageService } from 'primeng/api';
import { EnvironmentListViewComponent } from './environment-list-view.component';

describe('EnvironmentListViewComponent', () => {
  let fixture: ComponentFixture<EnvironmentListViewComponent>;
  let queryClient: QueryClient;
  const admin = signal(false);
  const environment = (id: number, name: string, overrides: Partial<EnvironmentDto> = {}): EnvironmentDto => ({
    id,
    name,
    enabled: true,
    locked: false,
    type: 'TEST',
    ...overrides,
  });
  const environments: EnvironmentDto[] = [
    environment(1, 'test-alpha', { displayName: 'Shared Test', installedApps: ['Artemis'] }),
    environment(2, 'staging-beta', { type: 'STAGING', locked: true }),
    environment(3, 'production-gamma', { type: 'PRODUCTION' }),
  ];

  beforeEach(async () => {
    admin.set(false);
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
    for (const id of [1, 2, 3, 4]) queryClient.setQueryData(getEnvironmentReviewersQueryKey({ path: { environmentId: id } }), { reviewers: [] });
    queryClient.setQueryData(getAllEnabledEnvironmentsQueryKey(), environments);
    queryClient.setQueryData(getAllEnvironmentsQueryKey(), [...environments, environment(4, 'disabled-delta', { enabled: false })]);
    await TestBed.configureTestingModule({
      imports: [EnvironmentListViewComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideRouter([]),
        provideQueryClient(queryClient),
        ConfirmationService,
        MessageService,
        { provide: KeycloakService, useValue: { isLoggedIn: () => true, getUserGithubId: () => '10', getPreferredUsername: () => 'alice', isCurrentUser: () => false } },
        { provide: PermissionService, useValue: { isAdmin: () => admin(), isAtLeastMaintainer: () => admin(), hasWritePermission: () => true } },
        { provide: DateService, useValue: { formatDate: () => 'Formatted date' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EnvironmentListViewComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => queryClient.clear());

  const host = () => fixture.nativeElement as HTMLElement;
  const names = () => Array.from(host().querySelectorAll('app-environment-accordion')).map(element => element.querySelector('p-accordion-header p-button')?.textContent?.trim());
  async function search(value: string) {
    const input = host().querySelector('input[placeholder="Search environments or installed apps"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }
  async function selectFilter(label: string) {
    const filterButton = host().querySelector('i-tabler[name="filter-plus"], i-tabler[name="filter"]')!.closest('button')!;
    filterButton.click();
    await fixture.whenStable();
    const button = Array.from(document.querySelectorAll('.p-popover button')).find(element => element.textContent?.trim() === label) as HTMLButtonElement;
    expect(button).toBeTruthy();
    button.click();
    await fixture.whenStable();
  }

  it('groups and renders enabled environments in test, staging, then production order', () => {
    expect(names()).toEqual(['Shared Test', 'staging-beta', 'production-gamma']);
    expect(host().textContent).toContain('Go to Environments');
    expect(host().textContent).not.toContain('disabled-delta');
  });

  it.each([
    ['ALPHA', ['Shared Test']],
    ['shared', ['Shared Test']],
    ['ARTEMIS', ['Shared Test']],
    ['gamma', ['production-gamma']],
  ])('filters rendered environments by case-insensitive name, display name, or app: %s', async (term, expected) => {
    await search(term);
    expect(names()).toEqual(expected);
  });

  it('restores every enabled environment when the search is cleared', async () => {
    await search('alpha');
    await search('');
    expect(names()).toHaveLength(3);
  });

  it('explains when no environments match the search', async () => {
    await search('does-not-exist');
    expect(names()).toEqual([]);
    expect(host().textContent).toContain('No environments found for the search criteria');
  });

  it('filters out locks held by other users through the availability popover', async () => {
    await selectFilter('Available Only');
    expect(names()).toEqual(['Shared Test', 'production-gamma']);
    expect(host().querySelector('i-tabler[name="filter"]')).toBeTruthy();
    await selectFilter('All Environments');
    expect(names()).toHaveLength(3);
    expect(host().querySelector('i-tabler[name="filter-plus"]')).toBeTruthy();
  });

  it('combines environment type and availability filters', async () => {
    await selectFilter('Staging');
    expect(names()).toEqual(['staging-beta']);
    await selectFilter('Available Only');
    expect(names()).toEqual([]);
    expect(host().textContent).toContain('No available staging environments found.');
  });

  it('shows the type-specific empty state and restores all types', async () => {
    queryClient.setQueryData(
      getAllEnabledEnvironmentsQueryKey(),
      environments.filter(item => item.type !== 'PRODUCTION')
    );
    await selectFilter('Production');
    expect(host().textContent).toContain('No production environments found.');
    await selectFilter('All Types');
    expect(names()).toEqual(['Shared Test', 'staging-beta']);
  });

  it('keeps the current user’s locked environment visible despite search and type filters', async () => {
    queryClient.setQueryData(getAllEnabledEnvironmentsQueryKey(), [
      ...environments,
      environment(4, 'my-locked-server', { locked: true, type: 'STAGING', lockedBy: { id: 10, login: 'alice', name: 'Alice', avatarUrl: '', htmlUrl: '' } }),
    ]);
    await selectFilter('Production');
    await selectFilter('Available Only');
    await search('no-match');
    expect(names()).toEqual(['my-locked-server']);
    expect(host().textContent).toContain('Locked by you');
    expect(host().querySelector('.ring-2.ring-blue-500')).toBeTruthy();
  });

  it('shows the initial empty state when the server returns no environments', async () => {
    queryClient.setQueryData(getAllEnabledEnvironmentsQueryKey(), []);
    await vi.waitFor(() => expect(host().textContent).toContain('No environments found. Create a new environment to get started.'));
  });

  it('explains when every environment is locked', async () => {
    queryClient.setQueryData(getAllEnabledEnvironmentsQueryKey(), [environments[1]]);
    await selectFilter('Available Only');
    expect(host().textContent).toContain('No available (unlocked) environments found.');
  });

  it('loads disabled environments only in the editable administrator view', async () => {
    admin.set(true);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
    await fixture.whenStable();
    await vi.waitFor(() => expect(names()).toContain('disabled-delta'));
    expect(host().textContent).toContain('Disabled');
    expect(host().textContent).toContain('Manage Environments');
  });

  it('hides the list navigation link when embedded in the environment management page', async () => {
    fixture.componentRef.setInput('hideLinkToList', true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(host().textContent).not.toContain('Go to Environments');
    expect(host().textContent).not.toContain('Sync Environments');
    admin.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(host().textContent).toContain('Sync Environments');
  });
});
