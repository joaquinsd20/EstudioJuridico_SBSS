import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CatalogosCliente, ClienteGuardar, ClienteResumen, ClientesResumen,
  FichaCliente, Pagina, RegistrarComunicacion, VerificarConflictoRequest,
  VerificarConflictoResponse
} from '../models/cliente.models';

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/clientes`;

  listar(filtros: { documento?: string; nombre?: string; tipoPersona?: string; estado?: string }, pagina = 0, tamano = 20): Observable<Pagina<ClienteResumen>> {
    let params = new HttpParams().set('page', pagina).set('size', tamano).set('sort', 'nombreBusqueda,asc');
    Object.entries(filtros).forEach(([k, v]) => { if (v) params = params.set(k, v); });
    return this.http.get<Pagina<ClienteResumen>>(this.base, { params });
  }

  resumen(): Observable<ClientesResumen> { return this.http.get<ClientesResumen>(`${this.base}/resumen`); }
  catalogos(): Observable<CatalogosCliente> { return this.http.get<CatalogosCliente>(`${this.base}/catalogos`); }
  ficha(uuid: string): Observable<FichaCliente> { return this.http.get<FichaCliente>(`${this.base}/${uuid}/ficha`); }
  crear(datos: ClienteGuardar): Observable<{ uuid: string }> { return this.http.post<{ uuid: string }>(this.base, datos); }
  actualizar(uuid: string, datos: ClienteGuardar): Observable<void> { return this.http.put<void>(`${this.base}/${uuid}`, datos); }
  verificarConflicto(datos: VerificarConflictoRequest): Observable<VerificarConflictoResponse> {
    return this.http.post<VerificarConflictoResponse>(`${this.base}/conflictos/verificar`, datos);
  }
  registrarComunicacion(uuid: string, datos: RegistrarComunicacion): Observable<{ uuid: string }> {
    return this.http.post<{ uuid: string }>(`${this.base}/${uuid}/comunicaciones`, datos);
  }
}
