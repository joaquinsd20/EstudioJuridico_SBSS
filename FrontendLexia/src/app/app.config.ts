import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners
} from '@angular/core';
import { PreloadAllModules, provideRouter, withPreloading } from '@angular/router';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { cacheInterceptor } from './core/interceptors/cache.interceptor';
import { AuthService } from './core/services/auth.service';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Descarga en segundo plano el código de los demás módulos tras el primer
    // pintado: al cambiar de pantalla ya no hay que esperar esa descarga.
    provideRouter(routes, withPreloading(PreloadAllModules)),
    // El interceptor adjunta el token a cada peticion y devuelve al login
    // cuando el backend responde 401.
    provideHttpClient(withInterceptors([cacheInterceptor, authInterceptor])),
    // Antes de mostrar nada, valida contra el backend que el token guardado
    // siga sirviendo. Evita entrar a la aplicacion con una sesion expirada.
    provideAppInitializer(() => inject(AuthService).restaurarSesion())
  ]
};
