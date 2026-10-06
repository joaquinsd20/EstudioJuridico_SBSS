import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  BibliotecaEstadisticas,
  ExtensionBibliotecaPermitida,
  FiltrosBiblioteca,
  Pagina,
  RecursoJuridico,
  ValidacionArchivoBiblioteca
} from '../models/biblioteca-juridica.models';

@Injectable({ providedIn: 'root' })
export class BibliotecaJuridicaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/biblioteca`;

  /**
   * Restricción del cliente. El backend sigue siendo la autoridad de seguridad
   * y debe volver a validar extensión, firma/contenido y tamaño.
   */
  readonly extensionesPermitidas: ReadonlySet<ExtensionBibliotecaPermitida> =
    new Set<ExtensionBibliotecaPermitida>(['pdf', 'doc', 'docx']);

  readonly acceptArchivos = '.pdf,.doc,.docx';
  readonly maxArchivoBytes = 50 * 1024 * 1024;

  validarArchivo(archivo: File): ValidacionArchivoBiblioteca {
    const extension = this.extensionDe(archivo.name);

    if (!extension || !this.esExtensionPermitida(extension)) {
      return {
        valido: false,
        extension: null,
        mensaje: 'Formato no permitido. Solo se aceptan archivos PDF, DOC y DOCX.'
      };
    }

    if (archivo.size <= 0) {
      return {
        valido: false,
        extension,
        mensaje: 'El archivo está vacío. Selecciona un documento válido.'
      };
    }

    if (archivo.size > this.maxArchivoBytes) {
      return {
        valido: false,
        extension,
        mensaje: 'El archivo supera el límite de 50 MB.'
      };
    }

    const mime = (archivo.type || '').trim().toLowerCase();
    if (mime && !this.mimeCompatible(extension, mime)) {
      return {
        valido: false,
        extension,
        mensaje: 'El tipo informado por el navegador no corresponde al formato seleccionado.'
      };
    }

    return { valido: true, extension, mensaje: null };
  }

  private esExtensionPermitida(extension: string): extension is ExtensionBibliotecaPermitida {
    return this.extensionesPermitidas.has(extension as ExtensionBibliotecaPermitida);
  }

  private extensionDe(nombre: string): string {
    const limpio = (nombre || '').trim().toLowerCase();
    const punto = limpio.lastIndexOf('.');
    return punto >= 0 && punto + 1 < limpio.length
      ? limpio.substring(punto + 1)
      : '';
  }

  private mimeCompatible(extension: ExtensionBibliotecaPermitida, mime: string): boolean {
    const compatibles: Record<ExtensionBibliotecaPermitida, readonly string[]> = {
      pdf: ['application/pdf'],
      doc: [
        'application/msword',
        'application/vnd.ms-word',
        'application/octet-stream'
      ],
      docx: [
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/zip',
        'application/x-zip-compressed',
        'application/octet-stream'
      ]
    };

    return compatibles[extension].includes(mime);
  }

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
