import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // El interceptor adjunta el token a cada peticion y devuelve al login
    // cuando el backend responde 401.
    provideHttpClient(withInterceptors([authInterceptor])),
    // Antes de mostrar nada, valida contra el backend que el token guardado
    // siga sirviendo. Evita entrar a la aplicacion con una sesion expirada.
    provideAppInitializer(() => inject(AuthService).restaurarSesion())
  ]
};
