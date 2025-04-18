import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BranchDetailsComponent } from './branch-details.component';
import { importProvidersFrom, signal } from '@angular/core';
import { vi } from 'vitest';
import { TestModule } from '@app/test.module';

describe('Integration Test Branch Details Page', () => {
  let component: BranchDetailsComponent;
  let fixture: ComponentFixture<BranchDetailsComponent>;

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
      imports: [BranchDetailsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(BranchDetailsComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('branchName', 'branch');

    // Mock tanstack query data
    component.query = {
      ...component.query,
      data: signal({ name: 'branch', commitSha: '' }),
    };

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
