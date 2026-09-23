import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CargaDetallada, CredencialTemporal, FiltroEstadoUsuario, GuardarUsuario, ReasignarRequest,
  ResultadoReasignacion, RolOpcion, UsuarioDetalle, UsuarioLista
} from '../models/usuario.models';

/** Personal del estudio. Todo el endpoint exige USUARIO_GESTIONAR. */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/usuarios`;

  listar(filtros: { texto?: string; rol?: string; estado?: FiltroEstadoUsuario } = {}): Observable<UsuarioLista[]> {
    let params = new HttpParams();
    Object.entries(filtros).forEach(([k, v]) => { if (v) params = params.set(k, v); });
    return this.http.get<UsuarioLista[]>(this.base, { params });
  }

  roles(): Observable<RolOpcion[]> { return this.http.get<RolOpcion[]>(`${this.base}/roles`); }
  detalle(uuid: string): Observable<UsuarioDetalle> { return this.http.get<UsuarioDetalle>(`${this.base}/${uuid}`); }
  crear(datos: GuardarUsuario): Observable<CredencialTemporal> { return this.http.post<CredencialTemporal>(this.base, datos); }
  actualizar(uuid: string, datos: GuardarUsuario): Observable<void> { return this.http.put<void>(`${this.base}/${uuid}`, datos); }
  desactivar(uuid: string): Observable<void> { return this.http.post<void>(`${this.base}/${uuid}/desactivar`, {}); }
  activar(uuid: string): Observable<void> { return this.http.post<void>(`${this.base}/${uuid}/activar`, {}); }
  desbloquear(uuid: string): Observable<void> { return this.http.post<void>(`${this.base}/${uuid}/desbloquear`, {}); }
  carga(uuid: string): Observable<CargaDetallada> { return this.http.get<CargaDetallada>(`${this.base}/${uuid}/carga`); }
  reasignar(uuid: string, datos: ReasignarRequest): Observable<ResultadoReasignacion> {
    return this.http.post<ResultadoReasignacion>(`${this.base}/${uuid}/reasignar`, datos);
  }
  restablecerPassword(uuid: string): Observable<CredencialTemporal> {
    return this.http.post<CredencialTemporal>(`${this.base}/${uuid}/restablecer-password`, {});
  }
}
