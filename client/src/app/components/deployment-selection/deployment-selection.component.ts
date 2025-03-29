import { Component, inject, input } from '@angular/core';
import { EnvironmentDto } from '@app/core/modules/openapi';
import {
  deployToEnvironmentMutation,
  getAllEnabledEnvironmentsQueryKey,
  getEnvironmentByIdQueryKey,
  getEnvironmentsByUserLockingQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { EnvironmentListViewComponent } from '../environments/environment-list/environment-list-view.component';

@Component({
  selector: 'app-deployment-selection',
  templateUrl: './deployment-selection.component.html',
  imports: [EnvironmentListViewComponent],
})
export class DeploymentSelectionComponent {
  private messageService = inject(MessageService);

  queryClient = inject(QueryClient);

  sourceRef = input.required<string>();
  commitSha = input.required<string>();

  private currentEnvironmentId: number | null = null;

  deployEnvironment = injectMutation(() => ({
    ...deployToEnvironmentMutation(),
    onSuccess: () => {
      // Trigger update on main layout after deployment
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });

      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
      if (this.currentEnvironmentId) {
        this.queryClient.invalidateQueries({ queryKey: getEnvironmentByIdQueryKey({ path: { id: this.currentEnvironmentId } }) });
      }
      this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Deployment started successfully' });
    },
  }));

  handleDeploy = (environment: EnvironmentDto) => {
    this.currentEnvironmentId = environment.id;
    this.deployEnvironment.mutate({ body: { environmentId: environment.id, branchName: this.sourceRef(), commitSha: this.commitSha() } });
  };
}
