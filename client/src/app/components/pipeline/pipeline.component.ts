import { Component, computed, inject, input, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TableModule } from 'primeng/table';
import { PipelineService } from '@app/core/services/pipeline';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import { getLatestWorkflowRunsByBranchAndHeadCommitOptions, getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { SkeletonModule } from 'primeng/skeleton';

export type PipelineSelector = { repositoryId: number } & ({
  branchName: string;
} | {
  pullRequestId: number;
});

@Component({
  selector: 'app-pipeline',
  imports: [TableModule, ProgressSpinnerModule, PanelModule, IconsModule, TooltipModule, SkeletonModule],
  templateUrl: './pipeline.component.html',
})
export class PipelineComponent {
  pipelineService = inject(PipelineService);

  branchName = input<string>('');
  pullRequestId = input<number>();

  branchQuery = injectQuery(() => ({
    ...getLatestWorkflowRunsByBranchAndHeadCommitOptions({ path: { branch: this.branchName() }}),
    refetchInterval: 2000,
  }));
  pullRequestQuery = injectQuery(() => ({
    ...getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions({path: { pullRequestId: this.pullRequestId() || 0 }}),
    refetchInterval: 2000,
  }));

  pipeline = computed(() => {
    const workflowRuns = (this.branchName() ? this.branchQuery.data() : this.pullRequestQuery.data()) || [];
    const groups = this.pipelineService.groupRuns(workflowRuns);

    if (groups.length === 0) {
      return null;
    }

    return {
      groups,
    }
  });
}
