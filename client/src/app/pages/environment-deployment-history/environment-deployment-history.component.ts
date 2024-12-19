import {Component, computed, inject, Injectable, signal} from '@angular/core';
import {DeploymentControllerService, DeploymentDTO} from '@app/core/modules/openapi';
import {ActivatedRoute} from '@angular/router';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {PrimeTemplate} from 'primeng/api';
import {SkeletonModule} from 'primeng/skeleton';
import {TableModule} from 'primeng/table';
import {IconsModule} from 'icons.module';
import {catchError, tap} from 'rxjs';
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
  private route = inject(ActivatedRoute);
  private deploymentService = inject(DeploymentControllerService);
  private deploymentHistoryStore = inject(EnvironmentDeploymentHistoryStoreService);
  dateService = inject(DateService);

  environmentId = signal<number | null>(null);

  isError = signal(false);
  isEmpty = signal(false);
  isLoading = signal(false);
  deployments = computed(() => this.deploymentHistoryStore.deployments());

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.environmentId.set(!isNaN(id) ? id : null);

    // Subscribe to route changes and update the signal
    this.route.paramMap.subscribe(paramMap => {
      const id = Number(paramMap.get('id'));
      this.environmentId.set(!isNaN(id) ? id : null);
    });
  }

  // TODO: Fix the query to fetch deployments by environment ID
  // Switching from one history page to another causes this query not called for the new environment ID
  // Thus we see the previous environment's deployment history
  query = injectQuery(() => ({
    queryKey: ['environment-deployments', this.environmentId()],
    queryFn: () => {
      const id = this.environmentId();
      if (id !== null) {
        this.isLoading.set(true);
        return this.deploymentService.getDeploymentsByEnvironmentId(id)
          .pipe(
            tap(data => {
              console.log('Deployments:', data);
              this.deploymentHistoryStore.setDeployments(data);
              this.isEmpty.set(data.length === 0);
              this.isLoading.set(false);
            }),
            catchError(() => {
                this.isError.set(true);
                this.isLoading.set(false);
                return [];
              }
            )
          ).subscribe()
      }
      throw new Error('Invalid environment ID');
    }
  }));


}

@Injectable({
  providedIn: 'root'
})
export class EnvironmentDeploymentHistoryStoreService {
  private deploymentsState = signal<DeploymentDTO[]>([]);

  deployments = this.deploymentsState.asReadonly();

  setDeployments(deployments: DeploymentDTO[]): void {
    this.deploymentsState.set(deployments);
  }
}
