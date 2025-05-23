import { Component, computed, inject, input, signal } from '@angular/core';
import { Avatar } from 'primeng/avatar';
import { Divider } from 'primeng/divider';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NgClass, SlicePipe } from '@angular/common';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { Tooltip } from 'primeng/tooltip';
import { UserLockInfoComponent } from '@app/components/user-lock-info/user-lock-info.component';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { ButtonModule } from 'primeng/button';
import { IconAdjustmentsAlt, IconArrowGuide, IconChevronLeft, IconChevronRight, IconRocket, IconServerCog, IconEyeOff, IconEye } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-navigation-bar',
  imports: [Avatar, Divider, RouterLink, ButtonModule, SlicePipe, TablerIconComponent, Tooltip, UserLockInfoComponent, RouterLinkActive, NgClass],
  providers: [
    provideTablerIcons({
      IconArrowGuide,
      IconServerCog,
      IconRocket,
      IconAdjustmentsAlt,
      IconChevronLeft,
      IconChevronRight,
      IconEyeOff,
      IconEye,
    }),
  ],
  templateUrl: './navigation-bar.component.html',
})
export class NavigationBarComponent {
  private keycloakService = inject(KeycloakService);
  private permissionService = inject(PermissionService);

  // Toggle sidebar state
  isExpanded = signal<boolean>(this.loadSidebarState());
  isVisible = signal<boolean>(true);

  repositoryId = input.required<number | undefined>();

  repositoryQuery = injectQuery(() => ({
    ...getRepositoryByIdOptions({ path: { id: this.repositoryId() ?? 0 } }),
    enabled: () => this.repositoryId() !== undefined,
  }));

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

  items = computed(() => {
    return [
      {
        label: 'CI/CD',
        icon: 'arrow-guide',
        path: ['repo', this.repositoryId(), 'ci-cd'],
      },
      {
        label: 'Environments',
        icon: 'server-cog',
        path: ['repo', this.repositoryId(), 'environment'],
      },
      ...(this.keycloakService.profile && this.permissionService.isAtLeastMaintainer()
        ? [
            {
              label: 'Release Management',
              icon: 'rocket',
              path: ['repo', this.repositoryId(), 'release'],
            },
            {
              label: 'Repository Settings',
              icon: 'adjustments-alt',
              path: ['repo', this.repositoryId(), 'settings'],
              showAtBottom: true,
            },
          ]
        : []),
    ];
  });

  bottomItems = computed(() => {
    return this.items().filter(item => item.showAtBottom);
  });

  login() {
    this.keycloakService.login();
  }

  toggleSidebarExpansion() {
    const newState = !this.isExpanded();
    this.isExpanded.set(newState);
    this.saveSidebarState(newState);
  }

  toggleSidebarVisibility() {
    const newState = !this.isVisible();
    this.isVisible.set(newState);
  }

  private saveSidebarState(state: boolean) {
    localStorage.setItem('sidebarExpanded', JSON.stringify(state));
  }

  private loadSidebarState(): boolean {
    return JSON.parse(localStorage.getItem('sidebarExpanded') || 'false');
  }
}
