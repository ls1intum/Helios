import { Component, computed, input, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SearchTable } from '@app/core/services/search-table.service';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconFilter, IconFilterPlus } from 'angular-tabler-icons/icons';
import { ButtonModule } from 'primeng/button';
import { DataView } from 'primeng/dataview';
import { InputTextModule } from 'primeng/inputtext';
import { Popover, PopoverModule } from 'primeng/popover';

const FILTER_BUTTON_BASE_CLASS =
  '!h-[2.125rem] !w-[2.35rem] !p-0 shrink-0 !border-[var(--p-inputtext-border-color)] !bg-transparent dark:!border-surface-500 dark:!bg-surface-0/8 dark:!text-primary-300 hover:dark:!bg-surface-0/12 transition-colors';
const ACTIVE_FILTER_BUTTON_CLASS = 'dark:!border-primary-400 dark:!bg-surface-0/14 dark:!text-primary-200';

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
  inputChanged = output<void>();
  op = viewChild.required<Popover>('op');

  filterButtonClass(): string {
    return `${FILTER_BUTTON_BASE_CLASS} ${this.searchTableService().hasActiveFilter() ? ACTIVE_FILTER_BUTTON_CLASS : ''}`.trim();
  }

  toggle(event: Event) {
    this.op().toggle(event);
  }

  onInput(event: Event): void {
    this.searchTableService().onInput(this.table(), event);
    this.inputChanged.emit();
  }
}
