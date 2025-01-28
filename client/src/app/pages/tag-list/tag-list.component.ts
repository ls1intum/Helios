import { Component } from '@angular/core';
import { TagTableComponent } from '@app/components/tag-table/tag-table.component';

@Component({
  selector: 'app-tag-list',
  imports: [TagTableComponent],
  templateUrl: './tag-list.component.html',
})
export class TagListComponent {}
