import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Guard de navegación; la protección definitiva sigue estando en @PreAuthorize del backend. */
export const permissionGuard: CanActivateFn = route => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const permiso = route.data['permission'] as string | undefined;

  if (!auth.estaAutenticado()) return router.createUrlTree(['/login']);
  if (auth.debeCambiarPassword()) return router.createUrlTree(['/cambiar-password']);
  if (permiso && !auth.tienePermiso(permiso)) {
    return router.createUrlTree(['/dashboard'], { queryParams: { accesoDenegado: 'configuracion' } });
  }
  return true;
};
