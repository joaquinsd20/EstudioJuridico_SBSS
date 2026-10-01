import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RecursoJuridico, RecursosJuridicosResponse } from '../models/biblioteca-juridica.models';

@Injectable({ providedIn: 'root' })
export class BibliotecaJuridicaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/biblioteca/recursos`;



  listar(): Observable<RecursosJuridicosResponse> {
    return this.http.get<RecursosJuridicosResponse>(this.apiUrl);
  }

  crear(datos: FormData): Observable<RecursoJuridico> {
    return this.http.post<RecursoJuridico>(this.apiUrl, datos);
  }

  ver(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/ver`, { responseType: 'blob' });
  }

  descargar(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/descargar`, { responseType: 'blob' });
  }

  eliminar(id: number): Observable<Object> {
    return this.http.delete(`${this.apiUrl}/${id}`);
  }
}
