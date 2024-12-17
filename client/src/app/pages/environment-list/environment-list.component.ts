import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { EnvironmentListViewComponent } from '@app/components/environments/environment-list/environment-list-view.component';

@Component({
  selector: 'app-environment-list',
  imports: [EnvironmentListViewComponent, CommonModule],
  templateUrl: './environment-list.component.html',
})
export class EnvironmentListComponent {
}
