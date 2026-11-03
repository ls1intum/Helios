import { Injectable, computed, inject } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getUserPermissionsOptions } from '../modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from './keycloak/keycloak.service';
import { RepositoryService } from './repository.service';
import { environment } from 'environments/environment';

@Injectable({
  providedIn: 'root',
})
export class PermissionService {
  keycloak = inject(KeycloakService);
  repositoryService = inject(RepositoryService);

  // The backend resolves the role per repository (X-Repository-Id header), so the cache key must
  // include the repository id (the headers option is part of the generated query key). Otherwise a
  // role loaded for one repository is reused for another. The short staleTime lets GitHub role
  // changes show up quickly instead of after the global 5 minute default.
  permissionsQuery = injectQuery(() => ({
    ...getUserPermissionsOptions({ headers: { 'X-Repository-Id': String(this.repositoryService.currentRepositoryId()) } }),
    staleTime: 1000 * 30,
    refetchOnWindowFocus: true,
    enabled: () => !!this.repositoryService.currentRepositoryId() && this.keycloak.isLoggedIn(),
  }));

  heliosDevelopers: string[] = environment.heliosDevelopers;
  isHeliosDeveloper = computed(() => !!this.keycloak.profile?.username && this.heliosDevelopers.includes(this.keycloak.profile.username.toLowerCase()));

  hasWritePermission = computed(
    () => this.keycloak.isLoggedIn() && (this.permissionsQuery.data()?.permission === 'WRITE' || this.permissionsQuery.data()?.permission === 'ADMIN' || this.isHeliosDeveloper())
  );
  isAtLeastMaintainer = computed(
    () => this.keycloak.isLoggedIn() && (this.permissionsQuery.data()?.permission === 'ADMIN' || this.permissionsQuery.data()?.roleName === 'maintain' || this.isHeliosDeveloper())
  );
  isAdmin = computed(() => this.keycloak.isLoggedIn() && (this.permissionsQuery.data()?.permission === 'ADMIN' || this.isHeliosDeveloper()));
}
