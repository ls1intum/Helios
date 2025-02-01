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

    if (deployments.length === 0) {
      return 'NEVER_DEPLOYED';
    }

    if (deployments.every(deployment => environment.latestDeployment?.id !== deployment.id)) {
      return 'REPLACED';
    }

    if (deployments.every(deployment => deployment.state === 'SUCCESS')) {
      return 'SUCCESS';
    }

    if (deployments.some(deployment => deployment.state === 'FAILURE')) {
      return 'FAILURE';
    }
    return 'UNKNOWN';
  };
}
