import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AgregarMonitorRequest,
  ConsultaMonitor,
  ExpedienteMonitor,
  MonitorCatalogos,
  MonitorFiltro,
  MonitorResumen,
  RegistrarConsultaMonitorRequest
} from '../models/monitor-judicial.models';

@Injectable({ providedIn: 'root' })
export class MonitorJudicialService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/monitor-judicial`;

  listar(filtro: MonitorFiltro = {}): Observable<ExpedienteMonitor[]> {
    let params = new HttpParams();

    if (filtro.expediente?.trim()) {
      params = params.set('expediente', filtro.expediente.trim());
    }
    if (filtro.cliente?.trim()) {
      params = params.set('cliente', filtro.cliente.trim());
    }
    if (filtro.abogado?.trim()) {
      params = params.set('abogado', filtro.abogado.trim());
    }
    if (filtro.organo?.trim()) {
      params = params.set('organo', filtro.organo.trim());
    }
    if (filtro.estadoProcesal?.trim()) {
      params = params.set('estadoProcesal', filtro.estadoProcesal.trim());
    }
    if (filtro.desde) {
      params = params.set('desde', filtro.desde);
    }
    if (filtro.hasta) {
      params = params.set('hasta', filtro.hasta);
    }

    return this.http.get<ExpedienteMonitor[]>(this.base, { params });
  }

  resumen(): Observable<MonitorResumen> {
    return this.http.get<MonitorResumen>(`${this.base}/resumen`);
  }

  catalogos(): Observable<MonitorCatalogos> {
    return this.http.get<MonitorCatalogos>(`${this.base}/catalogos`);
  }

  consultas(expedienteId?: number): Observable<ConsultaMonitor[]> {
    let params = new HttpParams();
    if (expedienteId) {
      params = params.set('expedienteId', String(expedienteId));
    }

    return this.http.get<ConsultaMonitor[]>(`${this.base}/consultas`, { params });
  }

  agregar(request: AgregarMonitorRequest): Observable<{ uuid: string }> {
    return this.http.post<{ uuid: string }>(this.base, request);
  }

  registrarConsulta(request: RegistrarConsultaMonitorRequest): Observable<{ uuid: string }> {
    return this.http.post<{ uuid: string }>(`${this.base}/consultas`, request);
  }

  verificar(
    uuid: string,
    estadoVerificacion: 'VERIFICADA' | 'RECHAZADA'
  ): Observable<void> {
    return this.http.patch<void>(
      `${this.base}/consultas/${uuid}/verificacion`,
      { estadoVerificacion }
    );
  }

  quitar(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${uuid}`);
  }
}
