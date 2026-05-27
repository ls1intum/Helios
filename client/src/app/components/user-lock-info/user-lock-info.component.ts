import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { getEnvironmentsByUserLockingOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TooltipModule } from 'primeng/tooltip';
import { DateService } from '@app/core/services/date.service';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconGitBranch, IconLock } from 'angular-tabler-icons/icons';
@Component({
  selector: 'app-user-lock-info',
  imports: [TablerIconComponent, TooltipModule, RouterLink],
  providers: [
    provideTablerIcons({
      IconLock,
      IconGitBranch,
    }),
  ],
  templateUrl: './user-lock-info.component.html',
})
export class UserLockInfoComponent implements OnInit, OnDestroy {
  private keycloakService = inject(KeycloakService);
  private dateService = inject(DateService);
  timeNow = signal<Date>(new Date());
  private intervalId?: ReturnType<typeof setInterval>;
  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

  timeUntilLockExpires = computed(() => {
    if (!this.isLoggedIn()) {
      return '';
    }

    const lockWillExpireAt = this.latestLockExpiration()?.environment?.lockWillExpireAt;
    if (lockWillExpireAt == null) {
      return 'Unlimited';
    }

    return this.dateService.timeUntilExpire(this.timeNow(), lockWillExpireAt);
  });

  // Returns the latest lock information for the current user
  lockQuery = injectQuery(() => ({
    ...getEnvironmentsByUserLockingOptions(),
    refetchInterval: () => (this.isLoggedIn() ? 10000 : false),
    enabled: this.isLoggedIn(),
  }));

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

  latestLockExpiration = computed(() => {
    if (!this.isLoggedIn()) {
      return null;
    }

    const lock = this.lockQuery.data();

    // For some reason, when there is no lock
    // an empty object instead of null is returned
    const val = lock?.lockedAt ? lock : null;
    return val;
  });
}
