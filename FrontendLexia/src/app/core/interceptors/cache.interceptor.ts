import { HttpEvent, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, defer, finalize, of, share, tap } from 'rxjs';
import { CargaGlobalService } from '../services/carga-global.service';

/**
 * Hace que la app se sienta más rápida sin cambiar el backend.
 *
 * 1. Peticiones GET idénticas en vuelo se comparten: si dos componentes piden
 *    lo mismo a la vez (p. ej. el contador de mensajes), sale una sola.
 * 2. Los catálogos (listas de estados, materias, juzgados...) casi no cambian:
 *    se guardan 5 minutos en memoria y al volver a una pantalla aparecen al
 *    instante, sin esperar al servidor.
 *
 * Cualquier escritura (POST, PUT, PATCH, DELETE) vacía la caché, así que un
 * catálogo editado en Configuración se ve actualizado enseguida.
 */
const TTL_CATALOGOS_MS = 5 * 60_000;
const enVuelo = new Map<string, Observable<HttpEvent<unknown>>>();
const cache = new Map<string, { expira: number; respuesta: HttpResponse<unknown> }>();

const esCatalogo = (url: string) => /\/catalogos(\/|\?|$)|\/usuarios\/roles(\?|$)|\/escritos-ia\/tipos(\?|$)/.test(url);

export function limpiarCacheHttp(): void {
  cache.clear();
}

export const cacheInterceptor: HttpInterceptorFn = (req, next) => {
  const carga = inject(CargaGlobalService);
  // defer: el contador sube al suscribirse, no al armar la petición.
  const conBarra = (obs: Observable<HttpEvent<unknown>>) => defer(() => {
    carga.iniciar();
    return obs.pipe(finalize(() => carga.terminar()));
  });

  if (req.method !== 'GET') {
    cache.clear();
    return conBarra(next(req));
  }
  // Las descargas de archivos (blob) no se comparten ni se guardan.
  if (req.responseType === 'blob' || req.responseType === 'arraybuffer') {
    return next(req);
  }

  const clave = req.urlWithParams;
  const guardada = cache.get(clave);
  if (guardada && guardada.expira > Date.now()) {
    return of(guardada.respuesta.clone());
  }

  const existente = enVuelo.get(clave);
  if (existente) return existente;

  // El contador de mensajes se consulta solo cada pocos segundos: no enciende la barra.
  const silenciosa = /\/(no-leidos|notificaciones)(\?|$)/.test(clave);
  const peticion = (silenciosa ? next(req) : conBarra(next(req))).pipe(
    tap(evento => {
      if (evento instanceof HttpResponse && esCatalogo(clave)) {
        cache.set(clave, { expira: Date.now() + TTL_CATALOGOS_MS, respuesta: evento.clone() });
      }
    }),
    finalize(() => enVuelo.delete(clave)),
    share()
  );
  enVuelo.set(clave, peticion);
  return peticion;
};
