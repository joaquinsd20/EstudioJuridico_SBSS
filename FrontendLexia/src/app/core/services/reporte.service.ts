import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
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

export interface PaginaReporte<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  esUltima: boolean;
}

export interface OpcionReporte {
  id: number;
  nombre: string;
}

export interface CatalogosReporte {
  materias: OpcionReporte[];
  abogados: OpcionReporte[];
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

@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/reportes`;

  private readonly catalogos$ = this.http
    .get<CatalogosReporte>(`${this.baseUrl}/catalogos`)
    .pipe(shareReplay({ bufferSize: 1, refCount: true }));

  getResumen(filtros: {
    fechaInicio?: string;
    fechaFin?: string;
    materia?: string;
    abogadoId?: number;
    preset?: string;
  }): Observable<ResumenReporteBackend> {
    let params = this.paramsBase(filtros);
    if (filtros.preset) params = params.set('preset', filtros.preset);
    return this.http.get<ResumenReporteBackend>(`${this.baseUrl}/resumen`, { params });
  }

  getDetalle(
    filtros: { fechaInicio?: string; fechaFin?: string; materia?: string; abogadoId?: number },
    pagina: number,
    tamano: number,
    texto = ''
  ): Observable<PaginaReporte<ItemReporteDetalle>> {
    let params = this.paramsBase(filtros)
      .set('page', Math.max(0, pagina).toString())
      .set('size', tamano.toString());
    if (texto.trim()) params = params.set('texto', texto.trim());
    return this.http.get<PaginaReporte<ItemReporteDetalle>>(`${this.baseUrl}/detalle`, { params });
  }

  getCatalogos(): Observable<CatalogosReporte> {
    return this.catalogos$;
  }

  getDatosExportacion(filtros: {
    fechaInicio?: string;
    fechaFin?: string;
    materia?: string;
    abogadoId?: number;
  }): Observable<ItemReporteDetalle[]> {
    return this.http.get<ItemReporteDetalle[]>(`${this.baseUrl}/exportar/datos`, {
      params: this.paramsBase(filtros)
    });
  }

  descargarExcel(filtros: {
    fechaInicio?: string;
    fechaFin?: string;
    materia?: string;
    abogadoId?: number;
  }): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/exportar/excel`, {
      params: this.paramsBase(filtros),
      responseType: 'blob'
    });
  }

  descargarPdf(filtros: {
    fechaInicio?: string;
    fechaFin?: string;
    materia?: string;
    abogadoId?: number;
  }): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/exportar/pdf`, {
      params: this.paramsBase(filtros),
      responseType: 'blob'
    });
  }

  private paramsBase(filtros: {
    fechaInicio?: string;
    fechaFin?: string;
    materia?: string;
    abogadoId?: number;
  }): HttpParams {
    let params = new HttpParams();
    if (filtros.fechaInicio) params = params.set('fechaInicio', filtros.fechaInicio);
    if (filtros.fechaFin) params = params.set('fechaFin', filtros.fechaFin);
    if (filtros.materia && filtros.materia !== 'todas') params = params.set('materia', filtros.materia);
    if (filtros.abogadoId) params = params.set('abogadoId', filtros.abogadoId.toString());
    return params;
  }
}
