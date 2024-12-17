import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import {PullRequestControllerService, PullRequestInfoDTO} from '@app/core/modules/openapi';
import { PullRequestStoreService } from '@app/core/services/pull-requests';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { TooltipModule } from 'primeng/tooltip';
import {Component, inject, signal} from '@angular/core';
import {TableModule} from 'primeng/table';
import {AvatarModule} from 'primeng/avatar';
import {TagModule} from 'primeng/tag';
import {injectQuery} from '@tanstack/angular-query-experimental';
import {catchError, tap} from 'rxjs';
import {IconsModule} from 'icons.module';
import {SkeletonModule} from 'primeng/skeleton';
import {Router} from '@angular/router';


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
  pullRequestService = inject(PullRequestControllerService);
  pullRequestStore = inject(PullRequestStoreService);

  isError = signal(false);
  isEmpty = signal(false);
  isLoading = signal(false);
  router = inject(Router);

  query = injectQuery(() => ({
    queryKey: ['pullRequests'],
    queryFn: () => {
      this.isLoading.set(true);
      return this.pullRequestService.getAllPullRequests()
        .pipe(
          tap(data => {
            // Filter to only include open pull requests
            const openPullRequests = data.filter(pr => pr.state === 'OPEN');
            this.pullRequestStore.setPullRequests(openPullRequests);
            this.isEmpty.set(openPullRequests.length === 0);
            this.isLoading.set(false);
          }),
          catchError(() => {
              this.isError.set(true);
              this.isLoading.set(false);
              return [];
            }
          )
        ).subscribe()
    },
  }));

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
    this.router.navigate(['repo', pr.repository?.id, 'pr', pr.number]);
  }
}

