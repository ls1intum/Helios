import { Component, input, OnInit, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconFilter, IconFilterPlus, IconX } from 'angular-tabler-icons/icons';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { Popover, PopoverModule } from 'primeng/popover';
import { PaginatedTable, PaginatedTableService } from '@app/core/services/paginated-table.service';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

const FILTER_BUTTON_BASE_CLASS =
  '!h-[2.125rem] !w-[2.35rem] !p-0 shrink-0 !border-[var(--p-inputtext-border-color)] !bg-transparent dark:!border-surface-500 dark:!bg-surface-0/8 dark:!text-primary-300 hover:dark:!bg-surface-0/12 transition-colors';
const ACTIVE_FILTER_BUTTON_CLASS = 'dark:!border-primary-400 dark:!bg-surface-0/14 dark:!text-primary-200';

@Component({
  selector: 'app-table-filter-paginated',
  imports: [ButtonModule, PopoverModule, InputTextModule, FormsModule, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconFilter,
      IconFilterPlus,
      IconX,
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

  filterButtonClass(): string {
    return `${FILTER_BUTTON_BASE_CLASS} ${this.paginationService().hasActiveFilter() ? ACTIVE_FILTER_BUTTON_CLASS : ''}`.trim();
  }

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
    this.searchTermSubject.next('');
  }

  onInput(event: Event) {
    console.log('onInput', event);
    const value = (event.target as HTMLInputElement).value;
    // Instead of hitting the service directly, just emit into our Subject
    this.searchTermSubject.next(value);
  }
}
