import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';

export const loggedInGuard: CanActivateFn = () => {
  const router = inject(Router);
  const keycloakService = inject(KeycloakService);

  const isLoggedIn = keycloakService.isLoggedIn();

  if (isLoggedIn === undefined || !isLoggedIn) {
    router.navigate(['/unauthorized']);
    return false;
  }

  return true;
};
