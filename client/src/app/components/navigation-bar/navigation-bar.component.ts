import {Component, computed, inject, input, numberAttribute} from '@angular/core';
import {Avatar} from 'primeng/avatar';
import {Divider} from 'primeng/divider';
import {HeliosIconComponent} from '@app/components/helios-icon/helios-icon.component';
import {ProfileNavSectionComponent} from '@app/components/profile-nav-section/profile-nav-section.component';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {SlicePipe} from '@angular/common';
import {TablerIconComponent} from 'angular-tabler-icons';
import {Tooltip} from 'primeng/tooltip';
import {UserLockInfoComponent} from '@app/components/user-lock-info/user-lock-info.component';
import {KeycloakService} from '@app/core/services/keycloak/keycloak.service';
import {PermissionService} from '@app/core/services/permission.service';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {getRepositoryByIdOptions} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';

@Component({
  selector: 'app-navigation-bar',
  imports: [
    Avatar,
    Divider,
    HeliosIconComponent,
    ProfileNavSectionComponent,
    RouterLink,
    SlicePipe,
    TablerIconComponent,
    Tooltip,
    UserLockInfoComponent,
    RouterLinkActive
  ],
  templateUrl: './navigation-bar.component.html',
})
export class NavigationBarComponent {
  private keycloakService = inject(KeycloakService);
  private permissionService = inject(PermissionService);

  username = computed(() => (this.keycloakService.decodedToken()?.preferred_username || '') as string);

  repositoryId = input.required({ transform: numberAttribute });

  repositoryQuery = injectQuery(() => ({
    ...getRepositoryByIdOptions({ path: { id: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  logout() {
    this.keycloakService.logout();
  }

  login() {
    this.keycloakService.login();
  }

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

  items = computed(() => {
    const baseItems = [
      {
        label: 'CI/CD',
        icon: 'arrow-guide',
        path: 'ci-cd',
      },
      {
        label: 'Release Management',
        icon: 'rocket',
        path: 'release',
      },
      {
        label: 'Environments',
        icon: 'server-cog',
        path: 'environment',
      },
      ...(this.keycloakService.profile && this.permissionService.isAtLeastMaintainer()
        ? [
          {
            label: 'Project Settings',
            icon: 'adjustments-alt',
            path: 'settings',
          },
        ]
        : []),
    ];
    return baseItems;
  });
}
