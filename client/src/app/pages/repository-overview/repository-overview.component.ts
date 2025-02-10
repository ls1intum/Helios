import { Component, inject } from '@angular/core';
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

@Component({
  selector: 'app-repository-overview',
  imports: [DataViewModule, ButtonModule, TagModule, CardModule, ChipModule, IconsModule, PageHeadingComponent, ToastModule],
  templateUrl: './repository-overview.component.html',
})
export class RepositoryOverviewComponent {
  private router = inject(Router);

  query = injectQuery(() => getAllRepositoriesOptions());

  navigateToProject(repository: RepositoryInfoDto) {
    console.log('Navigating to project', repository);
    this.router.navigate(['repo', repository.id.toString(), 'ci-cd']);
  }
}
