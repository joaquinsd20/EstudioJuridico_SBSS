import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  BibliotecaEstadisticas,
  FiltrosBiblioteca,
  Pagina,
  RecursoJuridico
} from '../models/biblioteca-juridica.models';

@Injectable({ providedIn: 'root' })
export class BibliotecaJuridicaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/biblioteca`;

  listar(
    filtros: FiltrosBiblioteca,
    pagina = 0,
    tamano = 20
  ): Observable<Pagina<RecursoJuridico>> {
    let params = new HttpParams()
      .set('page', pagina)
      .set('size', tamano);

    if (filtros.busqueda.trim()) params = params.set('busqueda', filtros.busqueda.trim());
    if (filtros.tipo) params = params.set('tipo', filtros.tipo);
    if (filtros.categoria) params = params.set('categoria', filtros.categoria);
    if (filtros.estado) params = params.set('estado', filtros.estado);
    if (filtros.conArchivo) params = params.set('conArchivo', filtros.conArchivo);
    if (filtros.anio) params = params.set('anio', filtros.anio);

    return this.http.get<Pagina<RecursoJuridico>>(
      `${this.apiUrl}/recursos`,
      { params }
    );
  }

  estadisticas(): Observable<BibliotecaEstadisticas> {
    return this.http.get<BibliotecaEstadisticas>(`${this.apiUrl}/estadisticas`);
  }

  obtener(uuid: string): Observable<RecursoJuridico> {
    return this.http.get<RecursoJuridico>(`${this.apiUrl}/recursos/${uuid}`);
  }

  crear(datos: FormData): Observable<RecursoJuridico> {
    return this.http.post<RecursoJuridico>(`${this.apiUrl}/recursos`, datos);
  }

  actualizar(uuid: string, datos: FormData): Observable<RecursoJuridico> {
    return this.http.put<RecursoJuridico>(`${this.apiUrl}/recursos/${uuid}`, datos);
  }

  ver(uuid: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/recursos/${uuid}/ver`, {
      responseType: 'blob'
    });
  }

  descargar(uuid: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/recursos/${uuid}/descargar`, {
      responseType: 'blob'
    });
  }

  eliminar(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/recursos/${uuid}`);
  }
}
