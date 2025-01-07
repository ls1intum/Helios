import { Component, computed, inject, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { ConnectRepoComponent } from '@app/components/connect-repo/connect-repo.component';
import { HeliosIconComponent } from '@app/components/helios-icon/helios-icon.component';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { ProfileNavSectionComponent } from '@app/components/profile-nav-section/profile-nav-section.component';
import { RepositoryInfoDto } from '@app/core/modules/openapi';
import { getAllRepositoriesOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { RepositoryService } from '@app/core/services/repository.service';
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
  imports: [
    DataViewModule,
    ButtonModule,
    TagModule,
    CardModule,
    ChipModule,
    IconsModule,
    ConnectRepoComponent,
    PageHeadingComponent,
    ToastModule,
    ProfileNavSectionComponent,
    HeliosIconComponent,
  ],
  templateUrl: './repository-overview.component.html',
})
export class ProjectOverviewComponent {
  private repositoryService = inject(RepositoryService);
  private keycloakService = inject(KeycloakService);
  private router = inject(Router);

  query = injectQuery(() => getAllRepositoriesOptions());
  repositories = computed(() => this.query.data());

  readonly repositoryConnection = viewChild.required(ConnectRepoComponent);

  showDialog() {
    this.repositoryConnection().show();
  }

  loggedIn() {
    return this.keycloakService.loggedIn();
  }

  refreshRepositories() {
    this.repositoryService.refreshRepositories().subscribe();
  }

  navigateToProject(repository: RepositoryInfoDto) {
    console.log('Navigating to project', repository);
    this.router.navigate(['repo', repository.id.toString(), 'ci-cd']);
  }
}
