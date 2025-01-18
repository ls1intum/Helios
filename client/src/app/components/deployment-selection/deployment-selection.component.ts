import { Component, inject, input } from '@angular/core';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { deployToEnvironmentMutation, getAllEnabledEnvironmentsQueryKey, getEnvironmentByIdQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PermissionService } from '@app/core/services/permission.service';
import { injectMutation, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { EnvironmentListViewComponent } from '../environments/environment-list/environment-list-view.component';
import { EnvironmentStateService } from '@app/core/services/environment-state.service';

@Component({
  selector: 'app-deployment-selection',
  templateUrl: './deployment-selection.component.html',
  imports: [EnvironmentListViewComponent],
})
export class DeploymentSelectionComponent {
  private messageService = inject(MessageService);
  permissionService = inject(PermissionService);
  private environmentStateService = inject(EnvironmentStateService);

  queryClient = inject(QueryClient);

  sourceRef = input.required<string>();

  private currentEnvironmentId: number | null = null;

  deployEnvironment = injectMutation(() => ({
    ...deployToEnvironmentMutation(),
    onSuccess: () => {
      // Trigger global update after deployment
      this.environmentStateService.triggerEnvironmentUpdate();

      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
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
