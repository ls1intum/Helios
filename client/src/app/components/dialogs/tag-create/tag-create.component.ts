import { SlicePipe } from '@angular/common';
import { Component, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommitInfoDto } from '@app/core/modules/openapi';
import { createTagMutation, getBranchByRepositoryIdAndNameQueryKey, getCommitsSinceLastTagOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-tag-create',
  imports: [ButtonModule, DialogModule, IconsModule, FormsModule, InputTextModule, TagModule, SlicePipe, TooltipModule],
  templateUrl: './tag-create.component.html',
})
export class TagCreateComponent {
  private messageService = inject(MessageService);
  private keycloakService = inject(KeycloakService);
  private queryClient = inject(QueryClient);

  isVisible = model.required<boolean>();
  branchName = input.required<string>();
  headCommit = input.required<CommitInfoDto>();
  repositoryId = input.required<number>();

  isCommitListVisible = signal(false);
  tagName = signal('');

  newCommitListQuery = injectQuery(() => ({
    ...getCommitsSinceLastTagOptions({ query: { branch: this.branchName() } }),
    enabled: !!this.isVisible() && !!this.branchName(),
  }));
  newCommitListMutation = injectMutation(() => ({
    ...createTagMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Tag created', detail: 'Tag has been created successfully' });
      this.queryClient.invalidateQueries({
        queryKey: getBranchByRepositoryIdAndNameQueryKey({ path: { repoId: this.repositoryId() }, query: { name: this.branchName() } }),
      });
      this.onClose();
    },
  }));

  onClose = () => {
    this.isVisible.update(() => false);
  };

  createTag = () => {
    this.newCommitListMutation.mutate({
      body: { name: this.tagName(), commitSha: this.headCommit().sha, branchName: this.branchName() },
    });
  };
}
