import { SlicePipe } from '@angular/common';
import { Component, computed, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommitInfoDto } from '@app/core/modules/openapi';
import {
  createReleaseCandidateMutation,
  getBranchByRepositoryIdAndNameQueryKey,
  getCommitsSinceLastReleaseCandidateOptions,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconGitBranch, IconGitCommit, IconTag } from 'angular-tabler-icons/icons';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-release-candidate-create',
  imports: [ButtonModule, DialogModule, TablerIconComponent, FormsModule, SkeletonModule, InputTextModule, TagModule, SlicePipe, TooltipModule],
  providers: [
    provideTablerIcons({
      IconTag,
      IconGitBranch,
      IconGitCommit,
    }),
  ],
  templateUrl: './release-candidate-create.component.html',
})
export class ReleaseCandidateCreateComponent {
  private messageService = inject(MessageService);
  private queryClient = inject(QueryClient);

  isVisible = model.required<boolean>();
  branchName = input.required<string>();
  headCommit = input.required<CommitInfoDto>();
  repositoryId = input.required<number>();

  isCommitListVisible = signal(false);
  releaseCandidateName = signal('');

  newCommitListQuery = injectQuery(() => ({
    ...getCommitsSinceLastReleaseCandidateOptions({ query: { branch: this.branchName() } }),
    enabled: !!this.isVisible() && !!this.branchName(),
  }));
  newCommitListMutation = injectMutation(() => ({
    ...createReleaseCandidateMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Candidate Created', detail: 'Release candidate has been created successfully' });
      this.queryClient.invalidateQueries({
        queryKey: getBranchByRepositoryIdAndNameQueryKey({ path: { repoId: this.repositoryId() }, query: { name: this.branchName() } }),
      });
      this.onClose();
    },
  }));

  isIdentical = computed(() => this.newCommitListQuery.data()?.aheadBy === 0 && this.newCommitListQuery.data()?.behindBy === 0);

  onClose = () => {
    this.isVisible.update(() => false);
  };

  createReleaseCandidate = () => {
    this.newCommitListMutation.mutate({
      body: { name: this.releaseCandidateName(), commitSha: this.headCommit().sha, branchName: this.branchName() },
    });
  };
}
