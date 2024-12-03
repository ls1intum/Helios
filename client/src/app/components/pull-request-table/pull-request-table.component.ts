import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { tap } from 'rxjs';
import { IconsModule } from 'icons.module';
import { PullRequestControllerService, PullRequestInfoDTO } from '@app/core/modules/openapi';
import { ButtonModule } from 'primeng/button';
import { RouterLink } from '@angular/router';


@Component({
  selector: 'app-pull-request-table',
  imports: [CommonModule, TableModule, AvatarModule, TagModule, IconsModule, ButtonModule, RouterLink],
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


  pullRequests = signal<PullRequestInfoDTO[]>([]);
  isLoading = signal(true);

  query = injectQuery(() => ({
    queryKey: ['pullRequests'],
    queryFn: () => this.pullRequestService.getAllPullRequests()
        .pipe(
          tap(data => {
            this.pullRequests.set(data);
            this.isLoading.set(false);
          })
        ).subscribe(),
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

  openPR(url: string): void {
    window.open(url, '_blank');
  }
}
