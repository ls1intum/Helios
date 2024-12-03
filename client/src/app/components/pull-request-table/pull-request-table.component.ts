import { Component, inject, Injectable, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { catchError, tap } from 'rxjs';
import { IconsModule } from 'icons.module';
import { PullRequestControllerService, PullRequestInfoDTO } from '@app/core/modules/openapi';
import { SkeletonModule } from 'primeng/skeleton';


@Component({
  selector: 'app-pull-request-table',
  imports: [CommonModule, TableModule, AvatarModule, TagModule, IconsModule, SkeletonModule],
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
  isLoading = signal(false)

  query = injectQuery(() => ({
    queryKey: ['pullRequests'],
    queryFn: () => {
      this.isLoading.set(true);
      return this.pullRequestService.getAllPullRequests()
        .pipe(
          tap(data => {
            this.pullRequestStore.setPullRequests(data);
            this.isEmpty.set(data.length === 0);
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

  openPR(url: string): void {
    window.open(url, '_blank');
  }
}

@Injectable({
  providedIn: 'root'
})
export class PullRequestStoreService {
  private pullRequestsState = signal<PullRequestInfoDTO[]>([]);

  get pullRequests() {
    return this.pullRequestsState.asReadonly();
  }

  setPullRequests(pullRequests: PullRequestInfoDTO[]) {
    this.pullRequestsState.set(pullRequests);
  }
}