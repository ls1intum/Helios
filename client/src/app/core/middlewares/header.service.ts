import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChild, GuardResult, MaybeAsync } from '@angular/router';
import { client } from '../modules/openapi';

@Injectable()
export class HeaderGuard implements CanActivateChild {
  constructor() {}
  canActivateChild(route: ActivatedRouteSnapshot): MaybeAsync<GuardResult> {
    client.interceptors.request.use(config => {
      config.headers.set('X-Repository-Id', route.params['repositoryId']);
      return config;
    });
    return true;
  }
}
