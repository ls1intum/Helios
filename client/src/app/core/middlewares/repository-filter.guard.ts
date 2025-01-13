import { inject, Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChild, GuardResult, MaybeAsync } from '@angular/router';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { client } from '../modules/openapi';
import { RepositoryService } from '../services/repository.service';

@Injectable()
export class RepositoryFilterGuard implements CanActivateChild {
  queryClient = inject(QueryClient);
  repositoryService = inject(RepositoryService);

  canActivateChild(route: ActivatedRouteSnapshot): MaybeAsync<GuardResult> {
    client.interceptors.request.use(config => {
      if (!route.params['repositoryId']) {
        config.headers.delete('X-Repository-Id');
      } else {
        config.headers.set('X-Repository-Id', route.params['repositoryId']);
      }

      return config;
    });

    if (route.params['repositoryId']) this.repositoryService.currentRepositoryId.set(route.params['repositoryId']);
    else this.repositoryService.currentRepositoryId.set(null);

    this.queryClient.invalidateQueries();
    return true;
  }
}
