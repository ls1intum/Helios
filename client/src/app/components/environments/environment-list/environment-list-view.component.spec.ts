import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnvironmentListViewComponent } from './environment-list-view.component';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService, ConfirmationService } from 'primeng/api';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { RouterTestingModule } from '@angular/router/testing';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { DatePipe } from '@angular/common';
import { provideTablerIcons } from 'angular-tabler-icons';
import { IconRefresh, IconServerCog } from 'angular-tabler-icons/icons';

// Mock services
const mockKeycloakService = {
  isLoggedIn: vi.fn().mockReturnValue(true),
  getUserGithubId: vi.fn().mockReturnValue('123'),
  keycloak: {
    init: vi.fn().mockResolvedValue(true),
    login: vi.fn(),
    logout: vi.fn(),
    updateToken: vi.fn().mockResolvedValue(true),
    token: 'mock-token',
  },
};

const mockPermissionService = {
  isAdmin: vi.fn().mockReturnValue(true),
  isAtLeastMaintainer: vi.fn().mockReturnValue(true),
  hasWritePermission: vi.fn().mockImplementation((env: EnvironmentDto) => {
    if (!env) return false;
    return env.id === 1; // Only allow write permission for test environment
  }),
};

const mockMessageService = {
  add: vi.fn(),
  clear: vi.fn(),
};

const mockConfirmationService = {
  confirm: vi.fn(),
};

describe('EnvironmentListViewComponent', () => {
  let component: EnvironmentListViewComponent;
  let fixture: ComponentFixture<EnvironmentListViewComponent>;

  const mockEnvironments: EnvironmentDto[] = [
    {
      id: 1,
      name: 'test-env',
      type: 'TEST',
      locked: false,
      lockedAt: undefined,
      lockReservationWillExpireAt: undefined,
      latestDeployment: {
        id: 1,
        type: 'GITHUB',
        ref: 'main',
        updatedAt: new Date().toISOString(),
      },
    },
    {
      id: 2,
      name: 'staging-env',
      type: 'STAGING',
      locked: true,
      lockedAt: new Date().toISOString(),
      lockReservationWillExpireAt: new Date(Date.now() + 3600000).toISOString(),
      lockedBy: {
        id: 123,
        name: 'Test User',
        login: 'testuser',
        avatarUrl: 'https://example.com/avatar.png',
        htmlUrl: 'https://github.com/testuser',
      },
      latestDeployment: {
        id: 2,
        type: 'GITHUB',
        ref: 'staging',
        updatedAt: new Date().toISOString(),
      },
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentListViewComponent, RouterTestingModule],
      providers: [
        provideExperimentalZonelessChangeDetection(),
        provideQueryClient(new QueryClient()),
        { provide: MessageService, useValue: mockMessageService },
        { provide: ConfirmationService, useValue: mockConfirmationService },
        DatePipe,
        provideTablerIcons({ IconRefresh, IconServerCog }),
        { provide: KeycloakService, useValue: mockKeycloakService },
        { provide: PermissionService, useValue: mockPermissionService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentListViewComponent);
    component = fixture.componentInstance;

    // Mock the environment query
    vi.spyOn(component.environmentQuery, 'data').mockReturnValue(mockEnvironments);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should filter environments based on search input', () => {
    component.searchInput.set('test');
    expect(component.filteredEnvironments().length).toBe(1);
    expect(component.filteredEnvironments()[0].name).toBe('test-env');

    component.searchInput.set('staging');
    expect(component.filteredEnvironments().length).toBe(1);
    expect(component.filteredEnvironments()[0].name).toBe('staging-env');
  });

  it('should group environments by type', () => {
    const groups = component.environmentGroups();
    expect(groups.size).toBe(2);
    expect(groups.get('TEST')?.length).toBe(1);
    expect(groups.get('STAGING')?.length).toBe(1);
  });

  it('should handle environment lock', () => {
    const mutateSpy = vi.spyOn(component.lockEnvironmentMutation, 'mutate');

    mockConfirmationService.confirm.mockImplementation(config => {
      if (config.accept) {
        config.accept();
      }
    });

    component.lockEnvironment(mockEnvironments[0]);

    expect(mockConfirmationService.confirm).toHaveBeenCalled();
    expect(mutateSpy).toHaveBeenCalledWith({ path: { id: 1 } });
  });

  it('should handle environment unlock', () => {
    const mutateSpy = vi.spyOn(component.unlockEnvironment, 'mutate');
    const event = new Event('click');

    component.onUnlockEnvironment(event, mockEnvironments[1]);

    expect(mutateSpy).toHaveBeenCalledWith({ path: { id: 2 } });
  });

  it('should handle environment deployment', () => {
    const deploySpy = vi.spyOn(component.deploy, 'emit');

    mockConfirmationService.confirm.mockImplementation(config => {
      if (config.accept) {
        config.accept();
      }
    });

    component.deployEnvironment(mockEnvironments[0]);

    expect(mockConfirmationService.confirm).toHaveBeenCalled();
    expect(deploySpy).toHaveBeenCalledWith(mockEnvironments[0]);
  });

  it('should calculate time until reservation expires', () => {
    const timeMap = component.timeUntilReservationExpires();
    expect(timeMap.size).toBe(1); // Only staging env has a lock
    expect(timeMap.get(2)).toBeGreaterThan(0); // Should have time remaining
  });

  it('should cleanup interval on destroy', () => {
    const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
    component.ngOnDestroy();
    expect(clearIntervalSpy).toHaveBeenCalled();
  });
});
