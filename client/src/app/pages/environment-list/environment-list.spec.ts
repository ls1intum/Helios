import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnvironmentListComponent } from './environment-list.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { vi } from 'vitest';

describe('Integration Test Environment List Page', () => {
  let component: EnvironmentListComponent;
  let fixture: ComponentFixture<EnvironmentListComponent>;

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
      imports: [EnvironmentListComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentListComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
