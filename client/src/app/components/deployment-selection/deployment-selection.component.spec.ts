import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DeploymentSelectionComponent } from './deployment-selection.component';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { ConfirmationService } from 'primeng/api';
import { EnvironmentListViewComponent } from '../environments/environment-list/environment-list-view.component';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { PermissionService } from '@app/core/services/permission.service';
import { DatePipe } from '@angular/common';
import { provideTablerIcons } from 'angular-tabler-icons';
import { IconRefresh, IconServerCog } from 'angular-tabler-icons/icons';

// Mock KeycloakService
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

// Mock Router
const mockRouter = {
  navigate: vi.fn(),
  events: {
    subscribe: vi.fn(),
  },
};

// Mock PermissionService
const mockPermissionService = {
  isAdmin: vi.fn().mockReturnValue(true),
  isAtLeastMaintainer: vi.fn().mockReturnValue(true),
  hasWritePermission: vi.fn().mockReturnValue(true),
};

describe('DeploymentSelectionComponent', () => {
  let component: DeploymentSelectionComponent;
  let fixture: ComponentFixture<DeploymentSelectionComponent>;
  let messageService: MessageService;
  let queryClient: QueryClient;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeploymentSelectionComponent, EnvironmentListViewComponent, RouterTestingModule],
      providers: [
        provideExperimentalZonelessChangeDetection(),
        provideQueryClient(new QueryClient()),
        MessageService,
        ConfirmationService,
        DatePipe,
        provideTablerIcons({ IconRefresh, IconServerCog }),
        { provide: KeycloakService, useValue: mockKeycloakService },
        { provide: Router, useValue: mockRouter },
        { provide: PermissionService, useValue: mockPermissionService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              params: {},
              queryParams: {},
            },
            params: {
              subscribe: vi.fn(),
            },
            queryParams: {
              subscribe: vi.fn(),
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeploymentSelectionComponent);
    component = fixture.componentInstance;
    messageService = TestBed.inject(MessageService);
    queryClient = TestBed.inject(QueryClient);

    // Set required inputs using direct property assignment
    Object.defineProperty(component, 'sourceRef', {
      get: () => () => 'main',
    });
    Object.defineProperty(component, 'commitSha', {
      get: () => () => 'abc123',
    });

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have required inputs set', () => {
    expect(component.sourceRef()).toBe('main');
    expect(component.commitSha()).toBe('abc123');
  });

  it('should render environment list view component', () => {
    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('app-environment-list-view')).toBeTruthy();
  });
});
