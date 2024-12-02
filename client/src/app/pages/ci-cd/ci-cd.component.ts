import { Component } from '@angular/core';
import { PullRequestTableComponent } from '@app/components/pull-request-table/pull-request-table.component';
import { TabViewModule } from 'primeng/tabview';
@Component({
  selector: 'app-ci-cd',
  imports: [PullRequestTableComponent, TabViewModule],
  templateUrl: './ci-cd.component.html',
  styleUrl: './ci-cd.component.css'
})
export class CiCdComponent {}
