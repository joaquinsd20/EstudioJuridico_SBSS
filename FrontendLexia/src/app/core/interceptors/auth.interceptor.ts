import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Adjunta el token a cada peticion y reacciona a un 401.
 *
 * El backend distingue 401 de 403 a proposito: 401 significa "no se quien eres"
 * y obliga a volver al login; 403 significa "se quien eres pero no puedes hacer
 * esto" y NO debe expulsar al usuario de su sesion. Por eso aqui solo se
 * intercepta el 401.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.token;
  const peticion = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      // Un 401 del propio login es "credenciales incorrectas": lo maneja la
      // pantalla mostrando el mensaje, no expulsando a quien nunca entro.
      const esLogin = req.url.includes('/auth/login');

      if (error.status === 401 && !esLogin) {
        auth.cerrarSesion();
        router.navigate(['/login']);
      }

      return throwError(() => error);
    })
  );
};
