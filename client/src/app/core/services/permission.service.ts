import { Injectable, computed } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getUserPermissionsOptions } from '../modules/openapi/@tanstack/angular-query-experimental.gen';

@Injectable({
  providedIn: 'root',
})
export class PermissionService {
  permissionsQuery = injectQuery(() => ({
    ...getUserPermissionsOptions(),
  }));

  hasWritePermission = computed(() => this.permissionsQuery.data()?.permission === 'WRITE' || this.permissionsQuery.data()?.permission === 'ADMIN');
  isAtLeastMaintainer = computed(() => this.permissionsQuery.data()?.permission === 'ADMIN' || this.permissionsQuery.data()?.roleName === 'maintain');

  refetchPermissions() {
    this.permissionsQuery.refetch();
  }
}
