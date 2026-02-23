import { KeycloakService } from './keycloak.service';

describe('KeycloakService', () => {
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
});
