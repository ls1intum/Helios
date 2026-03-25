import { Component, computed, inject, input } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getActivityHistoryByPullRequestIdOptions, getActivityHistoryByRepositoryIdAndBranchNameOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { CommonModule } from '@angular/common';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TooltipModule } from 'primeng/tooltip';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCloudUpload } from 'angular-tabler-icons/icons';

/** Selector for deployment history: either branch or pull request. */
export type DeploymentHistorySelector = { repositoryId: number; branchName: string } | { pullRequestId: number };

@Component({
  selector: 'app-deployment-history',
  standalone: true,
  imports: [CommonModule, TablerIconComponent, SkeletonModule, TableModule, AvatarModule, TooltipModule, UserAvatarComponent],
  providers: [
    provideTablerIcons({
      IconCloudUpload,
    }),
  ],
  templateUrl: './deployment-history.component.html',
})
export class DeploymentHistoryComponent {
  dateService = inject(DateService);

  selector = input.required<DeploymentHistorySelector | null>();

  private branchSelector = computed(() => {
    const s = this.selector();
    return s && 'branchName' in s ? s : null;
  });
  private pullRequestSelector = computed(() => {
    const s = this.selector();
    return s && 'pullRequestId' in s ? s : null;
  });

  branchQuery = injectQuery(() => {
    const sel = this.branchSelector();
    return {
      ...getActivityHistoryByRepositoryIdAndBranchNameOptions({
        path: {
          repositoryId: sel?.repositoryId ?? 0,
        },
        query: {
          branch: sel?.branchName ?? '',
        },
      }),
      enabled: sel !== null,
    };
  });

  pullRequestQuery = injectQuery(() => {
    const sel = this.pullRequestSelector();
    return {
      ...getActivityHistoryByPullRequestIdOptions({
        path: { pullRequestId: sel?.pullRequestId ?? 0 },
      }),
      enabled: sel !== null,
    };
  });

  /** The active query (branch or PR) based on selector. */
  activityHistoryQuery = computed(() => (this.branchSelector() ? this.branchQuery : this.pullRequestQuery));

  activityHistory = computed(() => this.activityHistoryQuery().data());
  isLoading = computed(() => this.activityHistoryQuery().isPending());
  isError = computed(() => this.activityHistoryQuery().isError());

  emptyMessage = computed(() => (this.branchSelector() ? 'No deployment history for this branch.' : 'No deployment history for this pull request.'));
  emptyDescription = computed(() =>
    this.branchSelector() ? 'Deployments will appear here once you deploy from this branch.' : 'Deployments will appear here once you deploy from this PR.'
  );
}
