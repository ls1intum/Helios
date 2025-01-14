import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileNavSectionComponent } from './profile-nav-section.component';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';
import { vi } from 'vitest';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';

describe('ProfileNavSectionComponent', () => {
  let component: ProfileNavSectionComponent;
  let fixture: ComponentFixture<ProfileNavSectionComponent>;

  vi.mock('@app/core/services/keycloak/keycloak.service', () => {
    return {
      KeycloakService: vi.fn(() => ({
        isLoggedIn: vi.fn(),
        keycloak: { token: 'token' },
      })),
    };
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileNavSectionComponent],
      providers: [provideExperimentalZonelessChangeDetection(), KeycloakService],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileNavSectionComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
