import { Component, inject, input } from "@angular/core";
import { EnvironmentListViewComponent } from "../environments/environment-list/environment-list-view.component";
import { injectMutation, injectQueryClient } from "@tanstack/angular-query-experimental";
import { deployToEnvironmentMutation, getAllEnvironmentsQueryKey } from "@app/core/modules/openapi/@tanstack/angular-query-experimental.gen";
import { EnvironmentDto } from "@app/core/modules/openapi";

@Component({
    selector: 'app-deployment-selection',
    templateUrl: './deployment-selection.component.html',
    imports: [EnvironmentListViewComponent],
})
export class DeploymentSelectionComponent {
  queryClient = injectQueryClient();

  sourceRef = input.required<string>();

  deployEnvironment = injectMutation(() => ({
    ...deployToEnvironmentMutation(),
    onSuccess: () => {
      this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey });
    }
  }));

  handleDeploy = (environment: EnvironmentDto) => {
    this.deployEnvironment.mutate({ body: { environmentId: environment.id, branchName: this.sourceRef() }});
  }
}
