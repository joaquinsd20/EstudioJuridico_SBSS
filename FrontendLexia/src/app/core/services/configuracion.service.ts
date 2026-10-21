import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface EstudioConfiguracion {
  id: number;
  razonSocial: string;
  nombreComercial: string | null;
  ruc: string;
  direccion: string | null;
  telefono: string | null;
  email: string | null;
  logoUrl: string | null;
  zonaHoraria: string;
  monedaBase: string;
}

export interface ActualizarEstudioConfiguracion {
  razonSocial: string;
  nombreComercial: string | null;
  ruc: string;
  direccion: string | null;
  telefono: string | null;
  email: string | null;
  zonaHoraria: string;
  monedaBase: string;
}

export interface ParametrosEstudio {
  dominioCorreoInterno: string;
  intentosLoginMaximos: number;
  plazoColchonInternoDias: number;
  alertasDiasPrevios: number[];
}

export interface RolConfiguracion {
  codigo: string;
  nombre: string;
  nivelAcceso: number;
  esPortalCliente: boolean;
}

@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/configuracion`;
  private readonly tiempoMaximoPeticionMs = 8_000;

  obtenerEstudio(): Observable<EstudioConfiguracion> {
    return this.http.get<EstudioConfiguracion>(`${this.base}/estudio`).pipe(timeout({ first: this.tiempoMaximoPeticionMs }));
  }

  actualizarEstudio(request: ActualizarEstudioConfiguracion): Observable<EstudioConfiguracion> {
    return this.http.put<EstudioConfiguracion>(`${this.base}/estudio`, request).pipe(timeout({ first: this.tiempoMaximoPeticionMs }));
  }

  obtenerParametros(): Observable<ParametrosEstudio> {
    return this.http.get<ParametrosEstudio>(`${this.base}/parametros`).pipe(timeout({ first: this.tiempoMaximoPeticionMs }));
  }

  actualizarParametros(request: ParametrosEstudio): Observable<ParametrosEstudio> {
    return this.http.put<ParametrosEstudio>(`${this.base}/parametros`, request).pipe(timeout({ first: this.tiempoMaximoPeticionMs }));
  }

  obtenerRolesAsignables(): Observable<RolConfiguracion[]> {
    return this.http.get<RolConfiguracion[]>(`${environment.apiUrl}/usuarios/roles`).pipe(timeout({ first: this.tiempoMaximoPeticionMs }));
  }
}
