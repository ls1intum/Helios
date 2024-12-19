import {Component, computed, inject, input, numberAttribute, signal} from '@angular/core';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {PrimeTemplate} from 'primeng/api';
import {SkeletonModule} from 'primeng/skeleton';
import {TableModule} from 'primeng/table';
import {IconsModule} from 'icons.module';
import { getDeploymentsByEnvironmentIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';

@Component({
  selector: 'app-environment-deployment-history',
  imports: [
    IconsModule,
    PrimeTemplate,
    SkeletonModule,
    TableModule
  ],
  templateUrl: './environment-deployment-history.component.html',
})
export class EnvironmentDeploymentHistoryComponent {
  dateService = inject(DateService);

  environmentId = input.required({ transform: numberAttribute });

  // TODO: Fix the query to fetch deployments by environment ID
  // Switching from one history page to another causes this query not called for the new environment ID
  // Thus we see the previous environment's deployment history
  deploymentsQuery = injectQuery(() => getDeploymentsByEnvironmentIdOptions({ path: { environmentId: this.environmentId() } }));
  deployments = computed(() => this.deploymentsQuery.data());
}
