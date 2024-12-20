import {Component, computed, inject, input} from '@angular/core';
import {IconsModule} from 'icons.module';
import {TagModule} from 'primeng/tag';
import { DateService } from '@app/core/services/date.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { DeploymentDto } from '@app/core/modules/openapi2';
import { getLatestDeploymentByEnvironmentIdOptions } from '@app/core/modules/openapi2/@tanstack/angular-query-experimental.gen';

@Component({
  selector: 'app-environment-commit-info',
  imports: [TagModule, IconsModule],
  templateUrl: './environment-commit-info.component.html',
})
export class EnvironmentCommitInfoComponent {
  private dateService = inject(DateService)

  environmentId = input.required<number>();
  deploymentQuery = injectQuery(() => getLatestDeploymentByEnvironmentIdOptions({ path: { environmentId: this.environmentId() }}));

  latestDeployment = computed<DeploymentDto | undefined>(() => this.deploymentQuery.data());

  // TODO: discuss with team if we should display the commit information in the UI

  // Derive fields directly from the DeploymentDTO
  state = computed(() => this.latestDeployment()?.state);
  commitHash = computed(() => this.latestDeployment()?.sha);
  commitMessage = computed(() => 'Placeholder commit text');
  branch = computed(() => this.latestDeployment()?.ref);
  committerName = computed(() => 'Unknown');
  commitDate = computed(() => this.dateService.formatDate(this.latestDeployment()?.createdAt, 'd. MMMM y, h:mm a'));
}
