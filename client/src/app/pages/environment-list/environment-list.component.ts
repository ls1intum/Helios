import {Component, computed, inject} from '@angular/core';
import {AccordionModule} from 'primeng/accordion';
import {CommonModule} from '@angular/common';
import {LockTagComponent} from '@app/components/lock-tag/lock-tag.component';
import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {
  EnvironmentCommitInfoComponent
} from '../../components/environment-commit-info/environment-commit-info.component';
import {ButtonModule} from 'primeng/button';
import {RouterLink} from '@angular/router';
import {FetchEnvironmentService} from '@app/core/services/fetch/environment';
import {injectQuery} from '@tanstack/angular-query-experimental';
import { getAllEnvironmentsOptions } from '@app/core/modules/openapi2/@tanstack/angular-query-experimental.gen';

@Component({
  selector: 'app-environment-list',
  imports: [AccordionModule, CommonModule, LockTagComponent, RouterLink, TagModule, IconsModule, EnvironmentCommitInfoComponent, ButtonModule],
  providers: [FetchEnvironmentService],
  templateUrl: './environment-list.component.html',
})
export class EnvironmentListComponent {
  fetchEnvironmentService = inject(FetchEnvironmentService);

  environmentQuery = injectQuery(() => getAllEnvironmentsOptions());

  isPending = computed(() => this.environmentQuery.isPending());
  environments = computed(() => this.environmentQuery.data());

  getFullUrl(url: string | undefined): string {
    if (!url) {
      return '';
    }

    if (url && (!url.startsWith('http') && !url.startsWith('https'))) {
      return 'http://' + url;
    }

    return url;
  }
}
