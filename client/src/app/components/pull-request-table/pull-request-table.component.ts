import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { AvatarGroupModule } from 'primeng/avatargroup';
import { TooltipModule } from 'primeng/tooltip';
import { Component, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { catchError, tap } from 'rxjs';
import { IconsModule } from 'icons.module';
import { SkeletonModule } from 'primeng/skeleton';
import { ActivatedRoute, Router } from '@angular/router';
import { DateService } from '@app/core/services/date.service';
import { getAllPullRequestsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PullRequestInfoDto } from '@app/core/modules/openapi';


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
  dateService = inject(DateService);
  router = inject(Router);
  route = inject(ActivatedRoute)

  query = injectQuery(() => getAllPullRequestsOptions());

  getStatus(pr: PullRequestInfoDto): string {
    if (pr.isMerged) return 'Merged';
    return pr.state === 'OPEN' ? 'Open' : 'Closed';
  }

  getStatusSeverity(pr: PullRequestInfoDto): ('success' | 'danger' | 'info') {
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

  openPRExternal(pr: PullRequestInfoDto): void {
    window.open(pr.htmlUrl, '_blank');
  }

  openPR(pr: PullRequestInfoDto): void {
    this.router.navigate(['pr', pr.number], {
      relativeTo: this.route.parent
    });
  }
}

