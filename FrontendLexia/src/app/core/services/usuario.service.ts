import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, finalize, of, shareReplay, tap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import {
  CargaDetallada, CredencialTemporal, FiltroEstadoUsuario, GuardarUsuario, ReasignarRequest,
  PaginaUsuarios, ResultadoReasignacion, ResumenUsuarios, RolOpcion, UsuarioDetalle, UsuarioLista
} from '../models/usuario.models';

/** Personal del estudio. Todo el endpoint exige USUARIO_GESTIONAR. */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiUrl}/usuarios`;
  private readonly cacheFotos = new Map<string, Blob>();
  private readonly descargasFotos = new Map<string, Observable<Blob>>();
  private readonly limiteCacheFotos = 80;
  private readonly limiteBytesCacheFotos = 10 * 1024 * 1024;
  private readonly maximoBlobEnCache = 512 * 1024;
  private bytesCacheFotos = 0;
  private readonly rolesCache$ = this.http.get<RolOpcion[]>(`${this.base}/roles`).pipe(
    shareReplay({ bufferSize: 1, refCount: false })
  );

  listar(filtros: { texto?: string; rol?: string; estado?: FiltroEstadoUsuario } = {}): Observable<UsuarioLista[]> {
    let params = new HttpParams();
    Object.entries(filtros).forEach(([k, v]) => { if (v) params = params.set(k, v); });
    return this.http.get<UsuarioLista[]>(this.base, { params });
  }

  listarPagina(
    filtros: { texto?: string; rol?: string; estado?: FiltroEstadoUsuario } = {},
    page = 0,
    size = 20
  ): Observable<PaginaUsuarios<UsuarioLista>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);
    Object.entries(filtros).forEach(([k, v]) => { if (v) params = params.set(k, v); });
    return this.http.get<PaginaUsuarios<UsuarioLista>>(`${this.base}/pagina`, { params });
  }

  resumen(): Observable<ResumenUsuarios> {
    return this.http.get<ResumenUsuarios>(`${this.base}/resumen`);
  }

  roles(): Observable<RolOpcion[]> { return this.rolesCache$; }
  detalle(uuid: string): Observable<UsuarioDetalle> { return this.http.get<UsuarioDetalle>(`${this.base}/${uuid}`); }
  crear(datos: GuardarUsuario): Observable<CredencialTemporal> { return this.http.post<CredencialTemporal>(this.base, datos); }
  actualizar(uuid: string, datos: GuardarUsuario): Observable<void> { return this.http.put<void>(`${this.base}/${uuid}`, datos); }
  subirFoto(uuid: string, archivo: File): Observable<{ fotoUrl: string }> {
    const datos = new FormData(); datos.append('archivo', archivo);
    return this.http.post<{ fotoUrl: string }>(`${this.base}/${uuid}/foto`, datos);
  }

  /**
   * Descarga una foto con el interceptor JWT. El nombre se extrae de fotoUrl,
   * pero el backend vuelve a comprobar que pertenece al estudio autenticado.
   */
  obtenerFoto(fotoUrl: string): Observable<Blob> {
    const nombre = this.nombreArchivoFoto(fotoUrl);
    if (!nombre) return throwError(() => new Error('La ruta de la foto no es válida.'));

    // La caché se separa por usuario autenticado: un cambio de sesión nunca
    // reutiliza en memoria el Blob privado que obtuvo otra cuenta/estudio.
    const uuidSesion = this.auth.usuario()?.uuid;
    const claveCache = uuidSesion ? `${uuidSesion}:${nombre}` : null;

    if (claveCache) {
      const almacenada = this.cacheFotos.get(claveCache);
      if (almacenada) {
        // LRU sencillo: una foto usada recientemente permanece en caché.
        this.cacheFotos.delete(claveCache);
        this.cacheFotos.set(claveCache, almacenada);
        return of(almacenada);
      }

      const descargaPendiente = this.descargasFotos.get(claveCache);
      if (descargaPendiente) return descargaPendiente;
    }

    const descarga = this.http.get(`${this.base}/foto/${encodeURIComponent(nombre)}`, {
      responseType: 'blob'
    }).pipe(
      tap(blob => {
        // Si aún no hay identidad local o la foto es pesada, no se conserva.
        // Los nuevos avatares son cuadrados de 512 px y suelen pesar mucho menos.
        if (!claveCache || blob.size < 1 || blob.size > this.maximoBlobEnCache) return;
        const previa = this.cacheFotos.get(claveCache);
        if (previa) this.bytesCacheFotos -= previa.size;
        this.cacheFotos.delete(claveCache);
        this.cacheFotos.set(claveCache, blob);
        this.bytesCacheFotos += blob.size;
        while (this.cacheFotos.size > this.limiteCacheFotos || this.bytesCacheFotos > this.limiteBytesCacheFotos) {
          const masAntigua = this.cacheFotos.keys().next().value;
          if (masAntigua === undefined) break;
          const blobAntiguo = this.cacheFotos.get(masAntigua);
          this.cacheFotos.delete(masAntigua);
          this.bytesCacheFotos -= blobAntiguo?.size ?? 0;
        }
      }),
      finalize(() => { if (claveCache) this.descargasFotos.delete(claveCache); }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    if (claveCache) this.descargasFotos.set(claveCache, descarga);
    return descarga;
  }

  private nombreArchivoFoto(fotoUrl: string): string | null {
    const ruta = fotoUrl.split(/[?#]/, 1)[0];
    const nombre = ruta.substring(ruta.lastIndexOf('/') + 1);
    return /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\.(?:png|jpg|gif|webp)$/i.test(nombre) ? nombre : null;
  }
  desactivar(uuid: string): Observable<void> { return this.http.post<void>(`${this.base}/${uuid}/desactivar`, {}); }
  activar(uuid: string): Observable<void> { return this.http.post<void>(`${this.base}/${uuid}/activar`, {}); }
  desbloquear(uuid: string): Observable<void> { return this.http.post<void>(`${this.base}/${uuid}/desbloquear`, {}); }
  carga(uuid: string): Observable<CargaDetallada> { return this.http.get<CargaDetallada>(`${this.base}/${uuid}/carga`); }
  reasignar(uuid: string, datos: ReasignarRequest): Observable<ResultadoReasignacion> {
    return this.http.post<ResultadoReasignacion>(`${this.base}/${uuid}/reasignar`, datos);
  }
  restablecerPassword(uuid: string): Observable<CredencialTemporal> {
    return this.http.post<CredencialTemporal>(`${this.base}/${uuid}/restablecer-password`, {});
  }
}
