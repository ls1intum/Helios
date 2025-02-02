import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { getEnvironmentsByUserLockingOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-user-lock-info',
  imports: [IconsModule, TooltipModule, RouterLink],
  templateUrl: './user-lock-info.component.html',
})
export class UserLockInfoComponent implements OnInit, OnDestroy {
  private keycloakService = inject(KeycloakService);

  timeNow = signal<Date>(new Date());
  private intervalId?: ReturnType<typeof setInterval>;

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

  latestLock = computed(() => {
    const lock = this.lockQuery.data();

    // For some reason, when there is no lock
    // an empty object instead of null is returned
    return lock?.lockedAt ? lock : null;
  });

  timeSinceLocked = computed(() => {
    const latestLock = this.latestLock();

    if (!latestLock?.lockedAt) return '';

    const lockedDate = new Date(latestLock.lockedAt);
    const now = this.timeNow(); // your signal for "current time"
    const diffMs = now.getTime() - lockedDate.getTime();

    // If lockedAt is in the future, return empty or handle differently
    if (diffMs < 0) {
      return '';
    }

    const totalSeconds = Math.floor(diffMs / 1000);
    const days = Math.floor(totalSeconds / 86400); // 1 day = 86400s
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    // Zero-pad hours, minutes, seconds
    const padTwo = (num: number) => num.toString().padStart(2, '0');

    if (days > 0) {
      // Format: dd:hh:mm (e.g., "1:05:12" => 1 day, 5 hours, 12 minutes)
      const dd = days.toString(); // or padTwo(days) if you prefer "01" for 1 day
      const hh = padTwo(hours);
      const mm = padTwo(minutes);
      return `${dd}:${hh}:${mm}`;
    } else {
      // Format: hh:mm:ss (e.g., "05:12:36")
      const hh = padTwo(hours);
      const mm = padTwo(minutes);
      const ss = padTwo(seconds);
      return `${hh}:${mm}:${ss}`;
    }
  });
}
