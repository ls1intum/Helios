import { Component, computed, inject, input, output, signal } from '@angular/core';
import { AccordionModule } from 'primeng/accordion';

import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EnvironmentDto } from '@app/core/modules/openapi';
import {
  getAllEnabledEnvironmentsOptions,
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsOptions,
  getAllEnvironmentsQueryKey,
  getEnvironmentsByUserLockingQueryKey,
  unlockEnvironmentMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PermissionService } from '@app/core/services/permission.service';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { EnvironmentDeploymentInfoComponent } from '../deployment-info/environment-deployment-info.component';
import { DeploymentStateTagComponent } from '../deployment-state-tag/deployment-state-tag.component';
import { LockTagComponent } from '../lock-tag/lock-tag.component';
import { LockTimeComponent } from '../lock-time/lock-time.component';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { AvatarModule } from 'primeng/avatar';
import { TooltipModule } from 'primeng/tooltip';
import { DateService } from '@app/core/services/date.service';

@Component({
  selector: 'app-environment-list-view',
  imports: [
    InputTextModule,
    AccordionModule,
    LockTagComponent,
    RouterLink,
    TagModule,
    IconsModule,
    ButtonModule,
    TooltipModule,
    DeploymentStateTagComponent,
    EnvironmentDeploymentInfoComponent,
    LockTimeComponent,
    AvatarModule,
    ConfirmDialogModule,
    CommonModule,
  ],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent {
  private queryClient = inject(QueryClient);
  private confirmationService = inject(ConfirmationService);
  permissionService = inject(PermissionService);
  keycloakService = inject(KeycloakService);
  dateService = inject(DateService);

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hideLinkToList = input<boolean | undefined>();

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());
  hasUnlockPermissions = computed(() => this.permissionService.isAtLeastMaintainer());
  hasDeployPermissions = computed(() => this.permissionService.hasWritePermission());
  hasEditEnvironmentPermissions = computed(() => this.permissionService.isAdmin());

  deploy = output<EnvironmentDto>();

  searchInput = signal<string>('');

  canViewAllEnvironments = computed(() => this.isLoggedIn() && this.editable() && this.hasEditEnvironmentPermissions());
  queryFunction = computed(() => {
    const options = this.canViewAllEnvironments() ? getAllEnvironmentsOptions() : getAllEnabledEnvironmentsOptions();
    return { ...options, refetchInterval: 3000 };
  });
  queryKey = computed(() => (this.canViewAllEnvironments() ? getAllEnvironmentsQueryKey() : getAllEnabledEnvironmentsQueryKey()));

  environmentQuery = injectQuery(() => this.queryFunction());

  unlockEnvironment = injectMutation(() => ({
    ...unlockEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: this.queryKey() });
      // Trigger update on main layout after unlocking
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
    },
  }));

  isCurrentUserLocked = (environment: EnvironmentDto) => {
    return environment.lockedBy?.id === this.keycloakService.getUserGithubId();
  };

  onUnlockEnvironment(event: Event, environment: EnvironmentDto) {
    this.unlockEnvironment.mutate({ path: { id: environment.id } });
    event.stopPropagation();
  }

  deployEnvironment(environment: EnvironmentDto) {
    this.confirmationService.confirm({
      header: 'Deployment',
      message: `Are you sure you want to deploy to ${environment.name}?`,
      accept: () => {
        this.deploy.emit(environment);
        this.queryClient.invalidateQueries({ queryKey: this.queryKey() });
      },
    });
  }

  onSearch(event: Event) {
    const input = event.target as HTMLInputElement;
    this.searchInput.set(input.value);
  }

  filteredEnvironments = computed(() => {
    const environments = this.environmentQuery.data();
    const search = this.searchInput();

    if (!environments) {
      return [];
    }

    return environments.filter(environment => {
      return environment.name.toLowerCase().includes(search.toLowerCase());
    });
  });

  getFullUrl(url: string): string {
    if (url && !url.startsWith('http') && !url.startsWith('https')) {
      return 'http://' + url;
    }
    return url;
  }

  getAvatarBorderClass(login: string) {
    return this.keycloakService.isCurrentUser(login) ? 'border-2 border-primary-400 rounded-full' : '';
  }

  openUserProfile(login: string) {
    //Redirect to the user's github profile
    window.open(`
      https://www.github.com/${login}
    `);
  }
}
