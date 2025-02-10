import {Component, computed, inject, input, signal} from '@angular/core';
import { Avatar } from 'primeng/avatar';
import { Divider } from 'primeng/divider';
import { HeliosIconComponent } from '@app/components/helios-icon/helios-icon.component';
import { ProfileNavSectionComponent } from '@app/components/profile-nav-section/profile-nav-section.component';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {NgClass, NgIf, SlicePipe} from '@angular/common';
import { TablerIconComponent } from 'angular-tabler-icons';
import { Tooltip } from 'primeng/tooltip';
import { UserLockInfoComponent } from '@app/components/user-lock-info/user-lock-info.component';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import {Button} from 'primeng/button';

@Component({
  selector: 'app-navigation-bar',
  imports: [Avatar, Divider, HeliosIconComponent, ProfileNavSectionComponent, RouterLink, SlicePipe, TablerIconComponent, Tooltip, UserLockInfoComponent, RouterLinkActive, NgClass],
  templateUrl: './navigation-bar.component.html',
})
export class NavigationBarComponent {
  private keycloakService = inject(KeycloakService);
  private permissionService = inject(PermissionService);

  // Toggle sidebar state
  isExpanded = signal(false);

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
        label: 'Release Management',
        icon: 'rocket',
        path: ['repo', this.repositoryId(), 'release'],
      },
      {
        label: 'Environments',
        icon: 'server-cog',
        path: ['repo', this.repositoryId(), 'environment'],
      },
      ...(this.keycloakService.profile && this.permissionService.isAtLeastMaintainer()
        ? [
            {
              label: 'Project Settings',
              icon: 'adjustments-alt',
              path: ['repo', this.repositoryId(), 'settings'],
            },
          ]
        : []),
    ];
  });

  login() {
    this.keycloakService.login();
  }

  toggleSidebar() {
    this.isExpanded.set(!this.isExpanded());
  }
}
