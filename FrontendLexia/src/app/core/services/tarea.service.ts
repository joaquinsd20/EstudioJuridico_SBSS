import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  EtapaTarea,
  Tarea,
  TareaCatalogos,
  TareaDetalle,
  TareaExcelFila,
  TareaFiltro,
  TareaGuardar,
  TareaPagina,
  TareaResumen
} from '../models/tarea.models';

@Injectable({ providedIn: 'root' })
export class TareaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/tareas`;

  private catalogosCache$?: Observable<TareaCatalogos>;
  private catalogosCacheAt = 0;
  private readonly catalogosTtlMs = 5 * 60 * 1000;

  listar(f: TareaFiltro = {}, page = 0, size = 24): Observable<TareaPagina> {
    let p = this.paramsFiltro(f)
      .set('page', String(Math.max(0, page)))
      .set('size', String(Math.min(100, Math.max(1, size))));

    return this.http.get<TareaPagina>(this.base, { params: p });
  }

  listarParaExportar(f: TareaFiltro = {}): Observable<TareaExcelFila[]> {
    return this.http.get<TareaExcelFila[]>(`${this.base}/exportacion`, {
      params: this.paramsFiltro(f)
    });
  }

  resumen(): Observable<TareaResumen> {
    return this.http.get<TareaResumen>(`${this.base}/resumen`);
  }

  catalogos(forzar = false): Observable<TareaCatalogos> {
    const ahora = Date.now();
    if (
      forzar ||
      !this.catalogosCache$ ||
      ahora - this.catalogosCacheAt > this.catalogosTtlMs
    ) {
      this.catalogosCacheAt = ahora;
      this.catalogosCache$ = this.http
        .get<TareaCatalogos>(`${this.base}/catalogos`)
        .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    }
    return this.catalogosCache$;
  }

  detalle(uuid: string): Observable<TareaDetalle> {
    return this.http.get<TareaDetalle>(`${this.base}/${uuid}`);
  }

  crear(d: TareaGuardar): Observable<TareaDetalle> {
    return this.http.post<TareaDetalle>(this.base, d);
  }

  actualizar(uuid: string, d: TareaGuardar): Observable<TareaDetalle> {
    return this.http.put<TareaDetalle>(`${this.base}/${uuid}`, d);
  }

  cambiarFlujo(uuid: string, etapa: EtapaTarea, observacion = ''): Observable<TareaDetalle> {
    return this.http.patch<TareaDetalle>(`${this.base}/${uuid}/flujo`, {
      etapa,
      observacion
    });
  }

  toggleSubtarea(uuid: string, id: number): Observable<TareaDetalle> {
    return this.http.patch<TareaDetalle>(`${this.base}/${uuid}/subtareas/${id}`, {});
  }

  comentar(uuid: string, texto: string): Observable<TareaDetalle> {
    return this.http.post<TareaDetalle>(`${this.base}/${uuid}/comentarios`, { texto });
  }

  registrarTiempo(uuid: string, tiempoRealMinutos: number | null): Observable<TareaDetalle> {
    return this.http.patch<TareaDetalle>(`${this.base}/${uuid}/tiempo`, {
      tiempoRealMinutos
    });
  }

  private paramsFiltro(f: TareaFiltro): HttpParams {
    let p = new HttpParams();
    Object.entries(f).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') {
        p = p.set(k, String(v));
      }
    });
    return p;
  }
}
