import { inject, Injectable, signal } from '@angular/core';
import { PullRequestControllerService, PullRequestInfoDTO } from '../modules/openapi';
import { catchError, tap } from 'rxjs';
import { injectQuery } from '@tanstack/angular-query-experimental';

@Injectable({
  providedIn: 'root',
})
export class PullRequestStoreService {
  private pullRequestsState = signal<PullRequestInfoDTO[]>([]);
  private pullRequestService = inject(PullRequestControllerService);

  isError = signal(false);
  isEmpty = signal(false);
  isLoading = signal(false);

  get pullRequests() {
    return this.pullRequestsState.asReadonly();
  }

  setPullRequests(pullRequests: PullRequestInfoDTO[]) {
    this.pullRequestsState.set(pullRequests);
  }

  fetchPullRequests() {
    this.isLoading.set(true);
    return this.pullRequestService.getAllPullRequests().pipe(
      tap(data => {
        const openPullRequests = data.filter(pr => pr.state === 'OPEN');
        this.setPullRequests(openPullRequests);
        this.isEmpty.set(openPullRequests.length === 0);
        this.isLoading.set(false);
      }),
      catchError(() => {
        this.isError.set(true);
        this.isLoading.set(false);
        return [];
      })
    );
  }

  query = injectQuery(() => ({
    queryKey: ['pullRequests'],
    queryFn: () => {
      return this.fetchPullRequests().subscribe();
    },
  }));
}
