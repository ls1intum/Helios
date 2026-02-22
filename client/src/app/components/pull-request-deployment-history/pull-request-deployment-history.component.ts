import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getActivityHistoryByPullRequestIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { CommonModule } from '@angular/common';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TooltipModule } from 'primeng/tooltip';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCloudUpload } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-pull-request-activity-history',
  standalone: true,
  imports: [CommonModule, TablerIconComponent, SkeletonModule, TableModule, AvatarModule, TooltipModule, UserAvatarComponent],
  providers: [
    provideTablerIcons({
      IconCloudUpload,
    }),
  ],
  templateUrl: './pull-request-deployment-history.component.html',
})
export class PullRequestDeploymentHistoryComponent {
  dateService = inject(DateService);

  pullRequestId = input.required({ transform: numberAttribute });

  activityHistoryQuery = injectQuery(() => getActivityHistoryByPullRequestIdOptions({ path: { pullRequestId: this.pullRequestId() } }));
  activityHistory = computed(() => this.activityHistoryQuery.data());
}
