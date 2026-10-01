import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HonorarioLista, ResumenBackend, ResumenFacturacion, Opcion, CatalogosFacturacion, RegistroMovimientoDTO, MovimientoResponse, ActualizarHonorarioDTO, PagoHonorarioDetalle, DetalleHonorario } from '../models/facturacion.models';

@Injectable({ providedIn: 'root' })
export class FacturacionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/v1/facturacion';

private obtenerHeaders(): {
    headers: HttpHeaders
  } {

    const token =

      localStorage.getItem('token') ||

      sessionStorage.getItem('token') ||

      '';


    return {

      headers: new HttpHeaders({

        Authorization:
          `Bearer ${token}`,

        'Cache-Control':
          'no-cache',

        Pragma:
          'no-cache'

      })

    };

  }

  resumen(params: HttpParams): Observable<ResumenBackend> {
    return this.http.get<ResumenBackend>(`${this.apiUrl}/resumen`, {

          headers:
            this.obtenerHeaders().headers,

          params

        });
  }

  catalogos(params: HttpParams): Observable<CatalogosFacturacion> {
    return this.http.get<CatalogosFacturacion>(`${this.apiUrl}/catalogos`, {

          headers:
            this.obtenerHeaders().headers,

          params

        });
  }

  listar(params: HttpParams): Observable<HonorarioLista[]> {
    return this.http.get<HonorarioLista[]>(this.apiUrl, {

          params,

          headers:
            this.obtenerHeaders().headers

        });
  }

  registrarMovimiento(datos: RegistroMovimientoDTO): Observable<MovimientoResponse> {
    return this.http.post<MovimientoResponse>(`${this.apiUrl}/movimientos`, datos, this.obtenerHeaders());
  }

  actualizarHonorario(uuid: string, datos: ActualizarHonorarioDTO): Observable<MovimientoResponse> {
    return this.http.put<MovimientoResponse>(`${this.apiUrl}/honorarios/${uuid}`, datos, this.obtenerHeaders());
  }

  detalle(uuid: string, params: HttpParams): Observable<DetalleHonorario> {
    return this.http.get<DetalleHonorario>(`${this.apiUrl}/honorarios/${encodeURIComponent(uuid)}/detalle`, {

          headers:
            this.obtenerHeaders().headers,

          params

        });
  }

  comprobante(uuid: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.apiUrl}/honorarios/${encodeURIComponent(uuid)}/comprobante`, {

          headers:
            this.obtenerHeaders().headers,

          responseType:
            'blob',

          observe:
            'response'

        });
  }
}
