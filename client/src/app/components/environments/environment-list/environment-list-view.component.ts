import { Component, computed, input, output, signal } from '@angular/core';
import { AccordionModule } from 'primeng/accordion';

import { TagModule } from 'primeng/tag';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { LockTagComponent } from '../lock-tag/lock-tag.component';
import { DeploymentStateTagComponent } from '../deployment-state-tag/deployment-state-tag.component';
import { InputTextModule } from 'primeng/inputtext';
import { EnvironmentDeploymentInfoComponent } from '../deployment-info/environment-deployment-info.component';
import { getAllEnvironmentsOptions, getAllEnvironmentsQueryKey, unlockEnvironmentMutation } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { EnvironmentDto } from '@app/core/modules/openapi';

@Component({
  selector: 'app-environment-list-view',
  imports: [InputTextModule, AccordionModule, LockTagComponent, RouterLink, TagModule, IconsModule, ButtonModule, DeploymentStateTagComponent, EnvironmentDeploymentInfoComponent],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent {
  private queryClient = injectQueryClient();

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hideLinkToList = input<boolean | undefined>();

  deploy = output<EnvironmentDto>();

  searchInput = signal<string>('');

  environmentQuery = injectQuery(() => getAllEnvironmentsOptions());

  environments = computed(() => this.environmentQuery.data());

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

    return environments.filter(environment => {
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
