import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { MessageService } from 'primeng/api';
import { KeycloakService } from './core/services/keycloak/keycloak.service';
import { vi } from 'vitest';

describe('AppComponent', () => {
  beforeEach(async () => {
    vi.mock('@app/core/services/keycloak/keycloak.service', () => {
      return {
        KeycloakService: vi.fn(() => ({
          keycloak: { token: 'token' },
        })),
      };
    });

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideZonelessChangeDetection(), MessageService, KeycloakService],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
