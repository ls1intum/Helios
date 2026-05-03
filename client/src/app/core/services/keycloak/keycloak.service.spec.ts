import { KeycloakService } from './keycloak.service';
import { environment } from 'environments/environment';

describe('KeycloakService', () => {
  const originalSkipLoginPage = environment.keycloak.skipLoginPage;

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
});
