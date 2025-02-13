import { Component, computed, inject, input, output, signal, OnDestroy } from '@angular/core';
import { AccordionModule } from 'primeng/accordion';

import { DatePipe, CommonModule } from '@angular/common';
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
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { EnvironmentStatusInfoComponent } from '../environment-status-info/environment-status-info.component';
import { EnvironmentStatusTagComponent } from '../environment-status-tag/environment-status-tag.component';

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
    EnvironmentStatusTagComponent,
    EnvironmentDeploymentInfoComponent,
    EnvironmentStatusInfoComponent,
    LockTimeComponent,
    AvatarModule,
    ConfirmDialogModule,
    CommonModule,
    TimeAgoPipe,
    UserAvatarComponent,
  ],
  providers: [DatePipe],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent implements OnDestroy {
  private queryClient = inject(QueryClient);
  private confirmationService = inject(ConfirmationService);
  private keycloakService = inject(KeycloakService);
  private datePipe = inject(DatePipe);
  private permissionService = inject(PermissionService);
  private currentTime = signal(Date.now());
  private intervalId: number | undefined;

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hideLinkToList = input<boolean | undefined>();

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());
  isAdmin = computed(() => this.permissionService.isAdmin());
  hasUnlockPermissions = computed(() => this.permissionService.isAtLeastMaintainer());
  hasDeployPermissions = computed(() => this.permissionService.hasWritePermission());
  hasEditEnvironmentPermissions = computed(() => this.permissionService.isAdmin());
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

  openExternalLink(event: MouseEvent, environment: EnvironmentDto): void {
    // Prevent the click event from propagating
    event.stopPropagation();

    // Only proceed if the server URL is available
    if (environment.serverUrl) {
      window.open(this.getFullUrl(environment.serverUrl), '_blank');
    }
  }

  getDeploymentTime(environment: EnvironmentDto) {
    const date = environment.latestDeployment?.updatedAt;
    return date ? this.datePipe.transform(date, 'd MMMM y, h:mm a') : null; // Format date
  }

  canUnlock(environment: EnvironmentDto) {
    if (this.hasUnlockPermissions() || this.isCurrentUserLocked(environment)) {
      return true;
    } else if (!this.isCurrentUserLocked(environment) && (this.timeUntilReservationExpires()?.get(environment.id) ?? -1) === 0) {
      return true;
    } else {
      return false;
    }
  }

  unlockToolTip(environment: EnvironmentDto) {
    const timeLeft = this.timeUntilReservationExpires().get(environment.id);
    const timeLeftMinutes = timeLeft !== undefined && timeLeft !== null ? Math.ceil(timeLeft / 60000) : 0;
    if (this.isCurrentUserLocked(environment) || this.hasUnlockPermissions()) {
      if (timeLeft !== undefined && timeLeft !== null && timeLeft > 0) {
        // if the user is locked and the time has not expired, show the time left
        return timeLeftMinutes > 1 ? `Other users can unlock this environment in ${timeLeftMinutes} minutes` : 'Other users can unlock this environment in 1 minute';
      }
      // If the user is locked and the time has expired, show only unlock environment
      return 'Unlock Environment';
    }
    if (timeLeft === undefined || timeLeft === null) {
      // If the user is not locked and the time is not set, then user can not unlock
      return 'You can not unlock this environment';
    } else if (timeLeft === 0) {
      // If the user is not locked and the time has expired, show reservation expired
      return 'Reservation Expired. You can unlock this environment.';
    } else {
      // If the user is not locked and the time has not expired, show the time left
      return timeLeftMinutes > 1 ? `You can unlock this environment in ${timeLeftMinutes} minutes` : 'You can unlock this environment in 1 minute';
    }
  }
}
