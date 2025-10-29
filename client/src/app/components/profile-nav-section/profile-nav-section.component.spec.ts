import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileNavSectionComponent } from './profile-nav-section.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { RouterTestingModule } from '@angular/router/testing';

const keycloakStub = {
  isLoggedIn: () => true,
  keycloak: { token: 'token' },
  getUserGithubProfilePictureUrl: () => 'https://example.com/avatar.png',
  getUserGithubProfileUrl: () => 'https://example.com/profile',
  logout: () => {},
  login: () => {},
  profile: { firstName: 'Test', lastName: 'User' },
};

describe('ProfileNavSectionComponent', () => {
  let component: ProfileNavSectionComponent;
  let fixture: ComponentFixture<ProfileNavSectionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileNavSectionComponent, RouterTestingModule],
      providers: [provideZonelessChangeDetection(), { provide: KeycloakService, useValue: keycloakStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileNavSectionComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isExpanded', true);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
