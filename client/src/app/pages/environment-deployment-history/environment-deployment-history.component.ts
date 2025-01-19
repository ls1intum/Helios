import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { PrimeTemplate } from 'primeng/api';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { IconsModule } from 'icons.module';
import { getDeploymentsByEnvironmentIdOptions, getLockHistoryByEnvironmentIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';

@Component({
  selector: 'app-environment-deployment-history',
  imports: [IconsModule, PrimeTemplate, SkeletonModule, TableModule],
  templateUrl: './environment-deployment-history.component.html',
})
export class EnvironmentDeploymentHistoryComponent {
  dateService = inject(DateService);

  environmentId = input.required({ transform: numberAttribute });

  deploymentsQuery = injectQuery(() => getDeploymentsByEnvironmentIdOptions({ path: { environmentId: this.environmentId() } }));
  deployments = computed(() => this.deploymentsQuery.data());

  lockingHistoryQuery = injectQuery(() => getLockHistoryByEnvironmentIdOptions({ path: { environmentId: this.environmentId() } }));
  lockingHistory = computed(() => this.lockingHistoryQuery.data());

  combinedHistory = computed(() => {
    const deployments = this.deploymentsQuery.data() || [];
    const locks = this.lockingHistoryQuery.data() || [];

    // 1) Map deployments to a uniform shape
    const mappedDeployments = deployments.map(d => ({
      type: 'DEPLOYMENT',
      id: d.id,
      ref: d.ref,
      sha: d.sha,
      state: d.state,
      timestamp: d.updatedAt ?? d.createdAt,
    }));

    // 2) For each lock record, produce one or two events:
    //    - A LOCK_EVENT for lockedAt
    //    - An UNLOCK_EVENT (if unlockedAt is defined)
    const mappedLocks = locks.flatMap(l => {
      // Always create the LOCK_EVENT
      const lockEvent = {
        type: 'LOCK_EVENT',
        id: l.id,
        lockedBy: l.lockedBy,
        lockedAt: l.lockedAt,
        timestamp: l.lockedAt,
      };

      // Conditionally create the UNLOCK_EVENT
      const unlockEvent = l.unlockedAt
        ? {
            type: 'UNLOCK_EVENT',
            id: l.id,
            lockedBy: l.lockedBy, // or unlockedBy if it differs
            unlockedAt: l.unlockedAt,
            timestamp: l.unlockedAt,
          }
        : null;

      // Return an array with just the lockEvent, or both lockEvent + unlockEvent
      return unlockEvent ? [lockEvent, unlockEvent] : [lockEvent];
    });

    // 3) Combine all events and sort by timestamp (descending in this example)
    const combined = [...mappedDeployments, ...mappedLocks].sort((a, b) => {
      // Note: add a fallback (?? 0) to handle any undefined timestamps
      return new Date(b.timestamp ?? 0).getTime() - new Date(a.timestamp ?? 0).getTime();
    });

    return combined;
  });
}
