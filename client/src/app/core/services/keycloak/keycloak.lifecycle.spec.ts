import { KeycloakService } from './keycloak.service';
import type Keycloak from 'keycloak-js';

const adapter = {
  authenticated: true,
  token: 'initial-access-token' as string | undefined,
  tokenParsed: undefined as { sub?: string; preferred_username?: string; github_id?: string | number } | undefined,
  init: vi.fn(),
  loadUserProfile: vi.fn(),
  updateToken: vi.fn(),
  clearToken: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  onAuthSuccess: undefined as (() => Promise<void>) | undefined,
  onAuthRefreshSuccess: undefined as (() => void) | undefined,
  onAuthRefreshError: undefined as (() => void) | undefined,
  onAuthLogout: undefined as (() => void) | undefined,
};

describe('Keycloak authentication lifecycle', () => {
  let service: KeycloakService;
  let storageListeners: EventListenerOrEventListenerObject[];
  const user = { id: 'user-1', username: 'octocat', firstName: 'Octo', lastName: 'Cat' };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    adapter.authenticated = true;
    adapter.token = 'initial-access-token';
    adapter.tokenParsed = { sub: 'user-1', preferred_username: 'octocat', github_id: 123 };
    adapter.init.mockResolvedValue(true);
    adapter.loadUserProfile.mockResolvedValue(user);
    adapter.updateToken.mockResolvedValue(true);
    adapter.onAuthSuccess = undefined;
    adapter.onAuthRefreshSuccess = undefined;
    adapter.onAuthRefreshError = undefined;
    adapter.onAuthLogout = undefined;
    storageListeners = [];
    const originalAddEventListener = window.addEventListener.bind(window);
    vi.spyOn(window, 'addEventListener').mockImplementation((name, listener, options) => {
      if (name === 'storage') storageListeners.push(listener);
      originalAddEventListener(name, listener, options);
    });
    service = new KeycloakService();
    // Angular shares modules between test files; mock the public adapter boundary
    // instead of relying on a module mock that depends on import order.
    vi.spyOn(service, 'keycloak', 'get').mockReturnValue(adapter as unknown as Keycloak);
  });

  afterEach(() => {
    adapter.onAuthLogout?.();
    storageListeners.forEach(listener => window.removeEventListener('storage', listener));
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('initializes SSO and exposes the loaded user profile and access token', async () => {
    await service.init();

    expect(adapter.init).toHaveBeenCalledExactlyOnceWith({ onLoad: 'check-sso' });
    expect(adapter.loadUserProfile).toHaveBeenCalledTimes(1);
    expect(service.isLoggedIn()).toBe(true);
    expect(service.profile).toEqual({ ...user, token: 'initial-access-token' });
    expect(service.getUserId()).toBe('user-1');
    expect(service.getPreferredUsername()).toBe('octocat');
    expect(service.isCurrentUser('OCTOCAT')).toBe(true);
    expect(service.isCurrentUser('someone-else')).toBe(false);
    expect(service.getUserGithubProfileUrl()).toBe('https://github.com/octocat');
    expect(service.getUserGithubProfilePictureUrl()).toBe('https://avatars.githubusercontent.com/u/123');
  });

  it('keeps unauthenticated SSO results logged out without profile requests or refresh timers', async () => {
    adapter.init.mockResolvedValue(false);

    await service.init();
    await vi.advanceTimersByTimeAsync(180000);

    expect(service.isLoggedIn()).toBe(false);
    expect(service.profile).toBeUndefined();
    expect(adapter.loadUserProfile).not.toHaveBeenCalled();
    expect(adapter.updateToken).not.toHaveBeenCalled();
  });

  it('refreshes an authenticated session once per minute with the expected minimum token validity', async () => {
    await service.init();
    await vi.advanceTimersByTimeAsync(59999);
    expect(adapter.updateToken).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1);
    expect(adapter.updateToken).toHaveBeenCalledExactlyOnceWith(60);

    await vi.advanceTimersByTimeAsync(60000);
    expect(adapter.updateToken).toHaveBeenCalledTimes(2);
  });

  it('does not accumulate refresh timers when authentication initialization repeats', async () => {
    await service.init();
    await service.init();
    await vi.advanceTimersByTimeAsync(60000);

    expect(adapter.updateToken).toHaveBeenCalledTimes(1);
  });

  it('loads a new profile when the adapter reports a successful login', async () => {
    adapter.init.mockResolvedValue(false);
    await service.init();
    adapter.loadUserProfile.mockResolvedValue({ ...user, username: 'new-user' });

    await adapter.onAuthSuccess?.();

    expect(service.isLoggedIn()).toBe(true);
    expect(service.profile?.username).toBe('new-user');
    await vi.advanceTimersByTimeAsync(60000);
    expect(adapter.updateToken).toHaveBeenCalledTimes(1);
  });

  it('updates the public profile token after a successful token refresh', async () => {
    await service.init();
    adapter.token = 'refreshed-access-token';

    adapter.onAuthRefreshSuccess?.();

    expect(service.isLoggedIn()).toBe(true);
    expect(service.profile?.token).toBe('refreshed-access-token');
  });

  it('clears the profile and stops refreshing when the identity provider logs out', async () => {
    await service.init();

    adapter.onAuthLogout?.();
    await vi.advanceTimersByTimeAsync(120000);

    expect(service.isLoggedIn()).toBe(false);
    expect(service.profile).toBeUndefined();
    expect(adapter.updateToken).not.toHaveBeenCalled();
  });

  it('ignores unrelated or malformed cross-tab storage events', async () => {
    await service.init();
    window.dispatchEvent(new StorageEvent('storage', { key: 'another-key', newValue: '{"type":"logout"}' }));
    window.dispatchEvent(new StorageEvent('storage', { key: 'helios:auth:sync', newValue: null }));
    window.dispatchEvent(new StorageEvent('storage', { key: 'helios:auth:sync', newValue: 'not-json' }));
    window.dispatchEvent(new StorageEvent('storage', { key: 'helios:auth:sync', newValue: '{"type":"login"}' }));

    expect(service.isLoggedIn()).toBe(true);
    expect(adapter.clearToken).not.toHaveBeenCalled();
  });

  it('does not construct GitHub profile links or user IDs from missing claims', () => {
    adapter.tokenParsed = undefined;

    expect(service.getUserId()).toBeUndefined();
    expect(service.getUserGithubId()).toBeUndefined();
    expect(service.getUserGithubProfileUrl()).toBe('');
    expect(service.getUserGithubProfilePictureUrl()).toBe('');
    expect(service.isCurrentUser('octocat')).toBe(false);
  });
});
