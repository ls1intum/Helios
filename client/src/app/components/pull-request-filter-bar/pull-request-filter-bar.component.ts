import { computed, Component, DestroyRef, effect, inject, Injector, input, OnInit, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { PullRequestFilterLabelOptionDto, PullRequestFilterOptionsDto, PullRequestFilterUserOptionDto } from '@app/core/modules/openapi';
import { PaginatedTableService } from '@app/core/services/paginated-table.service';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconArrowsSort, IconChevronDown, IconEye, IconFilter, IconTag, IconUser, IconUserCheck, IconX } from 'angular-tabler-icons/icons';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { ChipModule } from 'primeng/chip';
import { InputTextModule } from 'primeng/inputtext';
import { Popover, PopoverModule } from 'primeng/popover';
import { AvatarModule } from 'primeng/avatar';

type MenuOption = {
  label: string;
  value: string;
};

type ActiveFilterKey = 'status' | 'author' | 'assignee' | 'no-assignee' | 'label' | 'no-label' | 'review' | 'review-requested' | 'sort';
type ActiveFilterToken = { key: ActiveFilterKey; label: string };

export type PullRequestQueryFilters = {
  author: string | null;
  assignee: string | null;
  noAssignee: boolean;
  labelId: number | null;
  noLabel: boolean;
  reviewState: 'NONE' | 'REQUIRED' | null;
  requestedReviewer: string | null;
};

@Component({
  selector: 'app-pull-request-filter-bar',
  imports: [ButtonModule, PopoverModule, InputTextModule, FormsModule, TablerIconComponent, ChipModule, AvatarModule],
  providers: [
    provideTablerIcons({
      IconChevronDown,
      IconX,
      IconFilter,
      IconUser,
      IconTag,
      IconUserCheck,
      IconEye,
      IconArrowsSort,
    }),
  ],
  templateUrl: './pull-request-filter-bar.component.html',
})
export class PullRequestFilterBarComponent implements OnInit {
  private destroyRef = inject(DestroyRef);
  private injector = inject(Injector);

  /** Inputs/outputs for parent-driven table state and query-filter emission. */
  repositoryId = input.required<number>();
  paginationService = input.required<PaginatedTableService>();
  filterOptions = input<PullRequestFilterOptionsDto | null>(null);
  showChips = input(true);
  queryFiltersChange = output<PullRequestQueryFilters>();

  /** Local text bound to the top search box input. */
  localSearchTerm = '';

  /** Raw selected filter values */
  selectedAuthorLogin = signal<string | null>(null);
  selectedAssigneeLogin = signal<string | null>(null);
  noAssigneeFilter = signal(false);
  selectedLabelId = signal<number | null>(null);
  noLabelFilter = signal(false);
  selectedReviewStateValue = signal<string | null>(null);
  selectedRequestedReviewerLogin = signal<string | null>(null);

  /** Search text signals for filter popover option lists. */
  authorSearchText = signal('');
  assigneeSearchText = signal('');
  reviewRequestedSearchText = signal('');
  labelSearchText = signal('');

  /** Internal state used for debounced search and repository change detection. */
  private searchTermSubject = new Subject<string>();
  private lastRepositoryId: number | null = null;

  /** Mapping of status filter values to chip/query-token labels. */
  readonly statusFilterTokenMap: Record<string, string> = {
    OPEN: 'is:open',
    CLOSED: 'is:closed',
    MERGED: 'is:merged',
    DRAFT: 'is:draft',
    OPEN_READY_FOR_REVIEW: 'is:open review:required',
    USER_AUTHORED: 'author:@me',
    ASSIGNED_TO_USER: 'assignee:@me',
    REVIEW_REQUESTED: 'user-review-requested:@me',
    ALL: 'is:pr',
  };

  /** Review state filter options */
  readonly reviewStateFilters: MenuOption[] = [
    { label: 'No review requested', value: 'review:none' },
    { label: 'Review required', value: 'review:required' },
  ];

  /** Sort options with UI tokens encoding sort field/direction for selection logic. */
  readonly sortFilters: MenuOption[] = [
    { label: 'Recently updated', value: 'sort:updated-desc' },
    { label: 'Least recently updated', value: 'sort:updated-asc' },
    { label: 'Newest created', value: 'sort:created-desc' },
    { label: 'Oldest created', value: 'sort:created-asc' },
  ];

  /** All available options for each filter type from the backend */
  readonly allAuthorOptions = computed(() => this.filterOptions()?.authors ?? []);
  readonly allAssigneeOptions = computed(() => this.filterOptions()?.assignees ?? []);
  readonly allReviewerOptions = computed(() => this.filterOptions()?.reviewers ?? []);
  readonly allLabelOptions = computed(() => this.filterOptions()?.labels ?? []);

