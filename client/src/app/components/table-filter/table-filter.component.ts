import { Component, input, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SearchTable, SearchTableService } from '@app/core/services/search-table.service';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { Popover, PopoverModule } from 'primeng/popover';

@Component({
  selector: 'app-table-filter',
  imports: [ButtonModule, PopoverModule, InputTextModule, FormsModule, IconsModule],
  templateUrl: './table-filter.component.html',
})
export class TableFilterComponent<T> {
  table = input.required<SearchTable>();
  searchTableService = input.required<SearchTableService<T>>();
  op = viewChild.required<Popover>('op');

  toggle(event: Event) {
    this.op().toggle(event);
  }
}
