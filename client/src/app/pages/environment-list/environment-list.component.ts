import { Component, input, numberAttribute } from '@angular/core';
import { EnvironmentListViewComponent } from '@app/components/environments/environment-list/environment-list-view.component';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';

@Component({
  selector: 'app-environment-list',
  imports: [EnvironmentListViewComponent, PageHeadingComponent],
  templateUrl: './environment-list.component.html',
})
export class EnvironmentListComponent {
  repositoryId = input.required({ transform: numberAttribute });
}
