import { Component, computed, inject } from '@angular/core';
import { getAllReleaseInfosOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { FILTER_OPTIONS_TOKEN, SearchTableService } from '@app/core/services/search-table.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TableFilterComponent } from '../table-filter/table-filter.component';
import { TagModule } from 'primeng/tag';
import { ReleaseInfoListDto } from '@app/core/modules/openapi';
import { Router, RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconGitBranch, IconGitCommit, IconPlus, IconTag } from 'angular-tabler-icons/icons';

const FILTER_OPTIONS: { name: string; filter: (prs: ReleaseInfoListDto[]) => ReleaseInfoListDto[] }[] = [];

@Component({
  selector: 'app-release-candidate-table',
  imports: [TableModule, ButtonModule, TablerIconComponent, SkeletonModule, TableFilterComponent, TagModule, RouterLink, SlicePipe],
  providers: [
    SearchTableService,
    { provide: FILTER_OPTIONS_TOKEN, useValue: FILTER_OPTIONS },
    provideTablerIcons({
      IconPlus,
      IconGitCommit,
      IconGitBranch,
      IconTag,
    }),
  ],
  templateUrl: './release-candidate-table.component.html',
})
export class ReleaseCandidateTableComponent {
  releaseCandidatesQuery = injectQuery(() => getAllReleaseInfosOptions());
  router = inject(Router);
  searchTableService = inject(SearchTableService);

  filteredReleaseCandidates = computed(() => this.releaseCandidatesQuery.data() || []);
}
