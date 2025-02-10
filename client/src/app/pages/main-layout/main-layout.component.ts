import { NgClass, SlicePipe } from '@angular/common';
import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ProfileNavSectionComponent } from '@app/components/profile-nav-section/profile-nav-section.component';
import { getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DividerModule } from 'primeng/divider';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { HeliosIconComponent } from '@app/components/helios-icon/helios-icon.component';
import { UserLockInfoComponent } from '@app/components/user-lock-info/user-lock-info.component';
import { FooterComponent } from '@app/components/footer/footer.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    SlicePipe,
    ToastModule,
    RouterLinkActive,
    IconsModule,
    ButtonModule,
    TooltipModule,
    HeliosIconComponent,
    DividerModule,
    AvatarModule,
    CardModule,
    ProfileNavSectionComponent,
    UserLockInfoComponent,
    NgClass,
    FooterComponent,
  ],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent {
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
