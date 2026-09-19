import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CatalogosExpediente,
  EventoLineaTiempo,
  ExpedienteDetalle,
  ExpedienteGuardar,
  ExpedienteLista,
  ExpedienteResumen
} from '../models/expediente.models';

export interface ExpedienteFiltros {
  texto?: string;
  cliente?: string;
  estado?: string;
  materiaId?: number | null;
  organo?: string;
}

@Injectable({ providedIn: 'root' })
export class ExpedienteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/expedientes`;

  listar(filtros: ExpedienteFiltros = {}): Observable<ExpedienteLista[]> {
    let params = new HttpParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(clave, String(valor));
      }
    });
    return this.http.get<ExpedienteLista[]>(this.base, { params });
  }

  resumen(): Observable<ExpedienteResumen> {
    return this.http.get<ExpedienteResumen>(`${this.base}/resumen`);
  }

  catalogos(): Observable<CatalogosExpediente> {
    return this.http.get<CatalogosExpediente>(`${this.base}/catalogos`);
  }

  obtener(uuid: string): Observable<ExpedienteDetalle> {
    return this.http.get<ExpedienteDetalle>(`${this.base}/${uuid}`);
  }

  lineaTiempo(uuid: string): Observable<EventoLineaTiempo[]> {
    return this.http.get<EventoLineaTiempo[]>(`${this.base}/${uuid}/linea-tiempo`);
  }

  crear(datos: ExpedienteGuardar): Observable<{ uuid: string }> {
    return this.http.post<{ uuid: string }>(this.base, datos);
  }

  actualizar(uuid: string, datos: ExpedienteGuardar): Observable<void> {
    return this.http.put<void>(`${this.base}/${uuid}`, datos);
  }
}
