import {Component, computed, inject, input} from '@angular/core';
import {IconsModule} from 'icons.module';
import {TagModule} from 'primeng/tag';
import {DeploymentDTO} from '@app/core/modules/openapi';
import {DatePipe, NgSwitch, NgSwitchCase, NgSwitchDefault} from '@angular/common';
import { DeploymentStoreService } from '../environment-list/environment-list-view.component';

@Component({
  selector: 'app-environment-commit-info',
  imports: [TagModule, IconsModule, NgSwitchCase, NgSwitch, NgSwitchDefault],
  providers: [DatePipe],
  templateUrl: './environment-commit-info.component.html',
  styleUrl: './environment-commit-info.component.css',
})
export class EnvironmentCommitInfoComponent {
  private datePipe = inject(DatePipe);
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
  commitDate = computed(() => {
    const date = this.latestDeployment()?.createdAt;
    return date ? this.datePipe.transform(date, 'd MMMM y, h:mm a') : null; // Format date
  });
}
