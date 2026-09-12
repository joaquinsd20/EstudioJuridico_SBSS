import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, UsuarioAutenticado } from '../models/auth.models';

/**
 * Sesion del usuario: iniciar sesion, guardarla y cerrarla.
 *
 * NOTA SOBRE EL ALMACENAMIENTO DEL TOKEN
 * --------------------------------------
 * El token se guarda en localStorage, que es lo habitual en una SPA y sobrevive
 * al refrescar la pagina. Tiene una debilidad conocida: cualquier script que
 * llegue a ejecutarse en la pagina puede leerlo (XSS). La alternativa solida es
 * una cookie httpOnly, que JavaScript no puede leer, pero exige cambios en el
 * backend y proteccion CSRF.
 *
 * Para un sistema con expedientes judiciales conviene migrar a cookie httpOnly
 * antes de manejar datos reales del estudio. Queda anotado a proposito: no es
 * que localStorage sea la decision correcta, es la decision provisional.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http = inject(HttpClient);

  private readonly CLAVE_TOKEN = 'lexia.token';
  private readonly CLAVE_USUARIO = 'lexia.usuario';

  /** Usuario en sesion. Es signal para que el sidebar reaccione solo. */
  private readonly _usuario = signal<UsuarioAutenticado | null>(this.leerUsuarioGuardado());
  readonly usuario = this._usuario.asReadonly();

  readonly autenticado = computed(() => this._usuario() !== null);

  login(credenciales: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, credenciales)
      .pipe(tap(respuesta => this.guardarSesion(respuesta)));
  }

  /** Borra la sesion local. No llama al backend: el token es stateless. */
  cerrarSesion(): void {
    localStorage.removeItem(this.CLAVE_TOKEN);
    localStorage.removeItem(this.CLAVE_USUARIO);
    this._usuario.set(null);
  }

  get token(): string | null {
    return localStorage.getItem(this.CLAVE_TOKEN);
  }

  estaAutenticado(): boolean {
    return this.token !== null;
  }

  /** True si el usuario tiene ese codigo de rol o de permiso. */
  tienePermiso(codigo: string): boolean {
    return this._usuario()?.roles.includes(codigo) ?? false;
  }

  private guardarSesion(respuesta: LoginResponse): void {
    localStorage.setItem(this.CLAVE_TOKEN, respuesta.token);
    localStorage.setItem(this.CLAVE_USUARIO, JSON.stringify(respuesta.usuario));
    this._usuario.set(respuesta.usuario);
  }

  private leerUsuarioGuardado(): UsuarioAutenticado | null {
    const guardado = localStorage.getItem(this.CLAVE_USUARIO);
    if (!guardado) {
      return null;
    }
    try {
      return JSON.parse(guardado) as UsuarioAutenticado;
    } catch {
      // localStorage manipulado o de una version anterior del formato.
      localStorage.removeItem(this.CLAVE_USUARIO);
      return null;
    }
  }
}
