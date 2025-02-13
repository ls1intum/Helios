import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { getEnvironmentsByUserLockingOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import { DateService } from '@app/core/services/date.service';
@Component({
  selector: 'app-user-lock-info',
  imports: [IconsModule, TooltipModule, RouterLink],
  templateUrl: './user-lock-info.component.html',
})
export class UserLockInfoComponent implements OnInit, OnDestroy {
  private keycloakService = inject(KeycloakService);
  private dateService = inject(DateService);
  timeNow = signal<Date>(new Date());
  private intervalId?: ReturnType<typeof setInterval>;
  timeUntilLockExpires = computed(() =>
    this.latestLockExpiration()?.environment?.lockWillExpireAt !== null
      ? this.dateService.timeUntilExpire(this.timeNow(), this.latestLockExpiration()?.environment?.lockWillExpireAt)
      : `Unlimited`
  );
  // Returns the latest lock information for the current user
  lockQuery = injectQuery(() => ({
    ...getEnvironmentsByUserLockingOptions(),
    refetchInterval: 10000,
    enabled: () => !!this.keycloakService.isLoggedIn(),
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
    const lock = this.lockQuery.data();

    // For some reason, when there is no lock
    // an empty object instead of null is returned
    const val = lock?.lockedAt ? lock : null;
    return val;
  });
}
