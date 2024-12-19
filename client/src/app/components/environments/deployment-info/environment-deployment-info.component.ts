import {Component, computed, inject, input} from '@angular/core';
import {IconsModule} from 'icons.module';
import {TagModule} from 'primeng/tag';
import {EnvironmentDeployment} from '@app/core/modules/openapi';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-environment-deployment-info',
  imports: [TagModule, IconsModule],
  providers: [DatePipe],
  templateUrl: './environment-deployment-info.component.html',
})
export class EnvironmentDeploymentInfoComponent {
  private datePipe = inject(DatePipe);

  deployment = input.required<EnvironmentDeployment>();

  // TODO: discuss with team if we should display the commit information in the UI

  // Derive fields directly from the DeploymentDTO
  commitHash = computed(() => this.deployment()?.sha);
  commitMessage = computed(() => 'Placeholder commit text');
  branch = computed(() => this.deployment()?.ref);
  committerName = computed(() => 'Unknown');
  commitDate = computed(() => {
    const date = this.deployment()?.createdAt;
    return date ? this.datePipe.transform(date, 'd MMMM y, h:mm a') : null; // Format date
  });
}
