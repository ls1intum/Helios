import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { TooltipModule } from 'primeng/tooltip';
import { Component, computed, effect, inject, ViewChild } from '@angular/core';
import { Table, TableModule, TablePageEvent } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { ActivatedRoute, Router } from '@angular/router';
import { getPaginatedPullRequestsOptions, getPaginatedPullRequestsQueryKey, setPrPinnedByNumberMutation } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PullRequestInfoDto } from '@app/core/modules/openapi';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { SelectModule } from 'primeng/select';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { FormsModule } from '@angular/forms';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { WorkflowRunStatusComponent } from '@app/components/workflow-run-status-component/workflow-run-status.component';
import { PullRequestStatusIconComponent } from '@app/components/pull-request-status-icon/pull-request-status-icon.component';
import { MessageService, SortMeta } from 'primeng/api';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconExternalLink, IconFilterPlus, IconGitPullRequest, IconPinned, IconPinnedOff, IconPoint, IconBrandGithub } from 'angular-tabler-icons/icons';
import { PAGINATED_FILTER_OPTIONS_TOKEN, PaginatedTableService } from '@app/core/services/paginated-table.service';
import { TableFilterPaginatedComponent } from '@app/components/table-filter-paginated/table-filter-paginated.component';
import { NgTemplateOutlet } from '@angular/common';

// Define filter options for pull requests
const PR_FILTER_OPTIONS = [
  { name: 'All pull requests', value: 'ALL' },
  { name: 'Open pull requests', value: 'OPEN' },
  { name: 'Open and ready for review', value: 'OPEN_READY_FOR_REVIEW' },
  { name: 'Draft pull requests', value: 'DRAFT' },
  { name: 'Merged pull requests', value: 'MERGED' },
  { name: 'Closed pull requests', value: 'CLOSED' },
  { name: 'Your pull requests', value: 'USER_AUTHORED' },
  { name: 'Everything assigned to you', value: 'ASSIGNED_TO_USER' },
  { name: 'Everything that requests a review by you', value: 'REVIEW_REQUESTED' },
];

@Component({
  selector: 'app-pull-request-table',
  imports: [
    TableModule,
    AvatarModule,
    TagModule,
    TimeAgoPipe,
    FormsModule,
    SelectModule,
    TablerIconComponent,
    SkeletonModule,
    AvatarGroupModule,
    TooltipModule,
    MarkdownPipe,
    ButtonModule,
    DividerModule,
    WorkflowRunStatusComponent,
    PullRequestStatusIconComponent,
    TableFilterPaginatedComponent,
    NgTemplateOutlet,
  ],
  providers: [
    PaginatedTableService,
    { provide: PAGINATED_FILTER_OPTIONS_TOKEN, useValue: PR_FILTER_OPTIONS },
    provideTablerIcons({
      IconFilterPlus,
      IconPoint,
      IconExternalLink,
      IconPinnedOff,
      IconPinned,
      IconGitPullRequest,
      IconBrandGithub,
    }),
  ],
  templateUrl: './pull-request-table.component.html',
})
export class PullRequestTableComponent {
  @ViewChild('table') table!: Table;
  @ViewChild(TableFilterPaginatedComponent) filterComponent!: TableFilterPaginatedComponent;

  messageService = inject(MessageService);
  queryClient = inject(QueryClient);
  router = inject(Router);
  route = inject(ActivatedRoute);
  keycloak = inject(KeycloakService);
  paginationService = inject(PaginatedTableService);

  // Create a computed query options that will update when pagination state changes
  queryOptions = computed(() => {
    const paginationState = this.paginationService.paginationState();

    // Convert the string filterType to the specific union type
    const filterType = paginationState.filterType as 'ALL' | 'OPEN' | 'USER_AUTHORED' | 'ASSIGNED_TO_USER' | 'REVIEW_REQUESTED' | undefined;

    return getPaginatedPullRequestsOptions({
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

  setPinnedMutation = injectMutation(() => ({
    ...setPrPinnedByNumberMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Pin Pull Request', detail: 'The pull request was pinned successfully' });
      this.queryClient.invalidateQueries({ queryKey: getPaginatedPullRequestsQueryKey() });
    },
  }));

  isHovered = new Map<number, boolean>();

  constructor() {
    // Re-fetch data when pagination state changes
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

  openPRExternal(event: Event, pr: PullRequestInfoDto): void {
    window.open(pr.htmlUrl, '_blank');
    event.stopPropagation();
  }

  // TODO: Find a better way to handle color of labels
  getLabelClasses(color: string) {
    // Color code 'ededed' is used for labels with no color
    // In this case, we don't want to transparency effect
    // If we add transparency, then it's hard to read the text
    // Also text color of black is also for better readability
    return {
      'border-color': `#${color}`,
      'background-color': color === 'ededed' ? `#${color}` : `#${color}75`,
    };
  }

  getAvatarBorderClass(login: string) {
    return this.keycloak.isCurrentUser(login) ? 'border-2 border-primary-400 rounded-full' : '';
  }

  openPR(pr: PullRequestInfoDto): void {
    this.router.navigate([pr.number], {
      relativeTo: this.route.parent,
    });
  }

  setPinned(event: Event, pr: PullRequestInfoDto, isPinned: boolean): void {
    this.setPinnedMutation.mutate({ path: { pr: pr.id }, query: { isPinned } });
    this.isHovered.set(pr.id, false);
    event.stopPropagation();
  }

  onPage(event: TablePageEvent) {
    console.log('Page event received:', event);

    // Update the pagination state
    this.paginationService.onPage(event);

    // Force a refetch with the updated pagination parameters
    this.query.refetch();
  }

  onSort(event: SortMeta) {
    this.paginationService.onSort(event);
  }

  clearFilters() {
    this.filterComponent.clearSearch();
    this.paginationService.clearFilters();
  }
}
