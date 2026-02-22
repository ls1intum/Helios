import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { KeycloakService } from './keycloak.service';

describe('KeycloakService', () => {
  let service: KeycloakService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
    service = TestBed.inject(KeycloakService);
  });

  it('should read claims from tokenParsed', () => {
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
  });

  it('should return undefined when tokenParsed is missing', () => {
    const keycloakStub = {
      token: 'header.a-b_.signature',
      tokenParsed: undefined,
    };
    (service as unknown as { _keycloak: unknown })._keycloak = keycloakStub;

    expect(() => service.getUserGithubId()).not.toThrow();
    expect(service.getUserGithubId()).toBeUndefined();
  });
});
