import { inject, Injectable, InjectionToken, signal } from '@angular/core';
import { Table } from 'primeng/table';
import { TreeTable } from 'primeng/treetable';
import { UserProfile } from './keycloak/user-profile';

export type SearchTable = TreeTable | Table;
export type FilterOption<T> = { name: string; filter: (prs: T[], userProfile?: UserProfile) => T[] };

export const FILTER_OPTIONS_TOKEN = new InjectionToken('filterOptions');

@Injectable({
  providedIn: 'root',
})
export class SearchTableService<T> {
  filterOptions = inject<FilterOption<T>[]>(FILTER_OPTIONS_TOKEN);

  searchValue = signal<string>('');
  activeFilter = signal<FilterOption<T>>(this.filterOptions[0]);

  clearFilter(table: SearchTable): void {
    table.filterGlobal('', 'contains');
    this.activeFilter.set(this.filterOptions[0]);
    this.searchValue.set('');
  }

  hasActiveFilter(): boolean {
    return this.activeFilter() !== this.filterOptions[0];
  }

  selectFilter(filter: FilterOption<T>): void {
    this.activeFilter.set(filter);
  }

  onInput(table: SearchTable, event: Event): void {
    table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
  }
}
