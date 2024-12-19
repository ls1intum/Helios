import {Component, computed, inject, Injectable, input, output, signal} from '@angular/core';
import {AccordionModule} from 'primeng/accordion';

import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {ButtonModule} from 'primeng/button';
import {RouterLink} from '@angular/router';
import {FetchEnvironmentService} from '@app/core/services/fetch/environment';
import {EnvironmentControllerService} from '@app/core/modules/openapi/api/environment-controller.service';
import {injectMutation, injectQuery} from '@tanstack/angular-query-experimental';
import {lastValueFrom, } from 'rxjs';
import {EnvironmentDTO} from '@app/core/modules/openapi/model/environment-dto';
import {DeploymentDTO} from '@app/core/modules/openapi/model/deployment-dto';
import { LockTagComponent } from '../lock-tag/lock-tag.component';
import { DeploymentStateTagComponent } from '../deployment-state-tag/deployment-state-tag.component';
import { InputTextModule } from 'primeng/inputtext';
import { queryClient } from '@app/app.config';
import { EnvironmentDeploymentInfoComponent } from '../deployment-info/environment-deployment-info.component';

@Component({
  selector: 'app-environment-list-view',
  imports: [InputTextModule, AccordionModule, LockTagComponent, RouterLink, TagModule, IconsModule, ButtonModule, DeploymentStateTagComponent, EnvironmentDeploymentInfoComponent],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent {
  environmentService = inject(EnvironmentControllerService);

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hideLinkToList = input<boolean | undefined>();

  onDeploy = output<EnvironmentDTO>();

  searchInput = signal<string>('');

  query = injectQuery(() => ({
    queryKey: ['environments'],
    queryFn: () => lastValueFrom(this.environmentService.getAllEnvironments()),
    refetchInterval: 5000,
  }));

  unlockEnvironment = injectMutation(() => ({
    mutationFn: (environment: EnvironmentDTO) => {
      return lastValueFrom(this.environmentService.unlockEnvironment(environment.id));
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments'] });
    }
  }));

  deployEnvironment(environment: EnvironmentDTO) {
    this.onDeploy.emit(environment);
  }

  onSearch(event: Event) {
    const input = event.target as HTMLInputElement;
    this.searchInput.set(input.value);
  }

  filteredEnvironments = computed(() => {
    const environments = this.query.data();
    const search = this.searchInput();

    if (!environments) {
      return [];
    }

    return environments.filter((environment) => {
      return environment.name.toLowerCase().includes(search.toLowerCase());
    });
  });

  getFullUrl(url: string): string {
    if (url && (!url.startsWith('http') && !url.startsWith('https'))) {
      return 'http://' + url;
    }
    return url;
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
