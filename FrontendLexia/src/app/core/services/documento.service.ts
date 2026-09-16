import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Documento,
  DocumentoActualizar,
  DocumentoContenido,
  DocumentoCrear,
  DocumentoEstadisticas,
  DocumentoFiltro,
  DocumentoVersion,
  ExpedienteOpcion,
  RelacionOpcion,
  UsuarioOpcion
} from '../models/documento.models';

@Injectable({ providedIn: 'root' })
export class DocumentoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/documentos`;

  listar(filtros: DocumentoFiltro = {}): Observable<Documento[]> {
    let params = new HttpParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(clave, String(valor));
      }
    });
    return this.http.get<Documento[]>(this.base, { params });
  }

  obtener(uuid: string): Observable<Documento> {
    return this.http.get<Documento>(`${this.base}/${uuid}`);
  }

  crear(datos: DocumentoCrear, archivo: File): Observable<Documento> {
    const form = new FormData();
    form.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
    form.append('archivo', archivo);
    return this.http.post<Documento>(this.base, form).pipe(timeout(60000));
  }

  actualizar(uuid: string, datos: DocumentoActualizar): Observable<Documento> {
    return this.http.put<Documento>(`${this.base}/${uuid}`, datos).pipe(timeout(60000));
  }

  estadisticas(): Observable<DocumentoEstadisticas> {
    return this.http.get<DocumentoEstadisticas>(`${this.base}/estadisticas`);
  }

  recientes(limite = 6): Observable<Documento[]> {
    return this.http.get<Documento[]>(`${this.base}/recientes`, {
      params: new HttpParams().set('limite', String(limite))
    });
  }

  papelera(): Observable<Documento[]> {
    return this.http.get<Documento[]>(`${this.base}/papelera`);
  }

  enviarPapelera(uuid: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${uuid}/papelera`, {}).pipe(timeout(60000));
  }

  restaurar(uuid: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${uuid}/restaurar`, {}).pipe(timeout(60000));
  }

  eliminarDefinitivamente(uuid: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${uuid}`).pipe(timeout(60000));
  }

  versiones(uuid: string): Observable<DocumentoVersion[]> {
    return this.http.get<DocumentoVersion[]>(`${this.base}/${uuid}/versiones`);
  }

  subirVersion(uuid: string, archivo: File, observaciones = ''): Observable<DocumentoVersion> {
    const form = new FormData();
    form.append('archivo', archivo);
    if (observaciones.trim()) form.append('observaciones', observaciones.trim());
    return this.http.post<DocumentoVersion>(`${this.base}/${uuid}/versiones`, form).pipe(timeout(60000));
  }

  restaurarVersion(uuid: string, versionId: number, observaciones = ''): Observable<DocumentoVersion> {
    let params = new HttpParams();
    if (observaciones.trim()) params = params.set('observaciones', observaciones.trim());
    return this.http.post<DocumentoVersion>(
      `${this.base}/${uuid}/versiones/${versionId}/restaurar`,
      {},
      { params }
    ).pipe(timeout(60000));
  }

  contenido(uuid: string): Observable<DocumentoContenido> {
    return this.http.get<DocumentoContenido>(`${this.base}/${uuid}/contenido`);
  }

  visualizar(uuid: string): Observable<Blob> {
    return this.http.get(`${this.base}/${uuid}/visualizar`, { responseType: 'blob' });
  }

  descargar(uuid: string): Observable<Blob> {
    return this.http.get(`${this.base}/${uuid}/descargar`, { responseType: 'blob' });
  }

  exportar(uuid: string, formato: 'pdf' | 'docx'): Observable<Blob> {
    return this.http.get(`${this.base}/${uuid}/exportar`, {
      params: new HttpParams().set('formato', formato),
      responseType: 'blob'
    });
  }

  expedientes(): Observable<ExpedienteOpcion[]> {
    return this.http.get<ExpedienteOpcion[]>(`${this.base}/catalogos/expedientes`);
  }

  creadores(): Observable<UsuarioOpcion[]> {
    return this.http.get<UsuarioOpcion[]>(`${this.base}/catalogos/creadores`);
  }

  actuaciones(expedienteId: number): Observable<RelacionOpcion[]> {
    return this.http.get<RelacionOpcion[]>(
      `${this.base}/catalogos/expedientes/${expedienteId}/actuaciones`
    );
  }

  tareas(expedienteId: number): Observable<RelacionOpcion[]> {
    return this.http.get<RelacionOpcion[]>(
      `${this.base}/catalogos/expedientes/${expedienteId}/tareas`
    );
  }
}
