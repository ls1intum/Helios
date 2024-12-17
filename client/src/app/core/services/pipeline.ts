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
    label: 'Build',
    matcher: ['Build', 'Pull Request Labeler']
  },
  {
    label: 'Test',
    matcher: ['Test', 'CodeQL', 'Validate PR Title', 'Check if German and English translations are consistent']
  },
  {
    label: 'Deployment',
    matcher: ['Testserver Locks', 'Deploy to Testserver', 'Deploy']
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
