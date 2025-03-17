import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MainLayoutComponent } from './main-layout.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { vi } from 'vitest';

describe('Integration Test Main Layout', () => {
  let component: MainLayoutComponent;
  let fixture: ComponentFixture<MainLayoutComponent>;

  beforeEach(async () => {
    vi.mock('@app/core/services/keycloak/keycloak.service', () => {
      return {
        KeycloakService: vi.fn(() => ({
          keycloak: { token: 'token' },
          isLoggedIn: vi.fn(() => false),
        })),
      };
    });

    await TestBed.configureTestingModule({
      imports: [MainLayoutComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayoutComponent);
    component = fixture.componentInstance;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
