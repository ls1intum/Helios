import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PermissionService } from '../services/permission.service';

export const writePermissionGuard: CanActivateFn = () => {
  const router = inject(Router);
  const permissionService = inject(PermissionService);

  const hasAccess = permissionService.hasWritePermission();

  if (!hasAccess) {
    router.navigate(['/unauthorized']);
  }

  return hasAccess;
};
