import { Component, OnInit, inject, input, numberAttribute, signal, OnDestroy } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { HeliosIconComponent } from '../../components/helios-icon/helios-icon.component';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import { ToastModule } from 'primeng/toast';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { CardModule } from 'primeng/card';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getEnvironmentsByUserLockingOptions, getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { SlicePipe } from '@angular/common';

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
  ],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent implements OnInit, OnDestroy {
  private keycloakService = inject(KeycloakService);

  repositoryId = input.required({ transform: numberAttribute });

  repositoryQuery = injectQuery(() => ({
    ...getRepositoryByIdOptions({ path: { id: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  lockQuery = injectQuery(() => ({
    ...getEnvironmentsByUserLockingOptions(),
    refetchInterval: 10000,
  }));

  items!: { label: string; icon: string; path: string }[];

  timeNow = signal<Date>(new Date());
  private intervalId?: ReturnType<typeof setInterval>;

  logout() {
    this.keycloakService.logout();
  }

  ngOnInit() {
    this.items = [
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
        path: 'environment/list',
      },
      {
        label: 'Project Settings',
        icon: 'adjustments-alt',
        path: 'settings',
      },
    ];

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

  timeSinceLocked(lockedAt: string | undefined): string {
    if (!lockedAt) return '';

    const lockedDate = new Date(lockedAt);
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
  }
}
