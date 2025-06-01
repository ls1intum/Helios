import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { RepositoryInfoDto } from '@app/core/modules/openapi';
import { getAllRepositoriesOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectQuery } from '@tanstack/angular-query-experimental';
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
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { TooltipModule } from 'primeng/tooltip';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { SearchDataViewService } from '@app/core/services/search-dataview.service';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconBrandGithub,
  IconCode,
  IconExclamationCircle,
  IconGitBranch,
  IconGitPullRequest,
  IconQuestionMark,
  IconServer,
  IconSettings,
  IconStar,
  IconSun,
  IconTag,
} from 'angular-tabler-icons/icons';
import { DialogModule } from 'primeng/dialog';
import { CarouselModule, CarouselPageEvent } from 'primeng/carousel';
import { connectionSteps } from './connection-steps';

const FILTER_OPTIONS = [{ name: 'All repositories', filter: (repos: RepositoryInfoDto[]) => repos }];

@Component({
  selector: 'app-repository-overview',
  imports: [
    DataViewModule,
    ButtonModule,
    TagModule,
    DividerModule,
    CardModule,
    AvatarModule,
    AvatarGroupModule,
    ChipModule,
    TablerIconComponent,
    PageHeadingComponent,
    ToastModule,
    Skeleton,
    TooltipModule,
    UserAvatarComponent,
    TimeAgoPipe,
    TableModule,
    TableFilterComponent,
    DialogModule,
    CarouselModule,
  ],
  providers: [
    SearchTableService,
    { provide: FILTER_OPTIONS_TOKEN, useValue: FILTER_OPTIONS },
    provideTablerIcons({
      IconBrandGithub,
      IconTag,
      IconStar,
      IconGitBranch,
      IconGitPullRequest,
      IconExclamationCircle,
      IconQuestionMark,
      IconSettings,
      IconCode,
      IconServer,
      IconSun,
    }),
  ],
  templateUrl: './repository-overview.component.html',
})
export class RepositoryOverviewComponent {
  private router = inject(Router);
  keycloakService = inject(KeycloakService);
  searchDataViewService = inject(SearchDataViewService);

  connectionSteps = connectionSteps;
  addRepositoryDialogVisible = signal(false);
  currentStep = signal(0);

  query = injectQuery(() => getAllRepositoriesOptions());

  showAddRepositoryDialog() {
    this.currentStep.set(0);
    this.addRepositoryDialogVisible.set(true);
  }

  nextStep() {
    if (this.currentStep() < connectionSteps.length - 1) {
      this.currentStep.set(this.currentStep() + 1);
    }
  }

  previousStep() {
    if (this.currentStep() > 0) {
      this.currentStep.set(this.currentStep() - 1);
    }
  }

  finishSetup() {
    // Here you would typically implement the final action
    // For now, just close the dialog
    this.addRepositoryDialogVisible.set(false);
  }

  onPageChange(event: CarouselPageEvent) {
    console.log('Page changed:', event.page);
    this.currentStep.set(event.page || 0);
  }
  openExternalLink(url: string): void {
    window.open(url, '_blank');
  }

  navigateToReleases(repository: RepositoryInfoDto) {
    this.router.navigate(['repo', repository.id.toString(), 'release']);
  }

  navigateToBranches(repository: RepositoryInfoDto) {
    this.router.navigate(['repo', repository.id.toString(), 'ci-cd', 'branch']);
  }

  navigateToPullRequests(repository: RepositoryInfoDto) {
    this.router.navigate(['repo', repository.id.toString(), 'ci-cd', 'pr']);
  }

  openProjectExternal(event: Event, repository: RepositoryInfoDto) {
    window.open(repository.htmlUrl, '_blank');
    event.stopPropagation();
  }
}
