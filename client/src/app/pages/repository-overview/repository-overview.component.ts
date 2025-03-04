import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { RepositoryInfoDto } from '@app/core/modules/openapi';
import { getAllRepositoriesOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule } from 'primeng/dataview';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { Skeleton } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TableFilterComponent } from '@app/components/table-filter/table-filter.component';
import { FILTER_OPTIONS_TOKEN, SearchTableService } from '@app/core/services/search-table.service';

const FILTER_OPTIONS = [{ name: 'All repositories', filter: (repos: RepositoryInfoDto[]) => repos }];

@Component({
  selector: 'app-repository-overview',
  imports: [DataViewModule, ButtonModule, TagModule, CardModule, ChipModule, IconsModule, PageHeadingComponent, ToastModule, Skeleton, TableModule, TableFilterComponent],
  providers: [SearchTableService, { provide: FILTER_OPTIONS_TOKEN, useValue: FILTER_OPTIONS }],
  templateUrl: './repository-overview.component.html',
})
export class RepositoryOverviewComponent {
  private router = inject(Router);
  searchTableService = inject(SearchTableService<RepositoryInfoDto>);

  query = injectQuery(() => getAllRepositoriesOptions());

  filteredRepositories = computed(() => this.searchTableService.activeFilter().filter(this.query.data() || []));

  navigateToProject(repository: RepositoryInfoDto) {
    console.log('Navigating to project', repository);
    this.router.navigate(['repo', repository.id.toString(), 'ci-cd']);
  }

  openProjectExternal(event: Event, repository: RepositoryInfoDto) {
    window.open(repository.htmlUrl, '_blank');
    event.stopPropagation();
  }
}
