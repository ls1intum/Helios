import {Component, computed, inject, input} from '@angular/core';
import {IconsModule} from 'icons.module';
import {TagModule} from 'primeng/tag';
import {DeploymentStoreService} from '@app/pages/environment-list/environment-list.component';
import {DeploymentDTO} from '@app/core/modules/openapi';
import { NgSwitch, NgSwitchCase, NgSwitchDefault} from '@angular/common';
import { DateService } from '@app/core/services/date.service';

@Component({
  selector: 'app-environment-commit-info',
  imports: [TagModule, IconsModule, NgSwitchCase, NgSwitch, NgSwitchDefault],
  templateUrl: './environment-commit-info.component.html',
})
export class EnvironmentCommitInfoComponent {
  private dateService = inject(DateService)
  private deploymentStore = inject(DeploymentStoreService);

  environmentId = input.required<number>();

  latestDeployment = computed<DeploymentDTO | null>(() => {
    const id = this.environmentId();
    return this.deploymentStore.getLatestDeploymentWithEnvironmentId(id);
  });

  // TODO: discuss with team if we should display the commit information in the UI

  // Derive fields directly from the DeploymentDTO
  state = computed(() => this.latestDeployment()?.state);
  commitHash = computed(() => this.latestDeployment()?.sha);
  commitMessage = computed(() => 'Placeholder commit text');
  branch = computed(() => this.latestDeployment()?.ref);
  committerName = computed(() => 'Unknown');
  commitDate = computed(() => this.dateService.formatDate(this.latestDeployment()?.createdAt, 'd. MMMM y, h:mm a'));
}
