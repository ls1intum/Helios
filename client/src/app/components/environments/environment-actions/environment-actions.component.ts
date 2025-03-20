import { CommonModule } from '@angular/common';
import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { LockTimeComponent } from '../lock-time/lock-time.component';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { RouterLink } from '@angular/router';
import { IconsModule } from 'icons.module';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';

@Component({
  selector: 'app-environment-actions',
  imports: [CommonModule, UserAvatarComponent, LockTimeComponent, ButtonModule, ButtonGroupModule, TooltipModule, TagModule, RouterLink, IconsModule],
  templateUrl: './environment-actions.component.html',
})
export class EnvironmentActionsComponent {
  @Input() environment!: EnvironmentDto;
  @Input() deployable: boolean = false;
  @Input() canViewAllEnvironments: boolean = false;
  @Input() timeUntilReservationExpires: number | undefined;

  @Output() deploy = new EventEmitter<Event>();
  @Output() unlock = new EventEmitter<Event>();
  @Output() extend = new EventEmitter<Event>();
  @Output() lock = new EventEmitter<Event>();

  // Inject required services
  private keycloakService = inject(KeycloakService);
  private permissionService = inject(PermissionService);

  isLoggedIn() {
    return this.keycloakService.isLoggedIn();
  }

  hasDeployPermissions() {
    return this.permissionService.hasWritePermission();
  }

  hasUnlockPermissions() {
    return this.permissionService.isAtLeastMaintainer();
  }

  isCurrentUserLocked() {
    const currentUserGithubId = Number(this.keycloakService.getUserGithubId());
    const environmentLockedById = Number(this.environment.lockedBy?.id);
    return environmentLockedById === currentUserGithubId;
  }

  canUserDeploy(): boolean {
    return !!(this.isLoggedIn() && (!this.environment.locked || this.isCurrentUserLocked()) && this.hasDeployPermissions());
  }

  canUnlockShow(): boolean {
    return !!(this.isLoggedIn() && (this.environment.lockReservationWillExpireAt !== null || this.isCurrentUserLocked() || this.hasUnlockPermissions()));
  }

  canUnlock(): boolean {
    if (this.hasUnlockPermissions() || this.isCurrentUserLocked()) {
      return true;
    } else if (!this.isCurrentUserLocked() && (this.timeUntilReservationExpires ?? -1) === 0) {
      return true;
    } else {
      return false;
    }
  }

  getLockTooltip(): string {
    return this.canUserDeploy() ? 'This will only lock the environment without any deployment.' : 'You do not have permission to lock this environment.';
  }

  getDeployTooltip(): string {
    if (!this.canUserDeploy()) {
      return 'You do not have permission to deploy to this environment.';
    }
    return this.environment.locked ? 'This will deploy to the server.' : 'This will lock the environment then deploy.';
  }

  getUnlockToolTip(): string {
    if (!this.canUnlock()) {
      return 'You do not have permission to unlock this environment.';
    }

    const timeLeft = this.timeUntilReservationExpires;
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
  }

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
}
