import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { PrimeTemplate } from 'primeng/api';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { getActivityHistoryByEnvironmentIdOptions, getEnvironmentByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { CommonModule } from '@angular/common';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { AvatarModule } from 'primeng/avatar';
import { TooltipModule } from 'primeng/tooltip';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCloudUpload, IconGitBranch, IconGitCommit, IconLock, IconLockOpen, IconStatusChange } from 'angular-tabler-icons/icons';
@Component({
  selector: 'app-environment-deployment-history',
  imports: [CommonModule, TablerIconComponent, PrimeTemplate, SkeletonModule, TableModule, PageHeadingComponent, AvatarModule, TooltipModule, UserAvatarComponent],
  providers: [
    provideTablerIcons({
      IconCloudUpload,
      IconLock,
      IconLockOpen,
      IconStatusChange,
      IconGitBranch,
      IconGitCommit,
    }),
  ],
  templateUrl: './environment-deployment-history.component.html',
})
export class EnvironmentDeploymentHistoryComponent {
  dateService = inject(DateService);

  environmentId = input.required({ transform: numberAttribute });
  repositoryId = input.required({ transform: numberAttribute });

  environmentQuery = injectQuery(() => getEnvironmentByIdOptions({ path: { id: this.environmentId() } }));
  environment = computed(() => this.environmentQuery.data());

  activityHistoryQuery = injectQuery(() => getActivityHistoryByEnvironmentIdOptions({ path: { environmentId: this.environmentId() } }));
  activityHistory = computed(() => this.activityHistoryQuery.data());
}
