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
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.estaAutenticado() ? true : router.createUrlTree(['/login']);
};
