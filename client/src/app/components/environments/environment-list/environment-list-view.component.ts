import { CommonModule, DatePipe } from '@angular/common';
import { Component, computed, inject, input, OnDestroy, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EnvironmentDto } from '@app/core/modules/openapi';
import {
  extendEnvironmentLockMutation,
  getAllEnabledEnvironmentsOptions,
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsOptions,
  getAllEnvironmentsQueryKey,
  getEnvironmentsByUserLockingQueryKey,
  lockEnvironmentMutation,
  unlockEnvironmentMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { AccordionModule } from 'primeng/accordion';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { DividerModule } from 'primeng/divider';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { EnvironmentAccordionComponent } from '../environment-accordion/environment-accordion.component';

@Component({
  selector: 'app-environment-list-view',
  imports: [
    InputTextModule,
    AccordionModule,
    RouterLink,
    TagModule,
    IconsModule,
    ButtonModule,
    TooltipModule,
    AvatarModule,
    CommonModule,
    ButtonGroupModule,
    ToggleButtonModule,
    FormsModule,
    SelectButtonModule,
    ToggleSwitchModule,
    DividerModule,
    EnvironmentAccordionComponent,
  ],
  providers: [DatePipe],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent implements OnDestroy {
  private queryClient = inject<QueryClient>(QueryClient);
  private confirmationService = inject(ConfirmationService);
  private keycloakService = inject(KeycloakService);
  private permissionService = inject(PermissionService);
  private currentTime = signal(Date.now());
  private intervalId: number | undefined;
  private messageService = inject(MessageService);

  showLatestDeployment: boolean = true;

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hideLinkToList = input<boolean | undefined>();

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());
  isAdmin = computed(() => this.permissionService.isAdmin());
  hasUnlockPermissions = computed(() => this.permissionService.isAtLeastMaintainer());
  hasDeployPermissions = computed(() => this.permissionService.hasWritePermission());
  hasEditEnvironmentPermissions = computed(() => this.permissionService.isAdmin());

  lockEnvironmentMutation = injectMutation(() => ({
    ...lockEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: this.queryKey() });
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
    },
  }));

  extendEnvironmentLockMutation = injectMutation(() => ({
    ...extendEnvironmentLockMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
      this.messageService.add({ severity: 'success', summary: 'Extend Lock', detail: 'Lock was extended successfully' });
    },
  }));

  canUserDeploy(environment: EnvironmentDto): boolean {
    return !!(this.isLoggedIn() && (!environment.locked || this.isCurrentUserLocked(environment)) && this.hasDeployPermissions());
  }

  deploy = output<EnvironmentDto>();

  searchInput = signal<string>('');
  timeUntilReservationExpires = computed(() => {
    const environments = this.environmentQuery.data();
    const now = this.currentTime();

    if (!environments) return new Map<number, number>();

    const timeLeftMap = new Map<number, number>();

    environments.forEach(env => {
      if (env.lockedAt && env.lockReservationWillExpireAt) {
        const willExpireAt = new Date(env.lockReservationWillExpireAt).getTime();
        const timeLeftMs = willExpireAt - now;

        timeLeftMap.set(env.id, Math.max(timeLeftMs, 0)); // Ensure non-negative
      }
    });

    return timeLeftMap;
  });
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

  lockEnvironment(environment: EnvironmentDto) {
    this.confirmationService.confirm({
      header: 'Lock Environment',
      message: `Are you sure you want to lock ${environment.name}?`,
      accept: () => {
        this.lockEnvironmentMutation.mutate({ path: { id: environment.id } });
      },
    });
  }

  extendLock(event: Event, environment: EnvironmentDto) {
    this.extendEnvironmentLockMutation.mutate({ path: { id: environment.id } });
    event.stopPropagation();
  }

  getLockTooltip(environment: EnvironmentDto): string {
    return this.canUserDeploy(environment) ? 'This will only lock the environment without any deployment.' : 'You do not have permission to lock this environment.';
  }

  getDeployTooltip(environment: EnvironmentDto): string {
    if (!this.canUserDeploy(environment)) return 'You do not have permission to deploy to this environment.';
    return environment.locked ? 'This will deploy to the server.' : 'This will lock the environment then deploy.';
  }

  constructor() {
    this.intervalId = window.setInterval(() => {
      this.currentTime.set(Date.now());
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.intervalId !== undefined) {
      clearInterval(this.intervalId);
    }
  }

  isCurrentUserLocked = (environment: EnvironmentDto) => {
    const currentUserGithubId = Number(this.keycloakService.getUserGithubId());
    const environmentLockedById = Number(environment.lockedBy?.id);
    return environmentLockedById === currentUserGithubId;
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
      },
    });
  }

  onSearch(event: Event) {
    const input = event.target as HTMLInputElement;
    this.searchInput.set(input.value);
  }

  filteredEnvironments = computed(() => {
    let environments = this.environmentQuery.data();

    const search = this.searchInput();

    if (!environments) {
      return [];
    }

    return environments.filter(environment => {
      return environment.name.toLowerCase().includes(search.toLowerCase());
    });
  });

  private readonly environmentTypeOrder = ['test', 'staging'];

  environmentGroups = computed(() => {
    const environments = this.filteredEnvironments();
    const groups = new Map<string, EnvironmentDto[]>();

    // Group environments
    environments.forEach(environment => {
      const type = environment.type || 'Ungrouped';
      if (!groups.has(type)) {
        groups.set(type, []);
      }
      groups.get(type)?.push(environment);
    });

    // Sort by predefined order
    return new Map(
      Array.from(groups.entries()).sort(([a], [b]) => {
        const indexA = this.environmentTypeOrder.indexOf(a.toLowerCase());
        const indexB = this.environmentTypeOrder.indexOf(b.toLowerCase());

        // If both types are not in the order array, sort alphabetically
        if (indexA === -1 && indexB === -1) return a.localeCompare(b);
        // If one type is not in the order array, it goes last
        if (indexA === -1) return 1;
        if (indexB === -1) return -1;
        // Otherwise, sort by the predefined order
        return indexA - indexB;
      })
    );
  });

  capitalizeFirstLetter(str: string): string {
    return str.charAt(0).toUpperCase() + str.toLowerCase().slice(1);
  }
}
