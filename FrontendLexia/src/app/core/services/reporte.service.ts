import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface KpiReporte {
  totalExpedientes: number;
  expedientesEnTramite: number;
  expedientesVencidos: number;
  expedientesConcluidos: number;
  ingresosTotales: number;
  cobradoTotal: number;
  saldoPendiente: number;
  tareasPendientes: number;
  tiempoPromedioMeses: number;
  horasFacturables: number;
}

export interface FiltrosReporte {
  fechaInicio?: string;
  fechaFin?: string;
  materia?: string;
  abogadoId?: number;
  rangoPreset?: string;
}

export interface EtiquetaReporte {
  nombre: string;
  total: number;
}

export interface BarraReporte {
  etiqueta: string;
  valor: number;
  color: string;
  detalle?: string;
}

export interface ItemReporteDetalle {
  id: number;
  codigo: string;
  titulo: string;
  cliente: string;
  contraparte: string;
  materia: string;
  abogado: string;
  estado: string;
  nivelRiesgo: string;
  fechaInicio: string;
  cuantia: number;
}

export interface ResumenReporteBackend {
  filtros: FiltrosReporte;
  kpis: KpiReporte;
  expedientesPorEstado: EtiquetaReporte[];
  expedientesPorMateria: EtiquetaReporte[];
  expedientesPorAbogado: BarraReporte[];
  cargaPorUsuario: BarraReporte[];
  tareasPorUsuario: BarraReporte[];
  ingresosPorMes: BarraReporte[];
  tablaDetalle: ItemReporteDetalle[];
}

@Injectable({
  providedIn: 'root'
})
export class ReporteService {

  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/reportes`;

  /** Catalogo de materias y abogados reutilizado de expedientes: evita
   *  mantener listas de nombres escritas a mano en el reporte. */
  readonly baseUrlCatalogo = `${environment.apiUrl}/expedientes/catalogos`;

  getResumen(filtros: { fechaInicio?: string; fechaFin?: string; materia?: string; abogadoId?: number; preset?: string }): Observable<ResumenReporteBackend | null> {
    let params = new HttpParams();
    if (filtros.fechaInicio) params = params.set('fechaInicio', filtros.fechaInicio);
    if (filtros.fechaFin) params = params.set('fechaFin', filtros.fechaFin);
    if (filtros.materia && filtros.materia !== 'todas') params = params.set('materia', filtros.materia);
    if (filtros.abogadoId) params = params.set('abogadoId', filtros.abogadoId.toString());
    if (filtros.preset) params = params.set('preset', filtros.preset);

    return this.http.get<ResumenReporteBackend>(`${this.baseUrl}/resumen`, { params }).pipe(
      catchError(() => of(null))
    );
  }

  descargarExcel(filtros: { fechaInicio?: string; fechaFin?: string; materia?: string; abogadoId?: number }): Observable<Blob | null> {
    let params = new HttpParams();
    if (filtros.fechaInicio) params = params.set('fechaInicio', filtros.fechaInicio);
    if (filtros.fechaFin) params = params.set('fechaFin', filtros.fechaFin);
    if (filtros.materia && filtros.materia !== 'todas') params = params.set('materia', filtros.materia);
    if (filtros.abogadoId) params = params.set('abogadoId', filtros.abogadoId.toString());

    return this.http.get(`${this.baseUrl}/exportar/excel`, {
      params,
      responseType: 'blob'
    }).pipe(
      catchError(() => of(null))
    );
  }

  descargarPdf(filtros: { fechaInicio?: string; fechaFin?: string; materia?: string; abogadoId?: number }): Observable<Blob | null> {
    let params = new HttpParams();
    if (filtros.fechaInicio) params = params.set('fechaInicio', filtros.fechaInicio);
    if (filtros.fechaFin) params = params.set('fechaFin', filtros.fechaFin);
    if (filtros.materia && filtros.materia !== 'todas') params = params.set('materia', filtros.materia);
    if (filtros.abogadoId) params = params.set('abogadoId', filtros.abogadoId.toString());

    return this.http.get(`${this.baseUrl}/exportar/pdf`, {
      params,
      responseType: 'blob'
    }).pipe(
      catchError(() => of(null))
    );
  }
}
