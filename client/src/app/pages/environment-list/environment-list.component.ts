import { Component } from '@angular/core';
import { EnvironmentListViewComponent } from '@app/components/environments/environment-list/environment-list-view.component';

@Component({
  selector: 'app-environment-list',
  imports: [EnvironmentListViewComponent],
  templateUrl: './environment-list.component.html',
})
export class EnvironmentListComponent {}
