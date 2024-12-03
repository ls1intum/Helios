import { inject, Injectable } from "@angular/core";
import { WorkflowRunControllerService, WorkflowRunDTO } from "@app/core/modules/openapi";
import { map, Observable } from "rxjs";

export interface WorkflowRunGroup {
  label: string;
  runs: WorkflowRunDTO[];
}

export interface Pipeline {
  groups: WorkflowRunGroup[];
}

const predefinedGroups: { label: string, matcher: Array<string> | ((name: string) => boolean) }[] = [
  {
    label: 'Validation',
    matcher: ['Chore', 'Validate OpenAPI Spec and Generated Client Code']
  },
  {
    label: 'Helper',
    matcher: ['Pull Request Labeler']
  },
  {
    label: 'All',
    matcher: () => true,
  }
];

@Injectable()
export class PipelineService {
  private controllerService = inject(WorkflowRunControllerService);

  getPullRequestPipeline(pullRequestId: number): Observable<Pipeline | null> {
    return this.controllerService.getLatestWorkflowRunsByPullRequestIdAndHeadCommit(pullRequestId)
      .pipe(
        map(runs => {
          const groups = this.groupRuns(runs);

          if (groups.length === 0) {
            return null;
          }

          return {
            groups,
          }
        })
      );
  }

  getBranchPipeline(branch: string): Observable<Pipeline | null> {
    return this.controllerService.getLatestWorkflowRunsByBranchAndHeadCommit(branch)
      .pipe(
        map(runs => {
          const groups = this.groupRuns(runs);

          if (groups.length === 0) {
            return null;
          }

          return {
            groups,
          }
        })
      );
  }

  private groupRuns(runs: WorkflowRunDTO[]): WorkflowRunGroup[] {
    const groups: WorkflowRunGroup[] = predefinedGroups.map(group => ({
      label: group.label,
      runs: runs.filter(run => {
        if (Array.isArray(group.matcher)) {
          return group.matcher.includes(run.name);
        }
        return group.matcher(run.name);
      }),
    }));

    return groups.filter(group => group.runs.length > 0);
  }
}
