import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { BusquedaRespuesta, TipoBusqueda, ResultadoBusqueda } from '../models/busqueda.models';

@Injectable({ providedIn: 'root' })
export class BusquedaService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  /**
   * Búsqueda global contra todos los tipos: expediente, cliente, contraparte,
   * resolución, actuación, documento y abogado (incluye DNI/RUC).
   */
  buscar(texto: string): Observable<BusquedaRespuesta> {
    const params = new HttpParams().set('texto', texto.trim());
    return this.http
      .get<BusquedaRespuesta>(`${this.base}/busqueda`, { params })
      .pipe(catchError(() => of({ texto, grupos: [], total: 0, parcialPorPermisos: false })));
  }

  /**
   * Búsqueda filtrando a un único tipo. Útil para afinar resultados en la
   * pantalla de resultados expandida.
   */
  porTipo(texto: string, tipo: TipoBusqueda): Observable<ResultadoBusqueda[]> {
    const params = new HttpParams().set('texto', texto.trim()).set('tipo', tipo);
    return this.http
      .get<ResultadoBusqueda[]>(`${this.base}/busqueda/tipo`, { params })
      .pipe(catchError(() => of([])));
  }

  /**
   * Devuelve un observable que reacciona al texto con debounce de 350 ms.
   * Útil para conectar directamente el campo de texto del buscador.
   */
  conDebounce(fuente$: Observable<string>): Observable<BusquedaRespuesta> {
    return fuente$.pipe(
      debounceTime(350),
      distinctUntilChanged(),
      switchMap(texto =>
        texto.trim().length >= 2 ? this.buscar(texto) : of({ texto, grupos: [], total: 0, parcialPorPermisos: false })
      )
    );
  }
}
