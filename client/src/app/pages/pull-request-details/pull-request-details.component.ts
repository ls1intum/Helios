import { Component, computed, inject, input } from '@angular/core';
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
