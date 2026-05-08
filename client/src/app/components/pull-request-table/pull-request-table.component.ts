import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { TooltipModule } from 'primeng/tooltip';
import { Component, computed, effect, inject, input, numberAttribute, signal, viewChild } from '@angular/core';
import { TableModule, TablePageEvent } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { ActivatedRoute, Router } from '@angular/router';
import {
  getPullRequestFilterOptionsByRepositoryIdOptions,
  getPullRequestsOptions,
  getPullRequestsQueryKey,
  setPrPinnedByNumberMutation,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PullRequestBaseInfoDto } from '@app/core/modules/openapi';
import { ButtonModule } from 'primeng/button';
import { ChipModule } from 'primeng/chip';
import { DividerModule } from 'primeng/divider';
import { SelectModule } from 'primeng/select';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { FormsModule } from '@angular/forms';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { WorkflowRunStatusComponent } from '@app/components/workflow-run-status-component/workflow-run-status.component';
import { PullRequestStatusIconComponent } from '@app/components/pull-request-status-icon/pull-request-status-icon.component';
import { MessageService, SortMeta } from 'primeng/api';
import { GithubLinkButtonComponent } from '@app/components/github-link-button/github-link-button.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconExternalLink, IconFilterPlus, IconGitPullRequest, IconPinned, IconPinnedOff, IconPoint } from 'angular-tabler-icons/icons';
import { PAGINATED_FILTER_OPTIONS_TOKEN, PAGINATION_STORAGE_KEY_TOKEN, PaginatedFilterOption, PaginatedTableService } from '@app/core/services/paginated-table.service';
import { NgTemplateOutlet } from '@angular/common';
import { PullRequestFilterBarComponent, PullRequestQueryFilters } from '@app/components/pull-request-filter-bar/pull-request-filter-bar.component';

// Define filter options for pull requests
export function createPullRequestFilterOptions(keycloakService: KeycloakService): PaginatedFilterOption[] {
  const isLoggedIn = keycloakService.isLoggedIn();

  const baseOptions: PaginatedFilterOption[] = [
    { name: 'Open pull requests', value: 'OPEN' },
    { name: 'All pull requests', value: 'ALL' },
    { name: 'Open and ready for review', value: 'OPEN_READY_FOR_REVIEW' },
    { name: 'Draft pull requests', value: 'DRAFT' },
    { name: 'Merged pull requests', value: 'MERGED' },
    { name: 'Closed pull requests', value: 'CLOSED' },
  ];

  const userOptions: PaginatedFilterOption[] = [
    { name: 'Your pull requests', value: 'USER_AUTHORED' },
    { name: 'Everything assigned to you', value: 'ASSIGNED_TO_USER' },
    { name: 'Everything that requests a review by you', value: 'REVIEW_REQUESTED' },
  ];

  return isLoggedIn ? [...baseOptions, ...userOptions] : baseOptions;
}

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
    ChipModule,
    DividerModule,
    WorkflowRunStatusComponent,
    PullRequestStatusIconComponent,
    PullRequestFilterBarComponent,
    NgTemplateOutlet,
    GithubLinkButtonComponent,
  ],
  providers: [
    PaginatedTableService,
    { provide: PAGINATED_FILTER_OPTIONS_TOKEN, useFactory: createPullRequestFilterOptions, deps: [KeycloakService] },
    { provide: PAGINATION_STORAGE_KEY_TOKEN, useValue: 'pullRequestPaginationState' },
    provideTablerIcons({
      IconFilterPlus,
      IconPoint,
      IconExternalLink,
      IconPinnedOff,
      IconPinned,
      IconGitPullRequest,
    }),
  ],
  templateUrl: './pull-request-table.component.html',
})
export class PullRequestTableComponent {
  filterBar = viewChild.required<PullRequestFilterBarComponent>('filterBar');

  messageService = inject(MessageService);
  queryClient = inject(QueryClient);
  router = inject(Router);
  route = inject(ActivatedRoute);
  keycloak = inject(KeycloakService);
  paginationService = inject(PaginatedTableService);
  repositoryId = input.required({ transform: numberAttribute });
  queryFilters = signal<PullRequestQueryFilters>({
    author: null,
    assignee: null,
    noAssignee: false,
    labelId: null,
    noLabel: false,
    reviewState: null,
    requestedReviewer: null,
  });

