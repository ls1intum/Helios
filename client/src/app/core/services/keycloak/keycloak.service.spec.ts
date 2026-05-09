import { KeycloakService } from './keycloak.service';
import { environment } from 'environments/environment';

describe('KeycloakService', () => {
  const originalSkipLoginPage = environment.keycloak.skipLoginPage;
  const AUTH_SYNC_STORAGE_KEY = 'helios:auth:sync';

  afterEach(() => {
    environment.keycloak.skipLoginPage = originalSkipLoginPage;
  });

  it('should read tokenParsed claims and handle missing tokenParsed', () => {
    const service = new KeycloakService();
    const keycloakStub = {
      token: 'header.payload.signature',
      tokenParsed: {
        github_id: 123456,
        preferred_username: 'octocat',
      },
    };
    (service as unknown as { _keycloak: unknown })._keycloak = keycloakStub;

    expect(service.getUserGithubId()).toBe(123456);
    expect(service.getPreferredUsername()).toBe('octocat');
    const keycloakWithoutParsedStub = {
      token: 'header.a-b_.signature',
      tokenParsed: undefined,
    };
    (service as unknown as { _keycloak: unknown })._keycloak = keycloakWithoutParsedStub;

    expect(() => service.getUserGithubId()).not.toThrow();
    expect(service.getUserGithubId()).toBeUndefined();
  });

  it('should login directly with GitHub when the login page is skipped', () => {
    const service = new KeycloakService();
    const login = vi.fn();
    environment.keycloak.skipLoginPage = true;
    (service as unknown as { _keycloak: unknown })._keycloak = { login };

    service.login();

    expect(login).toHaveBeenCalledTimes(1);
    expect(login).toHaveBeenCalledWith({ idpHint: 'github' });
  });

  it('should show the Keycloak login page when the login page is not skipped', () => {
    const service = new KeycloakService();
    const login = vi.fn();
    environment.keycloak.skipLoginPage = false;
    (service as unknown as { _keycloak: unknown })._keycloak = { login };

    service.login();

    expect(login).toHaveBeenCalledTimes(1);
    expect(login).toHaveBeenCalledWith();
  });

  it('should broadcast and redirect on manual logout without pre-clearing local auth state', () => {
    const service = new KeycloakService();
    const logout = vi.fn();
    const localStorageSpy = vi.spyOn(window.localStorage, 'setItem');

    (service as unknown as { _keycloak: unknown })._keycloak = { logout };
    (service as unknown as { _isLoggedIn: { set: (value: boolean) => void } })._isLoggedIn.set(true);

    service.logout();

    expect(service.isLoggedIn()).toBe(true);
    expect(localStorageSpy).toHaveBeenCalledWith(AUTH_SYNC_STORAGE_KEY, expect.any(String));
    expect(logout).toHaveBeenCalledWith({ redirectUri: window.location.href });

    localStorageSpy.mockRestore();
  });

  it('should react to cross-tab logout via storage event', () => {
    const service = new KeycloakService();
    const clearToken = vi.fn();

    (service as unknown as { _keycloak: unknown })._keycloak = { clearToken };
    (service as unknown as { _isLoggedIn: { set: (value: boolean) => void } })._isLoggedIn.set(true);

    window.dispatchEvent(
      new StorageEvent('storage', {
        key: AUTH_SYNC_STORAGE_KEY,
        newValue: JSON.stringify({ type: 'logout', at: Date.now() }),
      })
    );

    expect(service.isLoggedIn()).toBe(false);
    expect(clearToken).toHaveBeenCalledTimes(1);
  });

  it('should clear local auth state and broadcast after max consecutive refresh attempts with auth refresh errors', async () => {
    vi.useFakeTimers();
    const service = new KeycloakService();
    const clearToken = vi.fn();
    const localStorageSpy = vi.spyOn(window.localStorage, 'setItem');

    let keycloakStub: {
      clearToken: () => void;
      updateToken: ReturnType<typeof vi.fn>;
      authenticated: boolean;
      token: string | undefined;
      onAuthRefreshError?: () => void;
    };
    keycloakStub = {
      clearToken,
      updateToken: vi.fn().mockImplementation(() => {
        keycloakStub.onAuthRefreshError?.();
        return Promise.reject(new Error('refresh error'));
      }),
      authenticated: false,
      token: undefined,
    };

    (service as unknown as { _keycloak: unknown })._keycloak = keycloakStub;
    (service as unknown as { _isLoggedIn: { set: (value: boolean) => void } })._isLoggedIn.set(true);

    (service as unknown as { bindKeycloakEvents: () => void }).bindKeycloakEvents();
    (service as unknown as { startTokenRefresh: () => void }).startTokenRefresh();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(true);
    expect(clearToken).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(true);
    expect(clearToken).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(false);
    expect(clearToken).toHaveBeenCalledTimes(1);
    expect(localStorageSpy).toHaveBeenCalledWith(AUTH_SYNC_STORAGE_KEY, expect.any(String));

    localStorageSpy.mockRestore();
    vi.useRealTimers();
  });

  it('should only logout after max consecutive token refresh failures', async () => {
    vi.useFakeTimers();
    const service = new KeycloakService();
    const clearToken = vi.fn();
    const localStorageSpy = vi.spyOn(window.localStorage, 'setItem');

    const keycloakStub = {
      clearToken,
      updateToken: vi.fn().mockRejectedValue(new Error('network error')),
      authenticated: true,
      token: 'token',
    };

    (service as unknown as { _keycloak: unknown })._keycloak = keycloakStub;
    (service as unknown as { _isLoggedIn: { set: (value: boolean) => void } })._isLoggedIn.set(true);

    (service as unknown as { startTokenRefresh: () => void }).startTokenRefresh();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(true);
    expect(clearToken).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(true);
    expect(clearToken).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(false);
    expect(clearToken).toHaveBeenCalledTimes(1);
    expect(localStorageSpy).toHaveBeenCalledWith(AUTH_SYNC_STORAGE_KEY, expect.any(String));

    localStorageSpy.mockRestore();
    vi.useRealTimers();
  });

  it('should not double-count a refresh failure when onAuthRefreshError fires after updateToken rejection', async () => {
    vi.useFakeTimers();
    const service = new KeycloakService();
    const clearToken = vi.fn();
    const localStorageSpy = vi.spyOn(window.localStorage, 'setItem');

    const keycloakStub = {
      clearToken,
      updateToken: vi.fn().mockRejectedValue(new Error('network error')),
      authenticated: true,
      token: 'token',
    };

    (service as unknown as { _keycloak: unknown })._keycloak = keycloakStub;
    (service as unknown as { _isLoggedIn: { set: (value: boolean) => void } })._isLoggedIn.set(true);
    (service as unknown as { bindKeycloakEvents: () => void }).bindKeycloakEvents();
    (service as unknown as { startTokenRefresh: () => void }).startTokenRefresh();

    await vi.advanceTimersByTimeAsync(60000);
    (keycloakStub as unknown as { onAuthRefreshError?: () => void }).onAuthRefreshError?.();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(true);
    expect(clearToken).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(60000);
    expect(service.isLoggedIn()).toBe(false);
    expect(clearToken).toHaveBeenCalledTimes(1);
    expect(localStorageSpy).toHaveBeenCalledWith(AUTH_SYNC_STORAGE_KEY, expect.any(String));

    localStorageSpy.mockRestore();
    vi.useRealTimers();
  });
});
