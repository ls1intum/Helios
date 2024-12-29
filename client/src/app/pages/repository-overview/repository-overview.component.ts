import { Component, computed, inject, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { ConnectRepoComponent } from '@app/components/connect-repo/connect-repo.component';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { RepositoryInfoDto } from '@app/core/modules/openapi';
import { RepositoryService } from '@app/core/services/repository.service';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule } from 'primeng/dataview';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-repository-overview',
  imports: [DataViewModule, ButtonModule, TagModule, CardModule, ChipModule, IconsModule, ConnectRepoComponent, PageHeadingComponent],
  templateUrl: './repository-overview.component.html',
})
export class ProjectOverviewComponent {
  private repositoryService = inject(RepositoryService);
  private router = inject(Router);

  readonly repositoryConnection = viewChild.required(ConnectRepoComponent);

  repositories = computed(() => this.repositoryService.repositories());
  loading = computed(() => this.repositoryService.loading());

  showDialog() {
    console.log(this.repositories());
    this.repositoryConnection().show();
  }

  refreshRepositories() {
    this.repositoryService.refreshRepositories().subscribe();
  }

  navigateToProject(repository: RepositoryInfoDto) {
    console.log('Navigating to project', repository);
    this.router.navigate(['repo', repository.id.toString(), 'ci-cd']);
  }
}
