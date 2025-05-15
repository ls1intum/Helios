import { CommonModule } from '@angular/common';
import { Component, inject, input, output, computed } from '@angular/core';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { LockTimeComponent } from '../lock-time/lock-time.component';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { RouterLink } from '@angular/router';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { EnvironmentReviewersComponent } from '../environment-reviewers/environment-reviewers.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCheck, IconCloudUpload, IconLock, IconLockOpen, IconLockPlus, IconPencil, IconX } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-environment-actions',
  imports: [
    CommonModule,
    UserAvatarComponent,
    LockTimeComponent,
    ButtonModule,
    ButtonGroupModule,
    TooltipModule,
    TagModule,
    RouterLink,
    TablerIconComponent,
    EnvironmentReviewersComponent,
  ],
  providers: [
    provideTablerIcons({
      IconCloudUpload,
      IconLock,
      IconLockOpen,
      IconLockPlus,
      IconCheck,
      IconPencil,
      IconX,
    }),
  ],
  templateUrl: './environment-actions.component.html',
})
export class EnvironmentActionsComponent {
  // Convert inputs to signal inputs
  readonly environment = input.required<EnvironmentDto>();
  readonly deployable = input<boolean>(false);
  readonly canViewAllEnvironments = input<boolean>(false);
  readonly timeUntilReservationExpires = input<number | undefined>(undefined);

  // Convert outputs to signal outputs
  readonly deploy = output<Event>();
  readonly unlock = output<Event>();
  readonly extend = output<Event>();
  readonly lock = output<Event>();
  readonly cancelDeployment = output<Event>();

  // Inject required services
  private keycloakService = inject(KeycloakService);
  private permissionService = inject(PermissionService);

  // Convert methods to computed signals where appropriate
  readonly isLoggedIn = computed(() => this.keycloakService.isLoggedIn());
  readonly hasDeployPermissions = computed(() => this.permissionService.hasWritePermission());
  readonly hasUnlockPermissions = computed(() => this.permissionService.isAtLeastMaintainer());

  readonly isCurrentUserLocked = computed(() => {
    const currentUserGithubId = Number(this.keycloakService.getUserGithubId());
    const environmentLockedById = Number(this.environment().lockedBy?.id);
    return environmentLockedById === currentUserGithubId;
  });

  readonly canUserDeploy = computed(() => !!(this.isLoggedIn() && (!this.environment().locked || this.isCurrentUserLocked()) && this.hasDeployPermissions()));

  readonly canUnlockShow = computed(
    () => !!(this.isLoggedIn() && (this.environment().lockReservationWillExpireAt !== null || this.isCurrentUserLocked() || this.hasUnlockPermissions()))
  );

  readonly canUnlock = computed(() => {
    if (this.hasUnlockPermissions() || this.isCurrentUserLocked()) {
      return true;
    } else if (!this.isCurrentUserLocked() && (this.timeUntilReservationExpires() ?? -1) === 0) {
      return true;
    } else {
      return false;
    }
  });

  readonly getLockTooltip = computed(() =>
    this.canUserDeploy() ? 'This will only lock the environment without any deployment.' : 'You do not have permission to lock this environment.'
  );

  readonly getDeployTooltip = computed(() => {
    if (!this.canUserDeploy()) {
      return 'You do not have permission to deploy to this environment.';
    }
    return this.environment().locked ? 'This will deploy to the server.' : 'This will lock the environment then deploy.';
  });

  readonly getUnlockToolTip = computed(() => {
    if (!this.canUnlock()) {
      return 'You do not have permission to unlock this environment.';
    }

    const timeLeft = this.timeUntilReservationExpires();
    const timeLeftMinutes = timeLeft !== undefined ? Math.ceil(timeLeft / 60000) : 0;

    if (this.isCurrentUserLocked() || this.hasUnlockPermissions()) {
      if (timeLeft !== undefined) {
        if (timeLeft > 0) {
          return timeLeftMinutes > 1 ? `Other users can unlock this environment in ${timeLeftMinutes} minutes` : 'Other users can unlock this environment in 1 minute';
        } else if (timeLeft === 0) {
          return 'Reservation has expired. Any user can unlock this environment.';
        }
      }
      return 'Unlock Environment';
    }

    if (timeLeft === undefined) {
      return 'You can not unlock this environment';
    } else if (timeLeft === 0) {
      return 'Reservation Expired. You can unlock this environment.';
    } else {
      return timeLeftMinutes > 1 ? `You can unlock this environment in ${timeLeftMinutes} minutes` : 'You can unlock this environment in 1 minute';
    }
  });

  readonly isDeploymentInProgress = computed(() => {
    const deployment = this.environment().latestDeployment;
    return deployment && (deployment.state === 'IN_PROGRESS' || deployment.state === 'PENDING' || deployment.state === 'QUEUED' || deployment.state === 'REQUESTED');
  });

  readonly canCancelDeployment = computed(() => {
    return this.isDeploymentInProgress() && this.canUserDeploy();
  });

  openExternalLink(event: MouseEvent, link?: string): void {
    event.stopPropagation();
    if (link) {
      window.open(this.getFullUrl(link), '_blank');
    }
  }

  getFullUrl(url: string): string {
    if (url && !url.startsWith('http') && !url.startsWith('https')) {
      return 'http://' + url;
    }
    return url;
  }

  onDeploy(event: Event) {
    this.deploy.emit(event);
  }

  onUnlock(event: Event) {
    this.unlock.emit(event);
  }

  onExtend(event: Event) {
    this.extend.emit(event);
  }

  onLock(event: Event) {
    this.lock.emit(event);
  }

  onCancel(event: Event) {
    console.log('action cancel clicked');
    this.cancelDeployment.emit(event);
  }
}
