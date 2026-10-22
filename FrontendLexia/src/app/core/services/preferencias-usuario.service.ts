import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TemaService } from './tema.service';

export type TemaPersonal = 'CLARO' | 'OSCURO';

export interface PreferenciasUsuario {
  tema: TemaPersonal;
  sidebarColapsado: boolean;
}

const PREDETERMINADAS: PreferenciasUsuario = {
  tema: 'CLARO',
  sidebarColapsado: false,
};

/** Mantiene la interfaz estable mientras se consulta la preferencia autoritativa del backend. */
function leerPreferenciasLocales(): PreferenciasUsuario {
  try {
    return {
      tema: localStorage.getItem('lexia.tema') === 'oscuro' ? 'OSCURO' : 'CLARO',
      sidebarColapsado: localStorage.getItem('lexia-sidebar-collapsed') === 'true',
    };
  } catch {
    return { ...PREDETERMINADAS };
  }
}

@Injectable({ providedIn: 'root' })
export class PreferenciasUsuarioService {
  private readonly http = inject(HttpClient);
  private readonly tema = inject(TemaService);
  private readonly url = `${environment.apiUrl}/mi-configuracion`;
  private readonly tiempoMaximoPeticionMs = 8_000;
  private readonly estado = signal<PreferenciasUsuario>(leerPreferenciasLocales());
  readonly preferencias = this.estado.asReadonly();

  cargar(): Observable<PreferenciasUsuario> {
    return this.http.get<PreferenciasUsuario>(this.url).pipe(
      timeout({ first: this.tiempoMaximoPeticionMs }),
      tap(preferencias => this.aplicar(preferencias)),
    );
  }

  guardar(preferencias: PreferenciasUsuario): Observable<PreferenciasUsuario> {
    return this.http.put<PreferenciasUsuario>(this.url, preferencias).pipe(
      timeout({ first: this.tiempoMaximoPeticionMs }),
      tap(guardadas => this.aplicar(guardadas)),
    );
  }

  /** Limpia las preferencias visibles al salir o cambiar de cuenta en este navegador. */
  restablecer(): void {
    this.aplicar({ ...PREDETERMINADAS });
  }

  actualizarTema(tema: TemaPersonal): Observable<PreferenciasUsuario> {
    const siguiente = { ...this.estado(), tema };
    this.aplicar(siguiente);
    return this.http.put<PreferenciasUsuario>(this.url, siguiente).pipe(
      timeout({ first: this.tiempoMaximoPeticionMs }),
      tap(guardadas => this.aplicar(guardadas)),
    );
  }

  actualizarSidebar(sidebarColapsado: boolean): Observable<PreferenciasUsuario> {
    const siguiente = { ...this.estado(), sidebarColapsado };
    this.estado.set(siguiente);
    try { localStorage.setItem('lexia-sidebar-collapsed', String(sidebarColapsado)); } catch { /* solo persistencia local */ }
    return this.http.put<PreferenciasUsuario>(this.url, siguiente).pipe(
      timeout({ first: this.tiempoMaximoPeticionMs }),
      tap(guardadas => this.aplicar(guardadas)),
    );
  }

  /** Actualiza la interfaz local y las claves heredadas que aún leen algunos componentes. */
  private aplicar(preferencias: PreferenciasUsuario): void {
    const normalizadas: PreferenciasUsuario = {
      tema: preferencias.tema === 'OSCURO' ? 'OSCURO' : 'CLARO',
      sidebarColapsado: Boolean(preferencias.sidebarColapsado),
    };
    this.estado.set(normalizadas);
    this.tema.establecer(normalizadas.tema === 'OSCURO');
    try {
      localStorage.setItem('lexia-sidebar-collapsed', String(normalizadas.sidebarColapsado));
    } catch {
      // La preferencia se mantiene en memoria si el navegador bloquea el almacenamiento local.
    }
  }
}
