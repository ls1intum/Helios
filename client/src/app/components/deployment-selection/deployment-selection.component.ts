/* eslint-disable prettier/prettier */
import { Component, computed, inject, input } from '@angular/core';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { deployToEnvironmentMutation, getAllEnvironmentsQueryKey, getEnvironmentByIdQueryKey, getRepoPermissionsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
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
  repositoryId = input.required<number>();

  permissionsQuery = injectQuery(() => ({
    ...getRepoPermissionsOptions({
      path: { repoId: this.repositoryId() }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any),
    enabled: () => !!this.repositoryId(),
  }));

  hasDeployPermissions = computed<boolean>(() => this.permissionsQuery.data() === 'WRITE' || this.permissionsQuery.data() === 'ADMIN');
  hasAdminPermission = computed<boolean>(() => this.permissionsQuery.data() === 'ADMIN');

  private currentEnvironmentId: number | null = null;

  deployEnvironment = injectMutation(() => ({
    ...deployToEnvironmentMutation(),
    onSuccess: () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey({} as any) });
      if (this.currentEnvironmentId) {
        this.queryClient.invalidateQueries({ queryKey: getEnvironmentByIdQueryKey({ path: { id: this.currentEnvironmentId } }) });
      }
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Deployment started successfully' });
    },
  }));

  handleDeploy = (environment: EnvironmentDto) => {
    this.currentEnvironmentId = environment.id;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    this.deployEnvironment.mutate({ body: { environmentId: environment.id, branchName: this.sourceRef() } } as any);
  };
}
