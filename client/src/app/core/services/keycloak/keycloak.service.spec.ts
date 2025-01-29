import { TestBed } from '@angular/core/testing';

import { KeycloakService } from './keycloak.service';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';

describe('KeycloakService', () => {
  let service: KeycloakService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideExperimentalZonelessChangeDetection()],
    });
    service = TestBed.inject(KeycloakService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
