import { Component, computed, inject, input, signal } from '@angular/core';

import { PipelineComponent, PipelineSelector } from '@app/components/pipeline/pipeline.component';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { DeploymentSelectionComponent } from '@app/components/deployment-selection/deployment-selection.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { getBranchByRepositoryIdAndNameOptions, getCommitByRepositoryIdAndNameOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { ReleaseCandidateCreateComponent } from '@app/components/dialogs/release-candidate-create/release-candidate-create.component';
import { PermissionService } from '@app/core/services/permission.service';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { RouterLink } from '@angular/router';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconGitBranch, IconTag, IconTagPlus } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-branch-details',
  imports: [
    DeploymentSelectionComponent,
    InputTextModule,
    RouterLink,
    TagModule,
    TablerIconComponent,
    ButtonModule,
    PipelineComponent,
    SkeletonModule,
    ReleaseCandidateCreateComponent,
    MarkdownPipe,
  ],
  providers: [
    provideTablerIcons({
      IconGitBranch,
      IconTag,
      IconTagPlus,
    }),
  ],
  templateUrl: './branch-details.component.html',
})
export class BranchDetailsComponent {
  private permissionService = inject(PermissionService);

  repositoryId = input.required<number>();
  branchName = input.required<string>();

  isCreateDialogVisible = signal(false);

  query = injectQuery(() => ({
    ...getBranchByRepositoryIdAndNameOptions({ path: { repoId: this.repositoryId() }, query: { name: this.branchName() } }),
    refetchInterval: 30000,
  }));

  commitQuery = injectQuery(() => ({
    ...getCommitByRepositoryIdAndNameOptions({ path: { repoId: this.repositoryId(), sha: this.query.data()?.commitSha || '' } }),
    enabled: !!this.repositoryId() && !!this.query.data(),
  }));
  commit = computed(() => this.commitQuery.data());

  isAtLeastMaintainer = computed(() => this.permissionService.isAtLeastMaintainer());

  pipelineSelector = computed<PipelineSelector | null>(() => {
    const branch = this.query.data();

    if (!branch) {
      return null;
    }

    return {
      repositoryId: this.repositoryId()!,
      branchName: branch.name,
    };
  });
}
