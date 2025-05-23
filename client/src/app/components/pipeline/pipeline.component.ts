import { Component, computed, input } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TableModule } from 'primeng/table';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { TooltipModule } from 'primeng/tooltip';
import {
  getLatestWorkflowRunsByBranchAndHeadCommitOptions,
  getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions,
  getGroupsWithWorkflowsOptions,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { SkeletonModule } from 'primeng/skeleton';
import { WorkflowRunDto } from '@app/core/modules/openapi';
import { PipelineTestResultsComponent } from './test-results/pipeline-test-results.component';
import { DividerModule } from 'primeng/divider';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCircleCheck, IconCircleX, IconExclamationCircle, IconExternalLink, IconInfoCircle, IconProgress, IconProgressHelp, IconBrandGithub } from 'angular-tabler-icons/icons';
import { ButtonModule } from 'primeng/button';

export type PipelineSelector = { repositoryId: number } & (
  | {
      branchName: string;
    }
  | {
      pullRequestId: number;
    }
);

export interface Pipeline {
  groups: {
    name: string;
    id: number;
    workflows: WorkflowRunDto[];
    isLastWithWorkflows: boolean;
  }[];
}

@Component({
  selector: 'app-pipeline',
  imports: [TableModule, DividerModule, ButtonModule, ProgressSpinnerModule, PanelModule, TablerIconComponent, TooltipModule, SkeletonModule, PipelineTestResultsComponent],
  providers: [
    provideTablerIcons({
      IconInfoCircle,
      IconCircleCheck,
      IconCircleX,
      IconProgressHelp,
      IconProgress,
      IconExternalLink,
      IconExclamationCircle,
      IconBrandGithub,
    }),
  ],
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

  pipeline = computed<Pipeline>(() => {
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

  allGroupsHaveNoWorkflowRuns = computed(() => {
    const pipelineData = this.pipeline();
    return pipelineData.groups.length > 0 && pipelineData.groups.every(group => group.workflows.length === 0);
  });

  openLink(url: string) {
    window.open(url, '_blank');
  }
}
