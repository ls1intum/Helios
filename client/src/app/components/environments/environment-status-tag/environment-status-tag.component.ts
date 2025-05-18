import { Component, computed, input, OnDestroy, OnInit, signal } from '@angular/core';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconActivity,
  IconAlertTriangle,
  IconCheck,
  IconDatabase,
  IconDatabaseCog,
  IconDatabaseX,
  IconExclamationCircle,
  IconGitCommit,
  IconLoader,
  IconPower,
  IconSquare,
  IconX,
} from 'angular-tabler-icons/icons';
import { TagModule } from 'primeng/tag';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { Tooltip } from 'primeng/tooltip';

// union that matches the Java enum
export type LifecycleState = 'STARTING_UP' | 'MIGRATING_DB' | 'MIGRATION_FAILED' | 'MIGRATION_FINISHED' | 'RUNNING' | 'DEGRADED' | 'SHUTTING_DOWN' | 'STOPPED' | 'FAILED';

// EnvironmentStatusDto plus lifecycleState
export type EnvironmentStatusEx = EnvironmentStatusDto & {
  lifecycleState?: LifecycleState;
};

@Component({
  selector: 'app-environment-status-tag',
  imports: [TagModule, TablerIconComponent, TimeAgoPipe, Tooltip],
  providers: [
    provideTablerIcons({
      IconCheck,
      IconExclamationCircle,
      IconActivity,
      IconAlertTriangle,
      IconDatabase,
      IconDatabaseCog,
      IconDatabaseX,
      IconGitCommit,
      IconLoader,
      IconPower,
      IconSquare,
      IconX,
    }),
  ],
  templateUrl: './environment-status-tag.component.html',
})
export class EnvironmentStatusTagComponent implements OnInit, OnDestroy {
  private intervalId?: ReturnType<typeof setInterval>;
  private readonly now = signal<number>(Date.now());

  status = input.required<EnvironmentStatusDto>();

  ngOnInit() {
    // update the current time every 5 seconds
    this.intervalId = setInterval(() => this.now.set(Date.now()), 5_000);
  }
  ngOnDestroy() {
    clearInterval(this.intervalId);
  }

  ageMin = computed(() => {
    const last = new Date(this.status().checkedAt).getTime();
    return Math.floor((this.now() - last) / 60_000);
  });

  /** stale after 1 min for PUSH_UPDATE checks */
  isStale = computed(() => this.status().checkType === 'PUSH_UPDATE' && this.ageMin() > 2);

  /** lifecycle → view‑model (severity, icon, label, spin?) */
  lifecycleView = computed<{
    severity: 'success' | 'info' | 'warn' | 'danger';
    label: string;
    icon?: string;
    spin?: boolean;
  }>(() => {
    const state = this.status().state;

    switch (state) {
      case 'STARTING_UP':
        return {
          severity: 'info',
          icon: 'loader',
          label: 'Starting up',
          spin: true,
        };

      case 'MIGRATING_DB':
        return {
          severity: 'info',
          icon: 'database-cog',
          label: 'Migrating DB',
          spin: false,
        };

      case 'MIGRATION_FAILED':
        return {
          severity: 'danger',
          icon: 'database-x',
          label: 'Migration failed',
          spin: false,
        };

      case 'MIGRATION_FINISHED':
        return {
          severity: 'success',
          icon: 'database',
          label: 'Migration finished',
          spin: false,
        };

      case 'RUNNING':
        return { severity: 'success', icon: 'activity', label: 'Running', spin: false };

      case 'DEGRADED':
        return { severity: 'warn', label: 'Degraded' };

      case 'SHUTTING_DOWN':
        return { severity: 'info', icon: 'power', label: 'Shutting down', spin: false };

      case 'STOPPED':
        return { severity: 'info', icon: 'square', label: 'Stopped', spin: false };

      case 'FAILED':
        return { severity: 'danger', icon: 'x', label: 'Failed', spin: false };

      /* fallback – server not yet upgraded */
      default:
        return {
          severity: this.status().success ? 'success' : 'danger',
          icon: this.status().success ? 'check' : 'alert-triangle',
          label: (this.status().state ?? 'Unknown').replace('_', ' '),
          spin: false,
        };
    }
  });
}
