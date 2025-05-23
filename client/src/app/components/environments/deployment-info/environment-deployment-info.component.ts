import { Component, computed, inject, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { EnvironmentDeployment } from '@app/core/modules/openapi';
import { DatePipe, SlicePipe } from '@angular/common';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getCommitByRepositoryIdAndNameOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { AvatarModule } from 'primeng/avatar';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { TooltipModule } from 'primeng/tooltip';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconPoint, IconUser } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-environment-deployment-info',
  imports: [TagModule, TablerIconComponent, AvatarModule, TimeAgoPipe, TooltipModule, SlicePipe],
  providers: [
    DatePipe,
    provideTablerIcons({
      IconUser,
      IconPoint,
    }),
  ],
  templateUrl: './environment-deployment-info.component.html',
})
export class EnvironmentDeploymentInfoComponent {
  private datePipe = inject(DatePipe);

  repositoryId = input.required<number>();
  deployment = input.required<EnvironmentDeployment>();

  commitQuery = injectQuery(() => ({
    ...getCommitByRepositoryIdAndNameOptions({ path: { repoId: this.repositoryId(), sha: this.deployment()?.sha || '' } }),
    enabled: !!this.repositoryId(),
  }));
  commit = computed(() => {
    const data = this.commitQuery.data();

    // If the data is an empty object, treat it as null
    if (data && typeof data === 'object' && !Object.keys(data).length) {
      return null;
    }

    return data;
  });

  commitDate = computed(() => {
    const date = this.commit()?.authoredAt;
    return date ? this.datePipe.transform(date, 'd MMMM y, h:mm a') : null; // Format date
  });
}
