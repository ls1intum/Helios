import { Component, computed, input, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SearchTable } from '@app/core/services/search-table.service';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconFilter, IconFilterPlus } from 'angular-tabler-icons/icons';
import { ButtonModule } from 'primeng/button';
import { DataView } from 'primeng/dataview';
import { InputTextModule } from 'primeng/inputtext';
import { Popover, PopoverModule } from 'primeng/popover';

@Component({
  selector: 'app-table-filter',
  imports: [ButtonModule, PopoverModule, InputTextModule, FormsModule, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconFilter,
      IconFilterPlus,
    }),
  ],
  templateUrl: './table-filter.component.html',
})
export class TableFilterComponent {
  input = input.required<SearchTable | DataView>();
  table = computed(() => this.input() as SearchTable);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  searchTableService = input.required<any>();
  op = viewChild.required<Popover>('op');

  toggle(event: Event) {
    this.op().toggle(event);
  }
}
