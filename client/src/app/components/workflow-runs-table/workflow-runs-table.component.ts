import { Component, computed, effect, inject, ViewChild } from '@angular/core';
import { Table, TableModule, TablePageEvent } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { SelectModule } from 'primeng/select';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { WorkflowRunDto } from '@app/core/modules/openapi';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getWorkflowRunsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PAGINATED_FILTER_OPTIONS_TOKEN, PaginatedFilterOption, PaginatedTableService } from '@app/core/services/paginated-table.service';
import { TableFilterPaginatedComponent } from '@app/components/table-filter-paginated/table-filter-paginated.component';
import { MessageService, SortMeta } from 'primeng/api';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconAlertTriangle, IconBrandGithub, IconCircleCheck, IconCircleX, IconClockHour4, IconFilterPlus, IconGitPullRequest, IconProgress } from 'angular-tabler-icons/icons';

export function createWorkflowRunsFilterOptions(): PaginatedFilterOption[] {
  return [
    { name: 'All runs', value: 'ALL' },
    { name: 'Not started', value: 'NOT_STARTED' },
    { name: 'In progress', value: 'IN_PROGRESS' },
    { name: 'Succeeded', value: 'SUCCESS' },
    { name: 'Failed', value: 'FAILURE' },
    { name: 'Cancelled', value: 'CANCELLED' },
    { name: 'Action required', value: 'ACTION_REQUIRED' },
  ];
}

@Component({
  selector: 'app-workflow-runs-table',
  standalone: true,
  imports: [TableModule, TagModule, TimeAgoPipe, SelectModule, TablerIconComponent, SkeletonModule, TooltipModule, ButtonModule, DividerModule, TableFilterPaginatedComponent],
  providers: [
    PaginatedTableService,
    MessageService,
    { provide: PAGINATED_FILTER_OPTIONS_TOKEN, useFactory: createWorkflowRunsFilterOptions },
    provideTablerIcons({
      IconFilterPlus,
      IconGitPullRequest,
      IconCircleCheck,
      IconCircleX,
      IconProgress,
      IconClockHour4,
      IconAlertTriangle,
      IconBrandGithub,
    }),
  ],
  templateUrl: './workflow-runs-table.component.html',
})
export class WorkflowRunsTableComponent {
  @ViewChild('table') table!: Table;
  @ViewChild(TableFilterPaginatedComponent) filterComponent!: TableFilterPaginatedComponent;

  messageService = inject(MessageService);
  paginationService = inject(PaginatedTableService);

  queryOptions = computed(() => {
    const paginationState = this.paginationService.paginationState();
    const filterType = paginationState.filterType as 'ALL' | 'NOT_STARTED' | 'IN_PROGRESS' | 'CANCELLED' | 'SUCCESS' | 'FAILURE' | 'ACTION_REQUIRED' | undefined;

    return getWorkflowRunsOptions({
      query: {
        page: paginationState.page,
        size: paginationState.size,
        sortField: paginationState.sortField,
        sortDirection: paginationState.sortDirection,
        filterType: filterType,
        searchTerm: paginationState.searchTerm,
      },
    });
  });

  query = injectQuery(() => this.queryOptions());

  constructor() {
    effect(() => {
      this.paginationService.paginationState();
      if (this.query.data()) {
        this.query.refetch();
      }
    });
  }

  get typedPaginationService() {
    return this.paginationService as PaginatedTableService;
  }

  runs(): WorkflowRunDto[] {
    // Backend returns a paginated response; keep type-safe access here.
    // We intentionally type cast to `any` to avoid duplicating the generated type.
    const data = this.query.data() as { runs?: WorkflowRunDto[] } | undefined;
    return data?.runs ?? [];
  }

  totalElements(): number {
    const data = this.query.data() as { totalElements?: number } | undefined;
    return data?.totalElements ?? 0;
  }

  onPage(event: TablePageEvent) {
    this.paginationService.onPage(event);
    this.query.refetch();
  }

  onSort(event: SortMeta) {
    this.paginationService.onSort(event);
  }

  clearFilters() {
    this.filterComponent.clearSearch();
    this.paginationService.clearFilters();
  }

  statusSeverity(run: WorkflowRunDto): 'success' | 'danger' | 'info' | 'warn' | 'secondary' {
    if (run.conclusion === 'SUCCESS') return 'success';
    if (run.conclusion === 'FAILURE' || run.conclusion === 'STARTUP_FAILURE' || run.conclusion === 'TIMED_OUT') {
      return 'danger';
    }
    if (run.conclusion === 'CANCELLED') return 'secondary';
    if (run.status === 'IN_PROGRESS') return 'info';
    if (run.status === 'QUEUED' || run.status === 'WAITING' || run.status === 'PENDING' || run.status === 'REQUESTED') {
      return 'warn';
    }
    return 'secondary';
  }

  openRunExternal(event: Event, run: WorkflowRunDto) {
    window.open(run.htmlUrl, '_blank');
    event.stopPropagation();
  }

  getWorkflowStatusIcon(run: WorkflowRunDto): string {
    if (run.conclusion === 'SUCCESS') {
      return 'circle-check';
    }
    if (run.conclusion === 'FAILURE' || run.conclusion === 'STARTUP_FAILURE' || run.conclusion === 'TIMED_OUT') {
      return 'circle-x';
    }
    if (run.conclusion === 'CANCELLED') {
      return 'circle-x';
    }
    if (run.status === 'IN_PROGRESS') {
      return 'progress';
    }
    if (run.status === 'QUEUED' || run.status === 'WAITING' || run.status === 'PENDING' || run.status === 'REQUESTED') {
      return 'clock-hour-4';
    }
    if (run.status === 'ACTION_REQUIRED' || run.conclusion === 'ACTION_REQUIRED') {
      return 'alert-triangle';
    }
    return 'circle-x';
  }

  getWorkflowStatusClass(run: WorkflowRunDto): string {
    if (run.conclusion === 'SUCCESS') {
      return 'text-green-500';
    }
    if (run.conclusion === 'FAILURE' || run.conclusion === 'STARTUP_FAILURE' || run.conclusion === 'TIMED_OUT') {
      return 'text-red-500';
    }
    if (run.conclusion === 'CANCELLED') {
      return 'text-surface-500';
    }
    if (run.status === 'IN_PROGRESS') {
      return 'text-blue-500 animate-spin';
    }
    if (run.status === 'QUEUED' || run.status === 'WAITING' || run.status === 'PENDING' || run.status === 'REQUESTED') {
      return 'text-amber-500';
    }
    if (run.status === 'ACTION_REQUIRED' || run.conclusion === 'ACTION_REQUIRED') {
      return 'text-orange-500';
    }
    return 'text-surface-500';
  }

  getTestStatusIcon(run: WorkflowRunDto): string {
    if (run.testProcessingStatus === 'PROCESSED') {
      return 'circle-check';
    }
    if (run.testProcessingStatus === 'FAILED') {
      return 'alert-triangle';
    }
    if (run.testProcessingStatus === 'PROCESSING') {
      return 'progress';
    }
    return 'circle-check';
  }

  getTestStatusClass(run: WorkflowRunDto): string {
    if (run.testProcessingStatus === 'PROCESSED') {
      return 'text-green-500';
    }
    if (run.testProcessingStatus === 'FAILED') {
      return 'text-red-500';
    }
    if (run.testProcessingStatus === 'PROCESSING') {
      return 'text-blue-500 animate-spin';
    }
    return 'text-surface-500';
  }
}
