import { CommonModule, DatePipe } from '@angular/common';
import { Component, computed, inject, input, OnDestroy, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { EnvironmentDeployment, EnvironmentDto } from '@app/core/modules/openapi';
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
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { AccordionModule } from 'primeng/accordion';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { TooltipModule } from 'primeng/tooltip';
import { EnvironmentDeploymentInfoComponent } from '../deployment-info/environment-deployment-info.component';
import { DeploymentStateTagComponent } from '../deployment-state-tag/deployment-state-tag.component';
import { DeploymentStepperComponent } from '../deployment-stepper/deployment-stepper.component';
import { EnvironmentStatusInfoComponent } from '../environment-status-info/environment-status-info.component';
import { EnvironmentStatusTagComponent } from '../environment-status-tag/environment-status-tag.component';
import { LockTagComponent } from '../lock-tag/lock-tag.component';
import { LockTimeComponent } from '../lock-time/lock-time.component';

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
    DeploymentStepperComponent,
    EnvironmentStatusTagComponent,
    EnvironmentDeploymentInfoComponent,
    EnvironmentStatusInfoComponent,
    LockTimeComponent,
    AvatarModule,
    CommonModule,
    ButtonGroupModule,
    TimeAgoPipe,
    UserAvatarComponent,
    ToggleButtonModule,
    FormsModule,
    SelectButtonModule,
  ],
  providers: [DatePipe],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent implements OnDestroy {
  private queryClient = inject<QueryClient>(QueryClient);
  private confirmationService = inject(ConfirmationService);
  private keycloakService = inject(KeycloakService);
  private datePipe = inject(DatePipe);
  private permissionService = inject(PermissionService);
  private currentTime = signal(Date.now());
  private intervalId: number | undefined;
  private messageService = inject(MessageService);

  showLatestDeployment: boolean = true;

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hideLinkToList = input<boolean | undefined>();
  showTestEnvironmentsOnly = input<boolean | undefined>();

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
    if (this.showTestEnvironmentsOnly()) {
      environments = environments?.filter(environment => environment.type === 'TEST');
    }

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

  getUnlockToolTip(environment: EnvironmentDto) {
    if (!this.canUnlock(environment)) return 'You do not have permission to unlock this environment.';
    const timeLeft = this.timeUntilReservationExpires().get(environment.id);
    const timeLeftMinutes = timeLeft !== undefined && timeLeft !== null ? Math.ceil(timeLeft / 60000) : 0;
    if (this.isCurrentUserLocked(environment) || this.hasUnlockPermissions()) {
      if (timeLeft !== undefined && timeLeft !== null) {
        if (timeLeft > 0) {
          // if the user is locked and the time has not expired, show the time left
          return timeLeftMinutes > 1 ? `Other users can unlock this environment in ${timeLeftMinutes} minutes` : 'Other users can unlock this environment in 1 minute';
        } else if (timeLeft === 0) {
          // If the user is locked and the time has expired, show only unlock environment
          return 'Reservation has expired. Any user can unlock this environment.';
        }
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

  formatEnvironmentType(type: string): string {
    return type.charAt(0).toUpperCase() + type.slice(1).toLowerCase();
  }

  isDeploymentOngoing(environment: EnvironmentDto) {
    if (!environment.latestDeployment) {
      return false;
    } else if (environment.latestDeployment.state && ['SUCCESS', 'FAILURE', 'ERROR', 'INACTIVE', 'UNKNOWN'].includes(environment.latestDeployment.state)) {
      return false;
    }
    return true;
  }

  isRelease(deployment: EnvironmentDeployment): boolean {
    // TODO: This is a temporary solution to check if a deployment is a release
    // until Paul's PR is merged which enables syncing of releases from GitHub to find a corresponding release
    return !!deployment.releaseCandidateName || (!!deployment.ref && /^v?\d+\.\d+\.\d+/.test(deployment.ref));
  }

  getPrLink(env: EnvironmentDto) {
    return ['/repo', env.repository?.id, 'ci-cd', 'pr', env.latestDeployment?.pullRequestNumber?.toString()];
  }

  getBranchLink(env: EnvironmentDto) {
    return ['/repo', env.repository?.id, 'ci-cd', 'branch', env.latestDeployment?.ref];
  }
}
