import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TipoEscrito {
  codigo: string;
  nombre: string;
  descripcion: string;
}

export interface GenerarEscritoRequest {
  expedienteUuid: string;
  tipoEscrito: string;
  instrucciones?: string;
}

export interface ExportarEscritoRequest {
  titulo: string;
  contenido: string;
}

export interface EscritoGenerado {
  titulo: string;
  contenido: string;
  modelo: string;
  generadoConIa: boolean;
  expedienteUuid: string;
}

@Injectable({ providedIn: 'root' })
export class EscritosIaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/escritos-ia`;

  tipos(): Observable<TipoEscrito[]> {
    return this.http.get<TipoEscrito[]>(`${this.baseUrl}/tipos`).pipe(catchError(() => of([])));
  }

  generar(req: GenerarEscritoRequest): Observable<EscritoGenerado> {
    return this.http.post<EscritoGenerado>(`${this.baseUrl}/generar`, req);
  }

  exportarWord(req: ExportarEscritoRequest): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/exportar/word`, req, { responseType: 'blob' });
  }

  exportarPdf(req: ExportarEscritoRequest): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/exportar/pdf`, req, { responseType: 'blob' });
  }
}
