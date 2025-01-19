import { Injectable, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getUserPermissionsOptions } from '../modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from './keycloak/keycloak.service';
import { RepositoryService } from './repository.service';

@Injectable({
  providedIn: 'root',
})
export class PermissionService {
  keycloak = inject(KeycloakService);
  repositoryService = inject(RepositoryService);

  permissionsQuery = injectQuery(() => ({
    ...getUserPermissionsOptions(),
    enabled: () => !!this.repositoryService.currentRepositoryId() && !!this.keycloak.isLoggedIn(),
  }));

  heliosDevelopers = ['gbanu', 'thielpa', 'egekocabas', 'turkerkoc', 'stefannemeth'];
  isHeliosDeveloper = computed(() => !!this.keycloak.profile?.username && this.heliosDevelopers.includes(this.keycloak.profile.username.toLowerCase()));

  hasWritePermission = computed(() => this.permissionsQuery.data()?.permission === 'WRITE' || this.permissionsQuery.data()?.permission === 'ADMIN' || this.isHeliosDeveloper());
  isAtLeastMaintainer = computed(() => this.permissionsQuery.data()?.permission === 'ADMIN' || this.permissionsQuery.data()?.roleName === 'maintain' || this.isHeliosDeveloper());
  isAdmin = computed(() => this.permissionsQuery.data()?.permission === 'ADMIN' || this.isHeliosDeveloper());

  refetchPermissions() {
    this.permissionsQuery.refetch();
  }
}