  /** Search-filtered options shown in each popover list. */
  readonly authorOptions = computed(() => this.filterUsers(this.allAuthorOptions(), this.authorSearchText()));
  readonly assigneeOptions = computed(() => this.filterUsers(this.allAssigneeOptions(), this.assigneeSearchText()));
  readonly reviewerOptions = computed(() => this.filterUsers(this.allReviewerOptions(), this.reviewRequestedSearchText()));
  readonly labelOptions = computed(() => this.filterLabels(this.allLabelOptions(), this.labelSearchText()));

  /** Selected option objects resolved from raw selected values */
  readonly selectedAuthor = computed(() => this.allAuthorOptions().find(p => p.login === this.selectedAuthorLogin()) ?? null);
  readonly selectedAssignee = computed(() => this.allAssigneeOptions().find(p => p.login === this.selectedAssigneeLogin()) ?? null);
  readonly selectedLabel = computed(() => this.allLabelOptions().find(l => l.id === this.selectedLabelId()) ?? null);
  readonly selectedReviewToken = computed(() => this.reviewStateFilters.find(r => r.value === this.selectedReviewStateValue()) ?? null);
  readonly selectedRequestedReviewer = computed(() => this.allReviewerOptions().find(p => p.login === this.selectedRequestedReviewerLogin()) ?? null);
  readonly selectedSort = computed(() => {
    const sortToken = this.getSortToken(this.paginationService().sortField(), this.paginationService().sortDirection());
    return this.sortFilters.find(s => s.value === sortToken) ?? null;
  });

  /** True when any of the review-related sub-filters is active */
  readonly isReviewFilterActive = computed(() => !!this.selectedReviewStateValue() || !!this.selectedRequestedReviewerLogin());
  readonly isReviewNoneSelected = computed(() => this.selectedReviewStateValue() === 'review:none');

  /** True when the assignee filter has any value (specific person OR no-assignee) */
  readonly isAssigneeFilterActive = computed(() => this.noAssigneeFilter() || !!this.selectedAssigneeLogin());

  /** True when the label filter has any value (specific label OR no-label) */
  readonly isLabelFilterActive = computed(() => this.noLabelFilter() || !!this.selectedLabelId());

  /** Active status filter token derived from the active pagination filter type and mapped to a user-friendly label. */
  readonly activeStatusFilterToken = computed(() => {
    const filterType = this.paginationService().filterType();
    const isDefaultFilter = filterType === this.paginationService().filterOptions[0]?.value;
    if (isDefaultFilter) return null;
    return this.statusFilterTokenMap[filterType] ?? this.paginationService().activeFilter()?.name ?? null;
  });

  /** All active filters shown in the UI with remove-key metadata. */
  readonly activeFilterTokens = computed(() => {
    const filterTokens: ActiveFilterToken[] = [];
    const statusToken = this.activeStatusFilterToken();
    const authorLogin = this.selectedAuthorLogin();
    const assigneeLogin = this.selectedAssigneeLogin();
    const noAssignee = this.noAssigneeFilter();
    const noLabel = this.noLabelFilter();
    const selectedLabel = this.selectedLabel();
    const selectedReviewToken = this.selectedReviewToken();
    const requestedReviewerLogin = this.selectedRequestedReviewerLogin();
    const selectedSort = this.selectedSort();

    if (statusToken) {
      filterTokens.push({ key: 'status', label: statusToken });
    }
    if (authorLogin) {
      filterTokens.push({ key: 'author', label: `author:${authorLogin}` });
    }
    if (noAssignee) {
      filterTokens.push({ key: 'no-assignee', label: 'no:assignee' });
    } else if (assigneeLogin) {
      filterTokens.push({ key: 'assignee', label: `assignee:${assigneeLogin}` });
    }
    if (noLabel) {
      filterTokens.push({ key: 'no-label', label: 'no:label' });
    } else if (selectedLabel) {
      filterTokens.push({ key: 'label', label: `label:${selectedLabel.name}` });
    }
    if (selectedReviewToken) {
      filterTokens.push({ key: 'review', label: selectedReviewToken.label });
    }
    if (requestedReviewerLogin) {
      filterTokens.push({ key: 'review-requested', label: `review-requested:${requestedReviewerLogin}` });
    }
    if (selectedSort && selectedSort.value !== 'sort:updated-desc') {
      filterTokens.push({ key: 'sort', label: selectedSort.label });
    }

    return filterTokens;
  });

