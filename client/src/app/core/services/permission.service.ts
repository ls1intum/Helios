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

  hasDeployPermission = computed(() => this.permissionsQuery.data()?.permission === 'ADMIN' || this.permissionsQuery.data()?.permission === 'WRITE');
  hasUnlockPermission = computed(() => this.permissionsQuery.data()?.roleName === 'admin' || this.permissionsQuery.data()?.roleName === 'maintain');
  isAdminOrMaintainer = computed(() => this.permissionsQuery.data()?.permission === 'ADMIN' || this.permissionsQuery.data()?.roleName === 'maintain');

  refetchPermissions() {
    this.permissionsQuery.refetch();
  }
}
