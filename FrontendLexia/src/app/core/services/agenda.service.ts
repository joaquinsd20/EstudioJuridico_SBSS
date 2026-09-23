import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AgendaCatalogos,
  AgendaFiltro,
  AgendaResumen,
  EventoAgenda,
  EventoAgendaActualizar,
  EventoAgendaDetalle,
  EventoAgendaGuardar
} from '../models/agenda.models';

@Injectable({ providedIn: 'root' })
export class AgendaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/agenda`;

  listar(filtro: AgendaFiltro): Observable<EventoAgenda[]> {
    let params = new HttpParams()
      .set('desde', filtro.desde)
      .set('hasta', filtro.hasta);

    if (filtro.texto?.trim()) params = params.set('texto', filtro.texto.trim());
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    if (filtro.estado) params = params.set('estado', filtro.estado);
    if (filtro.expedienteId) params = params.set('expedienteId', String(filtro.expedienteId));
    if (filtro.participanteId) params = params.set('participanteId', String(filtro.participanteId));
    return this.http.get<EventoAgenda[]>(this.base, { params });
  }

  resumen(): Observable<AgendaResumen> {
    return this.http.get<AgendaResumen>(`${this.base}/resumen`);
  }

  catalogos(): Observable<AgendaCatalogos> {
    return this.http.get<AgendaCatalogos>(`${this.base}/catalogos`);
  }

  detalle(uuid: string): Observable<EventoAgendaDetalle> {
    return this.http.get<EventoAgendaDetalle>(`${this.base}/${uuid}`);
  }

  crear(datos: EventoAgendaGuardar): Observable<EventoAgendaDetalle> {
    return this.http.post<EventoAgendaDetalle>(this.base, datos);
  }

  actualizar(uuid: string, datos: EventoAgendaActualizar): Observable<EventoAgendaDetalle> {
    return this.http.put<EventoAgendaDetalle>(`${this.base}/${uuid}`, datos);
  }

  reprogramar(uuid: string, fechaInicio: string, fechaFin: string, motivo: string,
      recordatorioMinutos: number | null): Observable<EventoAgendaDetalle> {
    return this.http.patch<EventoAgendaDetalle>(`${this.base}/${uuid}/reprogramar`, {
      fechaInicio,
      fechaFin,
      motivo,
      recordatorioMinutos
    });
  }

  registrarResultado(uuid: string, resultado: string, verificado: boolean): Observable<EventoAgendaDetalle> {
    return this.http.patch<EventoAgendaDetalle>(`${this.base}/${uuid}/resultado`, { resultado, verificado });
  }

  cancelar(uuid: string): Observable<EventoAgendaDetalle> {
    return this.http.patch<EventoAgendaDetalle>(`${this.base}/${uuid}/cancelar`, {});
  }
}
