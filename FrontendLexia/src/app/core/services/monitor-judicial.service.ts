import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AgregarMonitorRequest,
  ConsultaMonitor,
  ConsultaMonitorFiltro,
  ExpedienteMonitor,
  MonitorCatalogos,
  MonitorFiltro,
  MonitorResumen,
  PaginaMonitor,
  RegistrarConsultaMonitorRequest
} from '../models/monitor-judicial.models';

@Injectable({ providedIn: 'root' })
export class MonitorJudicialService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/monitor-judicial`;

  /** Compatibilidad con llamadas antiguas que esperan un array. */
  listar(filtro: MonitorFiltro = {}, limite = 250, desplazamiento = 0): Observable<ExpedienteMonitor[]> {
    let params = this.paramsMonitor(filtro)
      .set('limite', String(limite))
      .set('desplazamiento', String(desplazamiento));

    return this.http.get<ExpedienteMonitor[]>(this.base, { params });
  }

  listarPagina(
    filtro: MonitorFiltro = {},
    pagina = 0,
    tamano = 20
  ): Observable<PaginaMonitor<ExpedienteMonitor>> {
    const params = this.paramsMonitor(filtro)
      .set('pagina', String(pagina))
      .set('tamano', String(tamano));

    return this.http.get<PaginaMonitor<ExpedienteMonitor>>(
      `${this.base}/pagina`,
      { params }
    );
  }

  resumen(): Observable<MonitorResumen> {
    return this.http.get<MonitorResumen>(`${this.base}/resumen`);
  }

  catalogos(busqueda = '', limite = 50): Observable<MonitorCatalogos> {
    let params = new HttpParams().set('limite', String(limite));
    const texto = busqueda.trim();
    if (texto) {
      params = params.set('busqueda', texto);
    }

    return this.http.get<MonitorCatalogos>(`${this.base}/catalogos`, { params });
  }

  /** Compatibilidad con Dashboard y consumidores que esperan un array. */
  consultas(
    filtro: ConsultaMonitorFiltro = {},
    limite = 250,
    desplazamiento = 0
  ): Observable<ConsultaMonitor[]> {
    const params = this.paramsConsultas(filtro)
      .set('limite', String(limite))
      .set('desplazamiento', String(desplazamiento));

    return this.http.get<ConsultaMonitor[]>(`${this.base}/consultas`, { params });
  }

  consultasPagina(
    filtro: ConsultaMonitorFiltro = {},
    pagina = 0,
    tamano = 20
  ): Observable<PaginaMonitor<ConsultaMonitor>> {
    const params = this.paramsConsultas(filtro)
      .set('pagina', String(pagina))
      .set('tamano', String(tamano));

    return this.http.get<PaginaMonitor<ConsultaMonitor>>(
      `${this.base}/consultas/pagina`,
      { params }
    );
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

  private paramsMonitor(filtro: MonitorFiltro): HttpParams {
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

    return params;
  }

  private paramsConsultas(filtro: ConsultaMonitorFiltro): HttpParams {
    let params = new HttpParams();

    if (filtro.expedienteId) {
      params = params.set('expedienteId', String(filtro.expedienteId));
    }
    if (filtro.tipoMovimiento?.trim()) {
      params = params.set('tipoMovimiento', filtro.tipoMovimiento.trim());
    }
    if (filtro.estadoVerificacion?.trim()) {
      params = params.set('estadoVerificacion', filtro.estadoVerificacion.trim());
    }
    if (filtro.desde) {
      params = params.set('desde', filtro.desde);
    }
    if (filtro.hasta) {
      params = params.set('hasta', filtro.hasta);
    }

    return params;
  }
}
