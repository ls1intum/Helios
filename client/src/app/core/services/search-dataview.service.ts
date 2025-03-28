import { Injectable, signal } from '@angular/core';
import { DataView } from 'primeng/dataview';

@Injectable({
  providedIn: 'root',
})
export class SearchDataViewService {
  searchValue = signal<string>('');
  filterOptions: Record<string, unknown>[] = [];

  clearFilter(dataView: DataView): void {
    dataView.filter('', 'contains');
    this.searchValue.set('');
  }

  hasActiveFilter(): boolean {
    return false;
  }

  activeFilter() {
    return [] as Record<string, unknown>[];
  }

  onInput(dataView: DataView, event: Event): void {
    dataView.filter((event.target as HTMLInputElement).value);
  }
}
