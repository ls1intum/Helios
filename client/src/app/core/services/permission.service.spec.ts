import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { getUserPermissionsQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from './keycloak/keycloak.service';
import { PermissionService } from './permission.service';
import { RepositoryService } from './repository.service';

describe('PermissionService', () => {
  let queryClient: QueryClient;
  let loggedIn: ReturnType<typeof signal<boolean>>;
  let profile: ReturnType<typeof signal<{ username?: string } | undefined>>;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
    loggedIn = signal(true);
    profile = signal<{ username?: string } | undefined>(undefined);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideQueryClient(queryClient),
        RepositoryService,
        {
          provide: KeycloakService,
          useValue: {
            isLoggedIn: () => loggedIn(),
            get profile() {
              return profile();
            },
          },
        },
      ],
    });
  });

  afterEach(() => queryClient.clear());

  it.each([
    ['read', { permission: 'READ' }, false, false, false],
    ['write', { permission: 'WRITE' }, true, false, false],
    ['maintain', { permission: 'WRITE', roleName: 'maintain' }, true, true, false],
    ['admin', { permission: 'ADMIN' }, true, true, true],
    ['unknown', { permission: 'UNKNOWN' }, false, false, false],
  ])('grants only the capabilities of the %s role', (_, permissions, write, maintain, admin) => {
    queryClient.setQueryData(getUserPermissionsQueryKey(), permissions);
    const service = TestBed.inject(PermissionService);
    TestBed.tick();

    expect(service.hasWritePermission()).toBe(write);
    expect(service.isAtLeastMaintainer()).toBe(maintain);
    expect(service.isAdmin()).toBe(admin);
  });

  it('does not grant access before permissions or a user profile are available', () => {
    const service = TestBed.inject(PermissionService);
    TestBed.tick();

    expect(service.isHeliosDeveloper()).toBe(false);
    expect(service.hasWritePermission()).toBe(false);
    expect(service.isAtLeastMaintainer()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('recognizes configured developer usernames case-insensitively', () => {
    const service = TestBed.inject(PermissionService);
    profile.set({ username: service.heliosDevelopers[0].toUpperCase() });
    TestBed.tick();

    expect(service.isHeliosDeveloper()).toBe(true);
    expect(service.hasWritePermission()).toBe(true);
    expect(service.isAtLeastMaintainer()).toBe(true);
    expect(service.isAdmin()).toBe(true);

    profile.set({ username: 'unlisted-developer' });
    expect(service.isHeliosDeveloper()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('revokes every privileged capability on logout despite cached administrator permissions', () => {
    queryClient.setQueryData(getUserPermissionsQueryKey(), { permission: 'ADMIN' });
    const service = TestBed.inject(PermissionService);
    TestBed.tick();
    expect(service.isAdmin()).toBe(true);

    loggedIn.set(false);

    expect(service.hasWritePermission()).toBe(false);
    expect(service.isAtLeastMaintainer()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });

  it('never grants configured developer privileges while logged out', () => {
    const service = TestBed.inject(PermissionService);
    profile.set({ username: service.heliosDevelopers[0] });
    loggedIn.set(false);
    TestBed.tick();

    expect(service.hasWritePermission()).toBe(false);
    expect(service.isAtLeastMaintainer()).toBe(false);
    expect(service.isAdmin()).toBe(false);
  });
});
