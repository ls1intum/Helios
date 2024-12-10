import {Component, inject, Injectable, signal} from '@angular/core';
import {AccordionModule} from 'primeng/accordion';
import {CommonModule} from '@angular/common';
import {LockTagComponent} from '@app/components/lock-tag/lock-tag.component';
import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {
  EnvironmentCommitInfoComponent
} from '../../components/environment-commit-info/environment-commit-info.component';
import {ButtonModule} from 'primeng/button';
import {RouterLink} from '@angular/router';
import {FetchEnvironmentService} from '@app/core/services/fetch/environment';
import {EnvironmentControllerService} from '@app/core/modules/openapi/api/environment-controller.service';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {catchError, firstValueFrom, tap} from 'rxjs';
import {EnvironmentDTO} from '@app/core/modules/openapi/model/environment-dto';
import {DeploymentControllerService} from '@app/core/modules/openapi/api/deployment-controller.service';
import {DeploymentDTO} from '@app/core/modules/openapi/model/deployment-dto';

@Component({
  selector: 'app-environment-list',
  imports: [AccordionModule, CommonModule, LockTagComponent, RouterLink, TagModule, IconsModule, EnvironmentCommitInfoComponent, ButtonModule],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-list.component.html',
})
export class EnvironmentListComponent {
  environmentService = inject(EnvironmentControllerService);
  deploymentService = inject(DeploymentControllerService);

  environmentStore = inject(EnvironmentStoreService);
  deploymentStore = inject(DeploymentStoreService);

  // Expose environments/deployments as a signal to the template
  readonly environments = this.environmentStore.environments;
  readonly latestDeployments = this.deploymentStore.latestDeployments;


  mockConnectedSystems = [
    {id: 'sys1', name: 'Integrated Code Lifecycle'},
    {id: 'sys2', name: 'MySQL'},
    {id: 'sys3', name: 'Iris'},
    {id: 'sys4', name: 'LTI'},
    {id: 'sys5', name: 'GitHub'},
    {id: 'sys6', name: 'GitLab'},
    {id: 'sys7', name: 'Jenkins'},
  ];


  isError = signal(false);
  isEmpty = signal(false);
  isLoading = signal(false);

  query = injectQuery(() => ({
    queryKey: ['environments'],
    queryFn: () => {
      this.isLoading.set(true);
      return this.environmentService.getAllEnvironments()
        .pipe(
          tap(data => {
            this.environmentStore.setEnvironments(data);
            this.isEmpty.set(data.length === 0);

            // Fetch the latest deployment for each environment
            this.fetchLatestDeployments(data)
              .catch(error => {
                console.error('Failed to fetch latest deployments', error);
                this.isError.set(true);
              })
              .finally(() => this.isLoading.set(false));


          }),
          catchError(() => {
            this.isError.set(true);
            this.isLoading.set(false);
            return [];
          })
        ).subscribe()
    },
  }));

  private async fetchLatestDeployments(environments: EnvironmentDTO[]) {
    const deploymentRequests = environments.map(async (env) => {
      try {
        const latestDeployment = await firstValueFrom(
          this.deploymentService.getLatestDeploymentByEnvironmentId(env.id)
        );
        this.deploymentStore.setLatestDeployment(env.id, latestDeployment || null);
      } catch (error) {
        console.error(`Failed to fetch latest deployment for environment ${env.id}`, error);
        this.deploymentStore.setLatestDeployment(env.id, null);
      }
    });

    await Promise.all(deploymentRequests);
  }
}

@Injectable({
  providedIn: 'root'
})
export class EnvironmentStoreService {
  private environmentsState = signal<EnvironmentDTO[]>([]);

  get environments() {
    return this.environmentsState.asReadonly();
  }

  setEnvironments(environments: EnvironmentDTO[]) {
    this.environmentsState.set(environments);
  }

  clearEnvironments() {
    this.environmentsState.set([]);
  }
}

@Injectable({
  providedIn: 'root',
})
export class DeploymentStoreService {
  private latestDeploymentsState = signal<{ [environmentId: number]: DeploymentDTO | null }>({});

  get latestDeployments() {
    return this.latestDeploymentsState.asReadonly();
  }

  setLatestDeployment(environmentId: number, deployment: DeploymentDTO | null) {
    this.latestDeploymentsState.update(state => ({
      ...state,
      [environmentId]: deployment,
    }));
  }

  getLatestDeploymentWithEnvironmentId(environmentId: number): DeploymentDTO | null {
    return this.latestDeploymentsState()[environmentId] || null;
  }

  clearLatestDeployments() {
    this.latestDeploymentsState.set({});
  }
}
