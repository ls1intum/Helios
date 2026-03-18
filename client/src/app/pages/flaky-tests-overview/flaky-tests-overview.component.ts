import { Component, computed, inject, input, numberAttribute, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Table, TableModule, TablePageEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getFlakyTestsOverviewOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconAlertTriangle, IconBug } from 'angular-tabler-icons/icons';
import { PAGINATED_FILTER_OPTIONS_TOKEN, PaginatedFilterOption, PaginatedTableService } from '@app/core/services/paginated-table.service';
import { TableFilterPaginatedComponent } from '@app/components/table-filter-paginated/table-filter-paginated.component';
import { SortMeta } from 'primeng/api';
import { FlakyTestDto } from '@app/core/modules/openapi';

function createFlakinessFilterOptions(): PaginatedFilterOption[] {
  return [
    { name: 'All', value: 'ALL' },
    { name: 'High (> 70)', value: 'HIGH' },
    { name: 'Medium (30 – 70)', value: 'MEDIUM' },
    { name: 'Low (1 – 30)', value: 'LOW' },
  ];
}

@Component({
  selector: 'app-flaky-tests-overview',
  imports: [CommonModule, TableModule, TagModule, TooltipModule, ButtonModule, SkeletonModule, PageHeadingComponent, TablerIconComponent, TableFilterPaginatedComponent],
  providers: [PaginatedTableService, { provide: PAGINATED_FILTER_OPTIONS_TOKEN, useFactory: createFlakinessFilterOptions }, provideTablerIcons({ IconBug, IconAlertTriangle })],
  templateUrl: './flaky-tests-overview.component.html',
})
export class FlakyTestsOverviewComponent {
  @ViewChild('table') table!: Table;
  @ViewChild(TableFilterPaginatedComponent) filterComponent!: TableFilterPaginatedComponent;

  repositoryId = input.required({ transform: numberAttribute });

  paginationService = inject(PaginatedTableService);

  queryOptions = computed(() => {
    const state = this.paginationService.paginationState();
    const filterType = state.filterType as 'ALL' | 'HIGH' | 'MEDIUM' | 'LOW' | undefined;
    return getFlakyTestsOverviewOptions({
      query: {
        page: state.page,
        size: state.size,
        sortDirection: state.sortDirection,
        filterType,
        searchTerm: state.searchTerm,
      },
    });
  });

  query = injectQuery(() => this.queryOptions());

  get typedPaginationService() {
    return this.paginationService as PaginatedTableService;
  }

  flakyTests(): FlakyTestDto[] {
    return this.query.data()?.flakyTests ?? [];
  }

  totalElements(): number {
    return this.query.data()?.filteredCount ?? 0;
  }

  onPage(event: TablePageEvent) {
    this.paginationService.onPage(event);
  }

  onSort(event: SortMeta) {
    this.paginationService.onSort(event);
  }

  clearFilters() {
    this.filterComponent.clearSearch();
    this.paginationService.clearFilters();
  }

  getSeverityTag(score: number): { label: string; severity: 'danger' | 'warn' | 'info' } {
    if (score > 70) return { label: 'High', severity: 'danger' };
    if (score > 30) return { label: 'Medium', severity: 'warn' };
    return { label: 'Low', severity: 'info' };
  }

  formatScore(score: number): string {
    return score.toFixed(1);
  }

  formatRate(rate: number): string {
    return (rate * 100).toFixed(1) + '%';
  }
}
