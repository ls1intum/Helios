import { Component, input, OnInit, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconFilter, IconFilterPlus } from 'angular-tabler-icons/icons';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { Popover, PopoverModule } from 'primeng/popover';
import { PaginatedTable, PaginatedTableService } from '@app/core/services/paginated-table.service';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

@Component({
  selector: 'app-table-filter-paginated',
  imports: [ButtonModule, PopoverModule, InputTextModule, FormsModule, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconFilter,
      IconFilterPlus,
    }),
  ],
  templateUrl: './table-filter-paginated.component.html',
})
export class TableFilterPaginatedComponent implements OnInit {
  paginationService = input.required<PaginatedTableService>();
  table = input.required<PaginatedTable>();
  op = viewChild.required<Popover>('op');

  localSearchTerm = '';
  // Subject that emits every keystroke
  private searchTermSubject = new Subject<string>();

  // The constructor sets up a subscription that waits 300ms of “no new keystrokes”
  // before actually updating the paginationService’s searchTerm.
  ngOnInit() {
    // 1) Whenever user stops typing for 300ms, update the actual service
    this.searchTermSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(searchValue => {
      this.paginationService().setSearchTerm(searchValue);
    });

    // load a previously saved searchTerm into the input field
    this.localSearchTerm = this.paginationService().searchTerm() || '';
  }

  toggle(event: Event) {
    this.op().toggle(event);
  }

  clearSearch(): void {
    this.localSearchTerm = '';
  }

  onInput(event: Event) {
    console.log('onInput', event);
    const value = (event.target as HTMLInputElement).value;
    // Instead of hitting the service directly, just emit into our Subject
    this.searchTermSubject.next(value);
  }
}
