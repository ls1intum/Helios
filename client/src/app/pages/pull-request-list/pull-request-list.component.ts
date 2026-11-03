import { Component, input, numberAttribute, ChangeDetectionStrategy } from '@angular/core';
import { PullRequestTableComponent } from '@app/components/pull-request-table/pull-request-table.component';

@Component({
  selector: 'app-pull-request-list',
  imports: [PullRequestTableComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './pull-request-list.component.html',
})
export class PullRequestListComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
