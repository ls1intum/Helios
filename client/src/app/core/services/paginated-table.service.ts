import { computed, effect, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { Table, TablePageEvent } from 'primeng/table';
import { TreeTable } from 'primeng/treetable';

export type PaginatedTable = TreeTable | Table;

// Define a generic type for filter options
export type PaginatedFilterOption = {
  name: string;
  value: string;
};

// Define an injection token for filter options
export const PAGINATED_FILTER_OPTIONS_TOKEN = new InjectionToken<PaginatedFilterOption[]>('paginatedFilterOptions');

export interface PaginationState {
  page: number;
  size: number;
  sortField?: string;
  sortDirection?: string;
  filterType?: string;
  searchTerm?: string;
}

@Injectable()
export class PaginatedTableService<T> {
  // Inject filter options
  filterOptions = inject<PaginatedFilterOption[]>(PAGINATED_FILTER_OPTIONS_TOKEN);

  // Pagination state
  page = signal(1);
  size = signal(20);
  sortField = signal<string | undefined>(undefined);
  sortDirection = signal<string | undefined>('desc');
  searchTerm = signal<string>('');

  // Active filter tracking
  activeFilter = signal<PaginatedFilterOption>(this.filterOptions[0] || { name: 'All', value: 'ALL' });

  // Computed pagination state for queries
  paginationState = computed<PaginationState>(() => ({
    page: this.page(),
    size: this.size(),
    sortField: this.sortField(),
    sortDirection: this.sortDirection(),
    filterType: this.activeFilter().value,
    searchTerm: this.searchTerm() || undefined,
  }));

  constructor() {
    // Load state from localStorage (if present) upon construction
    console.log('Loading pagination state from localStorage');
    this.loadPaginationFromLocalStorage();

    // Whenever paginationState changes, save it to localStorage
    effect(() => {
      console.log('Pagination state changed:', this.paginationState());
      const state = this.paginationState();
      this.savePaginationToLocalStorage(state);
    });
  }

  private loadPaginationFromLocalStorage(): void {
    const storedStateStr = localStorage.getItem('pullRequestPaginationState');
    if (!storedStateStr) {
      console.log('No pagination state found in localStorage');
      return;
    }

    try {
      console.log('Parsing pagination state from localStorage');
      const storedState: PaginationState = JSON.parse(storedStateStr);

      this.page.set(storedState.page);
      this.size.set(storedState.size);
      if (typeof storedState.sortField === 'string') {
        console.log('Setting sortField:', storedState.sortField);
        this.sortField.set(storedState.sortField);
      }
      if (typeof storedState.sortDirection === 'string') {
        console.log('Setting sortDirection:', storedState.sortDirection);
        this.sortDirection.set(storedState.sortDirection);
      }
      if (typeof storedState.searchTerm === 'string') {
        console.log('Setting searchTerm:', storedState.searchTerm);
        this.searchTerm.set(storedState.searchTerm);
      }
      if (typeof storedState.filterType === 'string') {
        const matchedFilter = this.filterOptions.find(opt => opt.value === storedState.filterType);
        if (matchedFilter) {
          console.log('Setting active filter:', matchedFilter);
          this.activeFilter.set(matchedFilter);
        }
      }
    } catch (error) {
      console.warn('Error parsing paginationState from localStorage:', error);
    }
  }

  private savePaginationToLocalStorage(state: PaginationState): void {
    localStorage.setItem('pullRequestPaginationState', JSON.stringify(state));
  }

  filterType(): string {
    return this.activeFilter().value;
  }

  // Method to select a filter (similar to your existing code)
  selectFilter(filter: PaginatedFilterOption): void {
    this.activeFilter.set(filter);
    this.page.set(0); // Reset to first page
  }

  // Method to set filter by value (more convenient in some cases)
  setFilterType(filterValue: string): void {
    const filter = this.filterOptions.find(f => f.value === filterValue) || this.filterOptions[0];
    this.selectFilter(filter);
  }

  // Helper method to handle search term changes
  setSearchTerm(term: string | undefined): void {
    this.searchTerm.set(term || '');
    this.page.set(1);
  }

  // Clear all filters
  clearFilters(table: PaginatedTable): void {
    this.activeFilter.set(this.filterOptions[0]);
    this.searchTerm.set('');
    this.page.set(0);

    // If we have a table reference, reset its state too
    if (table) {
      table.first = 0;
    }
  }

  // PrimeNG table event handlers
  onPage(event: TablePageEvent) {
    console.log('Page event received:', event);

    // Calculate the actual page number from first and rows
    const calculatedPage = event.rows > 0 ? Math.floor(event.first / event.rows) + 1 : 1;

    // Update both size and page
    this.size.set(event.rows);
    this.page.set(calculatedPage);

    console.log(`Pagination updated: page=${calculatedPage}, size=${event.rows}`);
  }

  onSort(event: any) {
    this.sortField.set(event.field);
    this.sortDirection.set(event.order === 1 ? 'asc' : 'desc');
  }

  // Input handler for search (similar to your existing code)
  onInput(table: PaginatedTable, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchTerm.set(value);
    this.page.set(0);

    if (table) {
      table.first = 0;
    }
  }

  // Check if any filter is active (other than the default)
  hasActiveFilter(): boolean {
    return this.activeFilter() !== this.filterOptions[0];
  }
}
