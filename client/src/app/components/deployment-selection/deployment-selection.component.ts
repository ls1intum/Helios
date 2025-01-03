import { Component, inject, input } from '@angular/core';
import { EnvironmentListViewComponent } from '../environments/environment-list/environment-list-view.component';
import { injectMutation, injectQueryClient } from '@tanstack/angular-query-experimental';
import { deployToEnvironmentMutation, getAllEnvironmentsQueryKey, getEnvironmentByIdQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { MessageService } from 'primeng/api';

@Component({
  selector: 'app-deployment-selection',
  templateUrl: './deployment-selection.component.html',
  imports: [EnvironmentListViewComponent],
})
export class DeploymentSelectionComponent {
  private messageService = inject(MessageService);

  queryClient = injectQueryClient();

  sourceRef = input.required<string>();

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
