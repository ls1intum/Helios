import { Component, input, numberAttribute } from '@angular/core';
import { PullRequestTableComponent } from '@app/components/pull-request-table/pull-request-table.component';

@Component({
  selector: 'app-pull-request-list',
  imports: [PullRequestTableComponent],
  templateUrl: './pull-request-list.component.html',
})
export class PullRequestListComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
