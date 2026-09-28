import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface EtiquetaGrafico {
  nombre: string;
  valor: number;
}

export interface BarraGrafico {
  nombre: string;
  valor: number;
  color: string;
  detalle: string | null;
}

export interface EstadoCobranza {
  facturado: number;
  cobrado: number;
  pendiente: number;
  vencido: number;
  cuentasPendientes: number;
  cuentasVencidas: number;
}

export interface GraficosDashboard {
  expedientesPorEstado: EtiquetaGrafico[];
  expedientesPorEspecialidad: EtiquetaGrafico[];
  expedientesPorAbogado: BarraGrafico[];
  cargaPorUsuario: BarraGrafico[];
  tareasPorUsuario: BarraGrafico[];
  ingresosPorMes: BarraGrafico[];
  cobranza: EstadoCobranza;
}

const GRAFICOS_VACIOS: GraficosDashboard = {
  expedientesPorEstado: [],
  expedientesPorEspecialidad: [],
  expedientesPorAbogado: [],
  cargaPorUsuario: [],
  tareasPorUsuario: [],
  ingresosPorMes: [],
  cobranza: {
    facturado: 0, cobrado: 0, pendiente: 0,
    vencido: 0, cuentasPendientes: 0, cuentasVencidas: 0
  }
};

@Injectable({ providedIn: 'root' })
export class GraficosDashboardService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * Carga los seis gráficos del dashboard en una sola llamada.
   * El backend garantiza snapshot consistente con @Transactional(readOnly=true).
   */
  graficos(): Observable<GraficosDashboard> {
    return this.http
      .get<GraficosDashboard>(`${this.base}/dashboard/graficos`)
      .pipe(catchError(() => of(GRAFICOS_VACIOS)));
  }
}
