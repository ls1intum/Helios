import { SlicePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ProfileNavSectionComponent } from '@app/components/profile-nav-section/profile-nav-section.component';
import { getEnvironmentsByUserLockingOptions, getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
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
import { HeliosIconComponent } from '../../components/helios-icon/helios-icon.component';
import { DateService } from '@app/core/services/date.service';

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
  ],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent implements OnInit, OnDestroy {
  private keycloakService = inject(KeycloakService);
  private permissionService = inject(PermissionService);
  dateService = inject(DateService);

  username = computed(() => (this.keycloakService.decodedToken()?.preferred_username || '') as string);

  repositoryId = input.required({ transform: numberAttribute });

  repositoryQuery = injectQuery(() => ({
    ...getRepositoryByIdOptions({ path: { id: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  lockQuery = injectQuery(() => ({
    ...getEnvironmentsByUserLockingOptions(),
    refetchInterval: 10000,
    enabled: () => !!this.keycloakService.isLoggedIn(),
  }));

  timeNow = signal<Date>(new Date());
  private intervalId?: ReturnType<typeof setInterval>;

  logout() {
    this.keycloakService.logout();
  }

  login() {
    this.keycloakService.login();
  }

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

  ngOnInit() {
    this.intervalId = setInterval(() => {
      this.timeNow.set(new Date());
    }, 1000);
  }

  ngOnDestroy() {
    // Clear the interval
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }
}
