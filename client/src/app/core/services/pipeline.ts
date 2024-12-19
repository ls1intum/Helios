import { Injectable } from "@angular/core";
import { WorkflowDto, WorkflowRunDto } from "../modules/openapi";

export interface WorkflowRunGroup {
  label: string;
  runs: WorkflowRunDto[];
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
  groupRuns(runs: WorkflowRunDto[]): WorkflowRunGroup[] {
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
