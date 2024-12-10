import { Component, inject } from '@angular/core';

import { Router } from '@angular/router';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { PullRequestInfoDTO } from '@app/core/modules/openapi';
import { PullRequestStoreService } from '@app/core/services/pull-requests';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { AvatarModule } from 'primeng/avatar';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';


@Component({
  selector: 'app-pull-request-table',
  imports: [TableModule, AvatarModule, TagModule, IconsModule, SkeletonModule, AvatarGroupModule, TooltipModule, MarkdownPipe],
  templateUrl: './pull-request-table.component.html',
  styles: [`
    :host ::ng-deep {
      .p-avatar {
        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
      }
    }
  `]
})
export class PullRequestTableComponent {
  pullRequestStore = inject(PullRequestStoreService);
  router = inject(Router);

  get isError() {
    return this.pullRequestStore.isError;
  }

  get isEmpty() {
    return this.pullRequestStore.isEmpty;
  }

  get isLoading() {
    return this.pullRequestStore.isLoading;
  }

  getStatus(pr: PullRequestInfoDTO): string {
    if (pr.isMerged) return 'Merged';
    return pr.state === 'OPEN' ? 'Open' : 'Closed';
  }

  getStatusSeverity(pr: PullRequestInfoDTO): ('success' | 'danger' | 'info') {
    if (pr.isMerged) return 'info';
    return pr.state === 'OPEN' ? 'success' : 'danger';
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  }

  openPRExternal(pr: PullRequestInfoDTO): void {
    window.open(pr.htmlUrl, '_blank');
  }

  openPR(pr: PullRequestInfoDTO): void {
    this.router.navigate(['pipeline', 'pr', pr.id]);
  }
}

