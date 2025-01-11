import { inject, Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChild, GuardResult, MaybeAsync } from '@angular/router';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { client } from '../modules/openapi';

@Injectable()
export class RepositoryFilterGuard implements CanActivateChild {
  queryClient = inject(QueryClient);

  canActivateChild(route: ActivatedRouteSnapshot): MaybeAsync<GuardResult> {
    client.interceptors.request.use(config => {
      if (!route.params['repositoryId']) {
        config.headers.delete('X-Repository-Id');
      } else {
        config.headers.set('X-Repository-Id', route.params['repositoryId']);
      }

      return config;
    });

    this.queryClient.invalidateQueries({ queryKey: ['getUserPermissions'] });

    return true;
  }
}
