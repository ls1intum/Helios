import { Component, computed, inject } from '@angular/core';
import { getAllTagsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { FILTER_OPTIONS_TOKEN, SearchTableService } from '@app/core/services/search-table.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TableFilterComponent } from '../table-filter/table-filter.component';
import { TagModule } from 'primeng/tag';
import { TagInfoDto } from '@app/core/modules/openapi';
import { Router, RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';

const FILTER_OPTIONS: { name: string; filter: (prs: TagInfoDto[]) => TagInfoDto[] }[] = [];

@Component({
  selector: 'app-tag-table',
  imports: [TableModule, ButtonModule, IconsModule, SkeletonModule, TableFilterComponent, TagModule, RouterLink, SlicePipe],
  providers: [SearchTableService, { provide: FILTER_OPTIONS_TOKEN, useValue: FILTER_OPTIONS }],
  templateUrl: './tag-table.component.html',
})
export class TagTableComponent {
  tagsQuery = injectQuery(() => getAllTagsOptions());
  router = inject(Router);
  searchTableService = inject(SearchTableService);

  filteredTags = computed(() => this.tagsQuery.data() || []);
}
