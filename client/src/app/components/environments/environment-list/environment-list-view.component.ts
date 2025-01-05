import { Component, computed, input, output, signal } from '@angular/core';
import { AccordionModule } from 'primeng/accordion';

import { RouterLink } from '@angular/router';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { getAllEnvironmentsOptions, getAllEnvironmentsQueryKey, unlockEnvironmentMutation } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { EnvironmentDeploymentInfoComponent } from '../deployment-info/environment-deployment-info.component';
import { DeploymentStateTagComponent } from '../deployment-state-tag/deployment-state-tag.component';
import { LockTagComponent } from '../lock-tag/lock-tag.component';

@Component({
  selector: 'app-environment-list-view',
  imports: [InputTextModule, AccordionModule, LockTagComponent, RouterLink, TagModule, IconsModule, ButtonModule, DeploymentStateTagComponent, EnvironmentDeploymentInfoComponent],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent {
  private queryClient = injectQueryClient();

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hasUnlockPermissions = input<boolean | undefined>();
  hasDeployPermissions = input<boolean | undefined>();
  hideLinkToList = input<boolean | undefined>();

  deploy = output<EnvironmentDto>();

  searchInput = signal<string>('');

  environmentQuery = injectQuery(() => getAllEnvironmentsOptions());

  unlockEnvironment = injectMutation(() => ({
    ...unlockEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
    },
  }));

  onUnlockEnvironment(event: Event, environment: EnvironmentDto) {
    this.unlockEnvironment.mutate({ path: { id: environment.id } });
    event.stopPropagation();
  }

  deployEnvironment(environment: EnvironmentDto) {
    this.deploy.emit(environment);
  }

  onSearch(event: Event) {
    const input = event.target as HTMLInputElement;
    this.searchInput.set(input.value);
  }

  filteredEnvironments = computed(() => {
    const environments = this.environmentQuery.data();
    const search = this.searchInput();

    if (!environments) {
      return [];
    }
    // TODO: Incorporate deployed by information to check force unlock permissions for each environment in the list
    const environmentsWithDeploymentInfo = environments.map(environment => {
      return {
        ...environment,
        deployedByCurrentUser: true,
      };
    });
    return environmentsWithDeploymentInfo.filter(environment => {
      return environment.name.toLowerCase().includes(search.toLowerCase());
    });
  });

  getFullUrl(url: string): string {
    if (url && !url.startsWith('http') && !url.startsWith('https')) {
      return 'http://' + url;
    }
    return url;
  }
}
