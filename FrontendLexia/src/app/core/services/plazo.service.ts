import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CalculoPlazo,
  Plazo,
  PlazoCatalogos,
  PlazoDetalle,
  PlazoFiltro,
  PlazoGuardar,
  PlazoRelaciones,
  PlazoResumen,
  TipoComputoPlazo
} from '../models/plazo.models';

@Injectable({ providedIn: 'root' })
export class PlazoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/plazos`;

  listar(filtro: PlazoFiltro): Observable<Plazo[]> {
    let params = new HttpParams();
    if (filtro.texto?.trim()) params = params.set('texto', filtro.texto.trim());
    if (filtro.expedienteId) params = params.set('expedienteId', String(filtro.expedienteId));
    if (filtro.responsableId) params = params.set('responsableId', String(filtro.responsableId));
    if (filtro.tipoPlazoId) params = params.set('tipoPlazoId', String(filtro.tipoPlazoId));
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.riesgo) params = params.set('riesgo', filtro.riesgo);
    if (filtro.computo) params = params.set('computo', filtro.computo);
    if (filtro.requiereRevision !== null && filtro.requiereRevision !== undefined) {
      params = params.set('requiereRevision', String(filtro.requiereRevision));
    }
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<Plazo[]>(this.base, { params });
  }

  resumen(): Observable<PlazoResumen> {
    return this.http.get<PlazoResumen>(`${this.base}/resumen`);
  }

  catalogos(): Observable<PlazoCatalogos> {
    return this.http.get<PlazoCatalogos>(`${this.base}/catalogos`);
  }

  relaciones(expedienteId: number): Observable<PlazoRelaciones> {
    return this.http.get<PlazoRelaciones>(`${this.base}/relaciones/${expedienteId}`);
  }

  detalle(uuid: string): Observable<PlazoDetalle> {
    return this.http.get<PlazoDetalle>(`${this.base}/${uuid}`);
  }

  calcular(expedienteId: number, fechaNotificacion: string, diasPlazo: number,
      tipoComputo: TipoComputoPlazo): Observable<CalculoPlazo> {
    return this.http.post<CalculoPlazo>(`${this.base}/calcular`, {
      expedienteId,
      fechaNotificacion,
      diasPlazo,
      tipoComputo
    });
  }

  crear(datos: PlazoGuardar): Observable<PlazoDetalle> {
    return this.http.post<PlazoDetalle>(this.base, datos);
  }

  actualizar(uuid: string, datos: PlazoGuardar): Observable<PlazoDetalle> {
    return this.http.put<PlazoDetalle>(`${this.base}/${uuid}`, datos);
  }

  cambiarEstado(uuid: string, estado: string): Observable<PlazoDetalle> {
    return this.http.patch<PlazoDetalle>(`${this.base}/${uuid}/estado/${estado}`, {});
  }
}
