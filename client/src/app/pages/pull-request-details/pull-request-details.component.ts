import { Component, computed, inject, input, signal } from '@angular/core';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { PipelineComponent, PipelineSelector } from '@app/components/pipeline/pipeline.component';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DeploymentSelectionComponent } from '@app/components/deployment-selection/deployment-selection.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { getPullRequestByRepositoryIdAndNumberOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { PullRequestStatusIconComponent } from '@app/components/pull-request-status-icon/pull-request-status-icon.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconGitBranch } from 'angular-tabler-icons/icons';
import { PipelineTestResultsComponent } from '@app/components/pipeline/test-results/pipeline-test-results.component';
import { PullRequestDeploymentHistoryComponent } from '@app/components/pull-request-deployment-history/pull-request-deployment-history.component';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Divider } from 'primeng/divider';

@Component({
  selector: 'app-branch-details',
  imports: [
    InputTextModule,
    TagModule,
    TablerIconComponent,
    ButtonModule,
    PipelineComponent,
    MarkdownPipe,
    DeploymentSelectionComponent,
    SkeletonModule,
    UserAvatarComponent,
    PullRequestStatusIconComponent,
    PipelineTestResultsComponent,
    PullRequestDeploymentHistoryComponent,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Divider,
  ],
  providers: [
    provideTablerIcons({
      IconGitBranch,
    }),
  ],
  templateUrl: './pull-request-details.component.html',
})
export class PullRequestDetailsComponent {
  private keycloakService = inject(KeycloakService);

  repositoryId = input.required<number>();
  pullRequestNumber = input.required<number>();
  deploymentTab = signal<'deploy' | 'history'>('deploy');

  onDeploymentTabChange(value: string | number | undefined): void {
    if (value === 'deploy' || value === 'history') {
      this.deploymentTab.set(value);
    }
  }
  query = injectQuery(() => ({
    ...getPullRequestByRepositoryIdAndNumberOptions({ path: { repoId: this.repositoryId(), number: this.pullRequestNumber() } }),
    refetchInterval: 30000,
  }));

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

  pipelineSelector = computed<PipelineSelector | null>(() => {
    const pr = this.query.data();

    if (!pr) {
      return null;
    }

    return {
      repositoryId: this.repositoryId(),
      pullRequestId: pr.id,
    };
  });
}
