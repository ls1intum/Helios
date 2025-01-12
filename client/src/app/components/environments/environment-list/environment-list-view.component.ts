import { Component, computed, inject, input, output, signal } from '@angular/core';
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
import { LockTimeComponent } from '../lock-time/lock-time.component';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';

@Component({
  selector: 'app-environment-list-view',
  imports: [
    InputTextModule,
    AccordionModule,
    LockTagComponent,
    RouterLink,
    TagModule,
    IconsModule,
    ButtonModule,
    DeploymentStateTagComponent,
    EnvironmentDeploymentInfoComponent,
    LockTimeComponent,
  ],
  templateUrl: './environment-list-view.component.html',
})
export class EnvironmentListViewComponent {
  private queryClient = injectQueryClient();
  private keycloakService = inject(KeycloakService);

  editable = input<boolean | undefined>();
  deployable = input<boolean | undefined>();
  hasUnlockPermissions = input<boolean>();
  hasDeployPermissions = input<boolean>();
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

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

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
