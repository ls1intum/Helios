import { Component, input, numberAttribute, ChangeDetectionStrategy } from '@angular/core';
import { WorkflowRunsTableComponent } from '@app/components/workflow-runs-table/workflow-runs-table.component';

@Component({
  selector: 'app-workflow-runs',
  standalone: true,
  imports: [WorkflowRunsTableComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './workflow-run-list.component.html',
})
export class WorkflowRunListComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
