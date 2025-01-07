import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { PermissionService } from '../services/permission.service';

export const maintainerGuard: CanActivateFn = () => {
  const router = inject(Router);
  const permissionService = inject(PermissionService);

  const hasAccess = permissionService.isAdminOrMaintainer();

  if (!hasAccess) {
    router.navigate(['/repo']);
  }

  return hasAccess;
};
