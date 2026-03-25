import { Component, input, numberAttribute } from '@angular/core';
import { WorkflowRunsTableComponent } from '@app/components/workflow-runs-table/workflow-runs-table.component';

@Component({
  selector: 'app-workflow-runs',
  standalone: true,
  imports: [WorkflowRunsTableComponent],
  templateUrl: './workflow-run-list.component.html',
})
export class WorkflowRunListComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
