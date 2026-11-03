import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { KeycloakService } from '../services/keycloak/keycloak.service';
import { PermissionService } from '../services/permission.service';
import { loggedInGuard } from './auth.guard';
import { adminGuard } from './admin.guard';
import { maintainerGuard } from './maintainer.guard';

describe('route access guards', () => {
  let navigate: ReturnType<typeof vi.fn>;
  let isLoggedIn: ReturnType<typeof vi.fn>;
  let isAdmin: ReturnType<typeof vi.fn>;
  let isAtLeastMaintainer: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    navigate = vi.fn();
    isLoggedIn = vi.fn();
    isAdmin = vi.fn();
    isAtLeastMaintainer = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Router, useValue: { navigate } },
        { provide: KeycloakService, useValue: { isLoggedIn } },
        { provide: PermissionService, useValue: { isAdmin, isAtLeastMaintainer } },
      ],
    });
  });

  it.each([false, undefined])('redirects users without an established login (%s)', authenticated => {
    isLoggedIn.mockReturnValue(authenticated);

    expect(TestBed.runInInjectionContext(() => loggedInGuard({} as never, {} as never))).toBe(false);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(['/unauthorized']);
  });

  it('allows an authenticated user without redirecting', () => {
    isLoggedIn.mockReturnValue(true);

    expect(TestBed.runInInjectionContext(() => loggedInGuard({} as never, {} as never))).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each([
    ['administrator', adminGuard],
    ['maintainer', maintainerGuard],
  ] as const)('enforces %s access without redirecting authorized users', (_, guard) => {
    isAdmin.mockReturnValue(true);
    isAtLeastMaintainer.mockReturnValue(true);

    expect(TestBed.runInInjectionContext(() => guard({} as never, {} as never))).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each([
    ['administrator', adminGuard],
    ['maintainer', maintainerGuard],
  ] as const)('redirects users without %s access', (_, guard) => {
    isAdmin.mockReturnValue(false);
    isAtLeastMaintainer.mockReturnValue(false);

    expect(TestBed.runInInjectionContext(() => guard({} as never, {} as never))).toBe(false);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(['/unauthorized']);
  });

  it('requires administrator access even when a user is a maintainer', () => {
    isAdmin.mockReturnValue(false);
    isAtLeastMaintainer.mockReturnValue(true);

    expect(TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never))).toBe(false);
    expect(TestBed.runInInjectionContext(() => maintainerGuard({} as never, {} as never))).toBe(true);
    expect(navigate).toHaveBeenCalledTimes(1);
  });
});
