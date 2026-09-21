import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ActuacionCrear, ActuacionCrearResponse, ActuacionDetalle, ActuacionFiltros,
  ActuacionLista, ActuacionResumen, CatalogosActuacion, DocumentoActuacionOpcion
} from '../models/actuacion.models';

@Injectable({ providedIn: 'root' })
export class ActuacionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/actuaciones`;

  listar(filtros: ActuacionFiltros = {}): Observable<ActuacionLista[]> {
    let params = new HttpParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') params = params.set(clave, String(valor));
    });
    return this.http.get<ActuacionLista[]>(this.base, { params });
  }

  resumen(): Observable<ActuacionResumen> { return this.http.get<ActuacionResumen>(`${this.base}/resumen`); }
  catalogos(): Observable<CatalogosActuacion> { return this.http.get<CatalogosActuacion>(`${this.base}/catalogos`); }
  detalle(uuid: string): Observable<ActuacionDetalle> { return this.http.get<ActuacionDetalle>(`${this.base}/${uuid}`); }
  documentos(expedienteId: number): Observable<DocumentoActuacionOpcion[]> {
    return this.http.get<DocumentoActuacionOpcion[]>(`${this.base}/catalogos/expedientes/${expedienteId}/documentos`);
  }
  crear(datos: ActuacionCrear): Observable<ActuacionCrearResponse> {
    return this.http.post<ActuacionCrearResponse>(this.base, datos);
  }
}
