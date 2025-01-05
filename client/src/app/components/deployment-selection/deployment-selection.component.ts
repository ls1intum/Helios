/* eslint-disable prettier/prettier */
import { Component, computed, inject, input } from '@angular/core';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { deployToEnvironmentMutation, getAllEnvironmentsQueryKey, getEnvironmentByIdQueryKey, getUserPermissionsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { EnvironmentListViewComponent } from '../environments/environment-list/environment-list-view.component';

@Component({
  selector: 'app-deployment-selection',
  templateUrl: './deployment-selection.component.html',
  imports: [EnvironmentListViewComponent],
})
export class DeploymentSelectionComponent {
  private messageService = inject(MessageService);

  queryClient = injectQueryClient();

  sourceRef = input.required<string>();

  permissionsQuery = injectQuery(() => ({
    ...getUserPermissionsOptions(),
  }));

  hasDeployPermission = computed<boolean>(() => this.permissionsQuery.data()?.permission === 'ADMIN' || this.permissionsQuery.data()?.permission === 'WRITE');
  hasUnlockPermission = computed<boolean>(() => this.permissionsQuery.data()?.roleName === 'admin' || this.permissionsQuery.data()?.roleName === 'maintain');

  private currentEnvironmentId: number | null = null;

  deployEnvironment = injectMutation(() => ({
    ...deployToEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
      if (this.currentEnvironmentId) {
        this.queryClient.invalidateQueries({ queryKey: getEnvironmentByIdQueryKey({ path: { id: this.currentEnvironmentId } }) });
      }
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Deployment started successfully' });
    },
  }));

  handleDeploy = (environment: EnvironmentDto) => {
    this.currentEnvironmentId = environment.id;
    this.deployEnvironment.mutate({ body: { environmentId: environment.id, branchName: this.sourceRef() } });
  };
}
