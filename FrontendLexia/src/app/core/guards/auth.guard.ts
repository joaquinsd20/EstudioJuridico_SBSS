import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Impide entrar a una pantalla sin sesion iniciada.
 *
 * Es lo que le da sentido al login: sin esto, cualquiera puede escribir
 * /dashboard en la barra de direcciones y ver la aplicacion entera.
 *
 * Solo comprueba que exista un token; la autoridad real la valida el backend en
 * cada peticion. Un guard de frontend es comodidad de navegacion, nunca
 * seguridad: quien manipule el localStorage vera la pantalla, pero la API le
 * respondera 401 y el interceptor lo devolvera al login.
 */
export const authGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.estaAutenticado()) {
    return router.createUrlTree(['/login']);
  }

  // Con contrasena temporal solo se puede ir a cambiarla. El backend ya bloquea
  // todo lo demas con 403; esto evita pintar pantallas que no podrian cargar
  // nada.
  if (auth.debeCambiarPassword() && route.routeConfig?.path !== 'cambiar-password') {
    return router.createUrlTree(['/cambiar-password']);
  }

  return true;
};
