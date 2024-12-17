import { Component, inject } from "@angular/core";
import { EnvironmentListViewComponent } from "../environments/environment-list/environment-list-view.component";
import { DeploymentControllerService, EnvironmentDTO } from "@app/core/modules/openapi";
import { injectMutation } from "@tanstack/angular-query-experimental";
import { queryClient } from "@app/app.config";
import { lastValueFrom } from "rxjs";

@Component({
    selector: 'app-deployment-selection',
    templateUrl: './deployment-selection.component.html',
    imports: [EnvironmentListViewComponent],
})
export class DeploymentSelectionComponent {
  deploymentService = inject(DeploymentControllerService);

  deployEnvironment = injectMutation(() => ({
    mutationFn: (environment: EnvironmentDTO) =>
     lastValueFrom(this.deploymentService.deployToEnvironment({
        branchName: 'main',
        environmentId: environment.id,
      })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments'] });
    }
  }));

  handleDeploy = (environment: EnvironmentDTO) => {
    this.deployEnvironment.mutate(environment);
  }
}
