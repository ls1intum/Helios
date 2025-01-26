import {Component, computed, input} from '@angular/core';
import {Skeleton} from 'primeng/skeleton';
import {TablerIconComponent} from 'angular-tabler-icons';
import {Tooltip} from 'primeng/tooltip';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {
  getLatestWorkflowRunsByBranchAndHeadCommitOptions,
  getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import {WorkflowRunDto} from '@app/core/modules/openapi';


export type WorkflowRunSelector = {
  type: 'pullRequest';
  pullRequestId: number;
} | {
  type: 'branch';
  branchName: string;
};


@Component({
  selector: 'app-workflow-run-status',
  imports: [
    Skeleton,
    TablerIconComponent,
    Tooltip
  ],
  templateUrl: './workflow-run-status.component.html',
})
export class WorkflowRunStatusComponent {
  /**
   * Union type input: user must pass either
   * {type: 'pullRequest', pullRequestId: ...} or
   * {type: 'branch', branchName: ...}.
   */
  selector = input.required<WorkflowRunSelector>();

  /**
   * Optional: set an interval to reFetch the data (e.g. 15000 for 15s)
   */
  reFetchInterval = input<number>(15000);

  // Query for branch
  branchQuery = injectQuery(() => {
    const selector = this.selector();

    if (selector.type === 'branch') {
      const branchName = selector.branchName;
      return {
        ...getLatestWorkflowRunsByBranchAndHeadCommitOptions({
          query: { branch: branchName },
        }),
        enabled: true,
        refetchInterval: this.reFetchInterval(),
      };
    }

    // Disabled query
    return {
      ...getLatestWorkflowRunsByBranchAndHeadCommitOptions({
        query: { branch: '' },
      }),
      enabled: false,
    };
  });

  pullRequestQuery = injectQuery(() => {
    const selector = this.selector();

    if (selector.type === 'pullRequest') {
      const pullRequestId = selector.pullRequestId;
      return {
        ...getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions({
          path: { pullRequestId },
        }),
        enabled: true,
        refetchInterval: this.reFetchInterval(),
      };
    }

    // Disabled query
    return {
      ...getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions({
        path: { pullRequestId: 0 },
      }),
      enabled: false,
    };
  });


  isLoading = computed(() => {
    if (this.selector().type === 'branch') {
      return this.branchQuery.isPending();
    } else if (this.selector().type === 'pullRequest') {
      return this.pullRequestQuery.isPending();
    }
    return false;
  });

  isError = computed(() => {
    if (this.selector().type === 'branch') {
      return this.branchQuery.isError();
    } else if (this.selector().type === 'pullRequest') {
      return this.pullRequestQuery.isError();
    }
    return false;
  });


  workflowRuns = computed<WorkflowRunDto[]>(() => {
    if (this.selector().type === 'branch') {
      return this.branchQuery.data() || [];
    } else if (this.selector().type === 'pullRequest') {
      return this.pullRequestQuery.data() || [];
    }
    return [];
  });


  workflowStatus = computed(() => {
    const runs = this.workflowRuns();

    // Some runs are in progress --> in progress
    if (runs.some(run => ['IN_PROGRESS', 'QUEUED', 'PENDING'].includes(run.status))) {
      return {
        icon: 'clock',
        color: 'text-yellow-500',
        tooltip: 'Workflow In Progress',
      };
    }

    // All completed successfully --> success
    if (
      runs.length > 0 &&
      runs.every(run => run.status === 'COMPLETED' && run.conclusion === 'SUCCESS')
    ) {
      return {
        icon: 'check',
        color: 'text-green-600',
        tooltip: 'All Workflows Passed',
      };
    }

    // Some run has failed or was cancelled --> failure
    if (
      runs.some(run =>
        run.status === 'COMPLETED' && ['FAILURE', 'CANCELLED', 'TIMED_OUT'].includes(run.conclusion ?? '')
      )
    ) {
      return {
        icon: 'x',
        color: 'text-red-600',
        tooltip: 'Some Workflows Failed',
      };
    }

    // Default or no runs --> unknown
    return {
      icon: 'minus',
      color: 'text-gray-500',
      tooltip: 'No Workflows or Unknown Status',
    };
  });


}
