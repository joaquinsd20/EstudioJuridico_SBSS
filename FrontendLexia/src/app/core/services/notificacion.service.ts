import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface Notificacion {
  id: number;
  tipo: string;
  titulo: string;
  mensaje: string;
  /** Valores admitidos por `ck_notificaciones_severidad` (V6__documental.sql:203). */
  severidad: 'INFO' | 'EXITO' | 'ADVERTENCIA' | 'CRITICA';
  entidadOrigen: string | null;
  registroId: number | null;
  expedienteId: number | null;
  expedienteCodigo: string | null;
  urlDestino: string | null;
  leida: boolean;
  fechaCreacion: string;
}

export interface BandejaNotificaciones {
  noLeidas: number;
  items: Notificacion[];
}

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /** Carga la bandeja: contador de no leídas y lista. */
  listar(limite = 30, soloNoLeidas = false): Observable<BandejaNotificaciones> {
    return this.http
      .get<BandejaNotificaciones>(`${this.base}/notificaciones`, {
        params: { limite: limite.toString(), soloNoLeidas: soloNoLeidas.toString() }
      })
      .pipe(catchError(() => of({ noLeidas: 0, items: [] })));
  }

  /** Marca una notificación como leída. */
  marcarLeida(id: number): Observable<Notificacion> {
    return this.http.patch<Notificacion>(`${this.base}/notificaciones/${id}/leer`, {});
  }

  /** Marca todas como leídas. Devuelve cuántas se marcaron. */
  marcarTodasLeidas(): Observable<number> {
    return this.http
      .post<number>(`${this.base}/notificaciones/leer-todas`, {})
      .pipe(catchError(() => of(0)));
  }

  /** Elimina las ya leídas. */
  eliminarLeidas(): Observable<number> {
    return this.http
      .delete<number>(`${this.base}/notificaciones/leidas`)
      .pipe(catchError(() => of(0)));
  }
}