  pullRequestQueryState = computed((): NonNullable<Parameters<typeof getPullRequestsOptions>[0]>['query'] => {
    const paginationState = this.paginationService.paginationState();
    const queryFilters = this.queryFilters();
    const effectiveAssignee = queryFilters.noAssignee ? null : queryFilters.assignee;
    const effectiveLabelId = queryFilters.noLabel ? null : queryFilters.labelId;
    const effectiveRequestedReviewer = queryFilters.reviewState === 'NONE' ? null : queryFilters.requestedReviewer;

    // Convert the string filterType to the specific union type
    const filterType = paginationState.filterType as
      | 'OPEN'
      | 'ALL'
      | 'OPEN_READY_FOR_REVIEW'
      | 'DRAFT'
      | 'MERGED'
      | 'CLOSED'
      | 'USER_AUTHORED'
      | 'ASSIGNED_TO_USER'
      | 'REVIEW_REQUESTED'
      | undefined;

    return {
      page: paginationState.page,
      size: paginationState.size,
      sortField: paginationState.sortField,
      sortDirection: paginationState.sortDirection,
      repositoryId: this.repositoryId(),
      filterType: filterType,
      searchTerm: paginationState.searchTerm,
      author: queryFilters.author ?? undefined,
      assignee: effectiveAssignee ?? undefined,
      noAssignee: queryFilters.noAssignee || undefined,
      labelId: effectiveLabelId ?? undefined,
      noLabel: queryFilters.noLabel || undefined,
      reviewState: queryFilters.reviewState ?? undefined,
      requestedReviewer: effectiveRequestedReviewer ?? undefined,
    } as unknown as NonNullable<Parameters<typeof getPullRequestsOptions>[0]>['query'];
  });

  // Create a computed query options that will update when pagination state changes
  queryOptions = computed(() => {
    return getPullRequestsOptions({
      query: this.pullRequestQueryState(),
    });
  });

  query = injectQuery(() => this.queryOptions());
  dropdownOptionsQuery = injectQuery(() =>
    getPullRequestFilterOptionsByRepositoryIdOptions({
      path: { repoId: this.repositoryId() },
    })
  );

  setPinnedMutation = injectMutation(() => ({
    ...setPrPinnedByNumberMutation(),
  }));

  isHovered = new Map<number, boolean>();
  visiblePullRequests = computed(() => {
    const data = this.query.data();
    return [...(data?.pinned ?? []), ...(data?.page ?? [])];
  });
  private retriedStuckQuery = false;

  constructor() {
    if (!this.paginationService.sortField()) {
      this.paginationService.setSort('updatedAt', 'desc');
    }

    // Re-fetch data when pagination state changes
    effect(() => {
      const isPending = this.query.isPending();
      const isFetching = this.query.isFetching();

      if (!this.retriedStuckQuery && isPending && !isFetching) {
        this.retriedStuckQuery = true;
        this.query.refetch();
      }

      if (!isPending) {
        this.retriedStuckQuery = false;
      }
    });
  }

  get typedPaginationService() {
    return this.paginationService as PaginatedTableService;
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
      ...(color === 'ededed' && { color: '#000000' }),
    };
  }

  getAvatarBorderClass(login: string | undefined): string {
    if (!login) return '';
    return this.keycloak.isCurrentUser(login) ? 'border-2 border-primary-400 rounded-full' : '';
  }

  openPR(pr: PullRequestBaseInfoDto): void {
    this.router.navigate([pr.number], {
      relativeTo: this.route.parent,
    });
  }

  setPinned(event: Event, pr: PullRequestBaseInfoDto, isPinned: boolean): void {
    event.stopPropagation();

    this.setPinnedMutation.mutate(
      {
        path: { pr: pr.id },
        query: { isPinned },
      },
      {
        onSuccess: () => {
          this.isHovered.set(pr.id, false);
          this.messageService.add({
            severity: 'success',
            summary: isPinned ? 'Pin Pull Request' : 'Unpin Pull Request',
            detail: `The pull request was ${isPinned ? 'pinned' : 'unpinned'} successfully`,
          });
          this.queryClient.invalidateQueries({ queryKey: getPullRequestsQueryKey() });
        },
      }
    );
  }

  onPage(event: TablePageEvent) {
    // Update the pagination state
    this.paginationService.onPage(event);
  }

  onSort(event: SortMeta) {
    this.paginationService.onSort(event);
  }

  onQueryFiltersChange(filters: PullRequestQueryFilters): void {
    this.queryFilters.set(filters);
    this.paginationService.resetToFirstPage();
  }

  clearFilters() {
    this.filterBar().clearSearch();
    this.filterBar().clearQueryFilters();
    this.paginationService.clearFilters();
  }
}
