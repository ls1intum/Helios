import { Component, computed, input } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TableModule } from 'primeng/table';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import {
  getLatestWorkflowRunsByBranchAndHeadCommitOptions,
  getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions,
  getGroupsWithWorkflowsOptions,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { SkeletonModule } from 'primeng/skeleton';

export type PipelineSelector = { repositoryId: number } & (
  | {
      branchName: string;
    }
  | {
      pullRequestId: number;
    }
);

@Component({
  selector: 'app-pipeline',
  imports: [TableModule, ProgressSpinnerModule, PanelModule, IconsModule, TooltipModule, SkeletonModule],
  templateUrl: './pipeline.component.html',
})
export class PipelineComponent {
  selector = input<PipelineSelector | null>();

  branchName = computed(() => {
    const selector = this.selector();
    if (!selector) return null;
    return 'branchName' in selector ? selector.branchName : null;
  });
  pullRequestId = computed(() => {
    const selector = this.selector();
    if (!selector) return null;
    return 'pullRequestId' in selector ? selector.pullRequestId : null;
  });
  repositoryId = computed(() => {
    const selector = this.selector();
    if (!selector) return null;
    return selector.repositoryId;
  });
  //TODO instead of refetching every 15 seconds, we should use websockets to get real-time updates
  branchQuery = injectQuery(() => ({
    ...getLatestWorkflowRunsByBranchAndHeadCommitOptions({ query: { branch: this.branchName()! } }),
    enabled: this.branchName() !== null,
    refetchInterval: 15000,
  }));
  pullRequestQuery = injectQuery(() => ({
    ...getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions({ path: { pullRequestId: this.pullRequestId() || 0 } }),
    enabled: this.pullRequestId() !== null,
    refetchInterval: 15000,
  }));

  groupsQuery = injectQuery(() => ({
    ...getGroupsWithWorkflowsOptions({ path: { repositoryId: this.repositoryId() || 0 } }),
    refetchInterval: 15000,
  }));

  pipeline = computed(() => {
    const workflowRuns = (this.branchName() ? this.branchQuery.data() : this.pullRequestQuery.data()) || [];
    const workflowGroups = this.groupsQuery.data() || [];

    const groupedWorkflowsRuns = workflowGroups.map(group => {
      const workflowIds = group.memberships?.map(membership => membership.workflowId) || [];
      const matchingRuns = workflowRuns.filter(run => workflowIds.includes(run.workflowId));
      return {
        name: group.name,
        id: group.id,
        workflows: matchingRuns,
        isLastWithWorkflows: false,
      };
    });

    let lastWithWorkflowsFound = false;
    // For loop in reverse to set isLastWithWorkflows in the correct order
    groupedWorkflowsRuns.reverse().forEach(group => {
      group.isLastWithWorkflows = !lastWithWorkflowsFound && group.workflows.length > 0;
      if (group.isLastWithWorkflows) lastWithWorkflowsFound = true;
    });
    // Reverse back to the original order
    groupedWorkflowsRuns.reverse();

    return {
      groups: groupedWorkflowsRuns,
    };
  });
}
