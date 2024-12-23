import { Component } from '@angular/core';
import { BranchTableComponent } from "../../components/branches-table/branches-table.component";

@Component({
  selector: 'app-branch-list',
  imports: [BranchTableComponent],
  templateUrl: './branch-list.component.html',
})
export class BranchListComponent {

}
