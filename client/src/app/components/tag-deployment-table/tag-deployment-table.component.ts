import { Component, input } from '@angular/core';
import { EnvironmentDto, TagDetailsDto } from '@app/core/modules/openapi';
import { getAllEnabledEnvironmentsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { OverlayBadgeModule } from 'primeng/overlaybadge';
import { BadgeModule } from 'primeng/badge';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-tag-deployment-table',
  imports: [TableModule, ButtonModule, IconsModule, SkeletonModule, TagModule, AvatarModule, OverlayBadgeModule, BadgeModule, TooltipModule],
  templateUrl: './tag-deployment-table.component.html',
})
export class TagDeploymentTableComponent {
  tag = input.required<TagDetailsDto>();

  environmentQuery = injectQuery(() => getAllEnabledEnvironmentsOptions());

  deploymentStatus = (environment: EnvironmentDto) => {
    let deployments = this.tag().deployments.filter(deployment => deployment.environment.id === environment.id);

    // If there are no deployments related to this tag anymore, this tag was not deployed yet
    if (deployments.length === 0) {
      return 'NEVER_DEPLOYED';
    }

    // If the latest deployment is not part of the deployments list, but there were deployments to this environment, the tag was replaced
    if (deployments.every(deployment => environment.latestDeployment?.id !== deployment.id)) {
      return 'REPLACED';
    }

    // For the following cases, we are only interested in the deployment that matches the latest deployment of the environment
    const deployment = deployments.find(deployment => environment.latestDeployment?.id === deployment.id);

    // If there is no deployment that matches the latest deployment, the tag was replaced
    if (deployment === undefined) {
      return 'REPLACED';
    }

    if (deployment.state === 'SUCCESS') {
      return 'SUCCESS';
    }

    if (deployment.state === 'FAILURE') {
      return 'FAILURE';
    }
    return 'UNKNOWN';
  };
}
