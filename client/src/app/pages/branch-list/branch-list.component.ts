import { Component, ChangeDetectionStrategy } from '@angular/core';
import { BranchTableComponent } from '../../components/branches-table/branches-table.component';

@Component({
  selector: 'app-branch-list',
  imports: [BranchTableComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './branch-list.component.html',
})
export class BranchListComponent {}
