import { Component, computed, input, numberAttribute, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getFlakyTestsOverviewOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconAlertTriangle, IconBug, IconSearch } from 'angular-tabler-icons/icons';

type SeverityFilter = 'all' | 'high' | 'medium' | 'low';

@Component({
  selector: 'app-flaky-tests-overview',
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    TagModule,
    TooltipModule,
    InputTextModule,
    ButtonModule,
    SkeletonModule,
    PaginatorModule,
    PageHeadingComponent,
    TablerIconComponent,
  ],
  providers: [
    provideTablerIcons({
      IconBug,
      IconAlertTriangle,
      IconSearch,
    }),
  ],
  templateUrl: './flaky-tests-overview.component.html',
})
export class FlakyTestsOverviewComponent {
  repositoryId = input.required({ transform: numberAttribute });

  currentPage = signal(0);
  pageSize = signal(10);
  searchTerm = signal('');
  severityFilter = signal<SeverityFilter>('all');

  private searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  debouncedSearch = signal('');

  flakyTestsQuery = injectQuery(() => getFlakyTestsOverviewOptions());

  filteredFlakyTests = computed(() => {
    const data = this.flakyTestsQuery.data()?.flakyTests ?? [];
    const search = this.debouncedSearch().toLowerCase();
    const severity = this.severityFilter();

    let filtered = data;
    if (search) {
      filtered = filtered.filter(t => t.testName.toLowerCase().includes(search) || t.className.toLowerCase().includes(search) || t.testSuiteName.toLowerCase().includes(search));
    }
    if (severity !== 'all') {
      filtered = filtered.filter(t => {
        const score = t.flakinessScore ?? 0;
        if (severity === 'high') return score > 70;
        if (severity === 'medium') return score > 30 && score <= 70;
        return score <= 30;
      });
    }
    return filtered;
  });

  paginatedFlakyTests = computed(() => {
    const filtered = this.filteredFlakyTests();
    const start = this.currentPage() * this.pageSize();
    return filtered.slice(start, start + this.pageSize());
  });

  totalRecords = computed(() => this.filteredFlakyTests().length);

  onSearchChange(value: string) {
    this.searchTerm.set(value);
    if (this.searchDebounceTimer) {
      clearTimeout(this.searchDebounceTimer);
    }
    this.searchDebounceTimer = setTimeout(() => {
      this.debouncedSearch.set(value);
      this.currentPage.set(0);
    }, 300);
  }

  onPageChange(event: PaginatorState) {
    this.currentPage.set(event.page ?? 0);
    this.pageSize.set(event.rows ?? 10);
  }

  setSeverityFilter(filter: SeverityFilter) {
    this.severityFilter.set(filter);
    this.currentPage.set(0);
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