  /** Wires repository/filter-options synchronization and debounced search propagation to the table service. */
  ngOnInit() {
    effect(
      () => {
        const repositoryId = this.repositoryId();
        untracked(() => {
          if (this.lastRepositoryId !== null && this.lastRepositoryId !== repositoryId) {
            this.resetTransientFiltersForRepositoryChange();
          }
          this.lastRepositoryId = repositoryId;
        });
      },
      { injector: this.injector }
    );

    this.searchTermSubject
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(searchValue => this.paginationService().setSearchTerm(searchValue));

    this.localSearchTerm = this.paginationService().searchTerm() || '';
  }

  /** Toggles any popover from template click events. */
  toggle(event: Event, popover: Popover) {
    popover.toggle(event);
  }

  /** Clears the local input, applies empty search immediately, and flushes pending debounced input. */
  clearSearch(): void {
    this.localSearchTerm = '';
    this.paginationService().setSearchTerm('');
    this.searchTermSubject.next('');
  }

  onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchTermSubject.next(value);
  }

  /** Returns the shared menu button class with active-state styling when a filter is applied. */
  menuButtonClass(active: boolean): string {
    const base =
      'h-7 px-2.5 inline-flex items-center gap-1.5 rounded border border-surface-300 dark:border-surface-600 bg-transparent hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors text-sm cursor-pointer text-surface-700 dark:text-surface-200';
    const selected = active ? 'border-primary-400 !text-primary-500 dark:!text-primary-400' : '';
    return `${base} ${selected}`.trim();
  }

  /** Derives the label for the filters menu button based on whether any status filter is active. */
  readonly filtersButtonLabel = computed(() => (this.paginationService().hasActiveFilter() ? (this.paginationService().activeFilter()?.name ?? 'Filters') : 'Filters'));

  selectStatus(filterValue: string, popover: Popover): void {
    this.paginationService().setFilterType(filterValue);
    popover.hide();
  }

  clearStatus(popover: Popover): void {
    this.clearStatusFilter();
    popover.hide();
  }

  selectAuthor(login: string | null, popover: Popover): void {
    const normalizedLogin = login?.trim() || null;
    this.selectedAuthorLogin.set(normalizedLogin);
    this.authorSearchText.set('');
    this.emitQueryFilters();
    popover.hide();
  }

  selectAssignee(login: string | null, popover: Popover): void {
    const normalizedLogin = login?.trim() || null;
    this.selectedAssigneeLogin.set(normalizedLogin);
    this.noAssigneeFilter.set(false);
    this.assigneeSearchText.set('');
    this.emitQueryFilters();
    popover.hide();
  }

  selectNoAssignee(popover: Popover): void {
    this.selectedAssigneeLogin.set(null);
    this.noAssigneeFilter.set(true);
    this.assigneeSearchText.set('');
    this.emitQueryFilters();
    popover.hide();
  }

  selectLabel(labelId: number | null, popover: Popover): void {
    this.selectedLabelId.set(labelId);
    this.noLabelFilter.set(false);
    this.labelSearchText.set('');
    this.emitQueryFilters();
    popover.hide();
  }

  selectNoLabel(popover: Popover): void {
    this.selectedLabelId.set(null);
    this.noLabelFilter.set(true);
    this.labelSearchText.set('');
    this.emitQueryFilters();
    popover.hide();
  }

  selectReview(value: string | null, popover: Popover): void {
    this.selectedReviewStateValue.set(value);
    if (value === 'review:none') {
      this.selectedRequestedReviewerLogin.set(null);
      this.reviewRequestedSearchText.set('');
    }
    this.emitQueryFilters();
    popover.hide();
  }

  selectReviewRequested(login: string | null, popover: Popover): void {
    const normalizedLogin = login?.trim() || null;
    this.selectedRequestedReviewerLogin.set(normalizedLogin);
    if (normalizedLogin) {
      this.selectedReviewStateValue.set('review:required');
    }
    this.reviewRequestedSearchText.set('');
    this.emitQueryFilters();
    popover.hide();
  }

  selectSort(value: string | null, popover: Popover): void {
    const sort = this.resolveSort(value);
    this.paginationService().setSort(sort.sortField, sort.sortDirection);
    popover.hide();
  }

  /** Removes a single active chip and updates the parent query filter state. */
  removeActiveFilter(key: ActiveFilterKey): void {
    switch (key) {
      case 'status':
        this.clearStatusFilter();
        break;
      case 'author':
        this.selectedAuthorLogin.set(null);
        break;
      case 'assignee':
        this.selectedAssigneeLogin.set(null);
        break;
      case 'no-assignee':
        this.noAssigneeFilter.set(false);
        break;
      case 'label':
        this.selectedLabelId.set(null);
        break;
      case 'no-label':
        this.noLabelFilter.set(false);
        break;
      case 'review':
        this.selectedReviewStateValue.set(null);
        break;
      case 'review-requested':
        this.selectedRequestedReviewerLogin.set(null);
        break;
      case 'sort':
        this.paginationService().setSort('updatedAt', 'desc');
        break;
    }
    this.emitQueryFilters();
  }

  /** Resets all transient query filters and returns sort/status to defaults. */
  clearQueryFilters(): void {
    this.clearStatusFilter();
    this.selectedAuthorLogin.set(null);
    this.selectedAssigneeLogin.set(null);
    this.noAssigneeFilter.set(false);
    this.selectedLabelId.set(null);
    this.noLabelFilter.set(false);
    this.selectedReviewStateValue.set(null);
    this.selectedRequestedReviewerLogin.set(null);
    this.paginationService().setSort('updatedAt', 'desc');
    this.authorSearchText.set('');
    this.assigneeSearchText.set('');
    this.reviewRequestedSearchText.set('');
    this.labelSearchText.set('');
    this.emitQueryFilters();
  }

  /** Resets the status filter to the first configured table filter option. */
  private clearStatusFilter(): void {
    const defaultFilter = this.paginationService().filterOptions[0];
    if (defaultFilter) {
      this.paginationService().setFilterType(defaultFilter.value);
    }
  }

  /** Emits the normalized query-filter payload consumed by the parent table component. */
  private emitQueryFilters(): void {
    const noAssignee = this.noAssigneeFilter();
    const noLabel = this.noLabelFilter();
    const reviewState = this.resolveReview(this.selectedReviewStateValue());
    const requestedReviewer = reviewState === 'NONE' ? null : this.selectedRequestedReviewerLogin();

    this.queryFiltersChange.emit({
      author: this.selectedAuthorLogin(),
      assignee: noAssignee ? null : this.selectedAssigneeLogin(),
      noAssignee: noAssignee,
      labelId: noLabel ? null : this.selectedLabelId(),
      noLabel: noLabel,
      reviewState: reviewState,
      requestedReviewer: requestedReviewer,
    });
  }

  /** Converts UI review tokens into backend query enum values. */
  private resolveReview(value: string | null): PullRequestQueryFilters['reviewState'] {
    switch (value) {
      case 'review:none':
        return 'NONE';
      case 'review:required':
        return 'REQUIRED';
      default:
        return null;
    }
  }

  /** Converts UI sort tokens into table-service sort field/direction values. */
  private resolveSort(value: string | null): { sortField: string | undefined; sortDirection: 'asc' | 'desc' } {
    switch (value) {
      case 'sort:updated-desc':
        return { sortField: 'updatedAt', sortDirection: 'desc' };
      case 'sort:updated-asc':
        return { sortField: 'updatedAt', sortDirection: 'asc' };
      case 'sort:created-desc':
        return { sortField: 'createdAt', sortDirection: 'desc' };
      case 'sort:created-asc':
        return { sortField: 'createdAt', sortDirection: 'asc' };
      default:
        return { sortField: undefined, sortDirection: 'desc' };
    }
  }

  /** Converts current sort state into a UI token used to highlight/select sort menu options. */
  private getSortToken(sortField: string | undefined, sortDirection: string | undefined): string | null {
    if (!sortField) {
      return null;
    }
    if (sortField === 'updatedAt' && sortDirection === 'asc') {
      return 'sort:updated-asc';
    }
    if (sortField === 'updatedAt' && sortDirection === 'desc') {
      return 'sort:updated-desc';
    }
    if (sortField === 'createdAt' && sortDirection === 'asc') {
      return 'sort:created-asc';
    }
    if (sortField === 'createdAt' && sortDirection === 'desc') {
      return 'sort:created-desc';
    }
    return null;
  }

  /** Clears transient UI state when the active repository changes. */
  private resetTransientFiltersForRepositoryChange(): void {
    this.clearSearch();
    this.clearQueryFilters();
  }

  /** Applies case-insensitive search against login and display name for user option lists. */
  private filterUsers(options: PullRequestFilterUserOptionDto[], searchText: string): PullRequestFilterUserOptionDto[] {
    const query = searchText.trim().toLowerCase();
    if (!query) {
      return options;
    }
    return options.filter(option => option.login.toLowerCase().includes(query) || option.name.toLowerCase().includes(query));
  }

  /** Applies case-insensitive search against label names. */
  private filterLabels(options: PullRequestFilterLabelOptionDto[], searchText: string): PullRequestFilterLabelOptionDto[] {
    const query = searchText.trim().toLowerCase();
    if (!query) {
      return options;
    }
    return options.filter(option => option.name.toLowerCase().includes(query));
  }
}
