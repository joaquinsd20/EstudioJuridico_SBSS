import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CambiarPasswordRequest, LoginRequest, LoginResponse, UsuarioAutenticado } from '../models/auth.models';
import { PreferenciasUsuarioService } from './preferencias-usuario.service';

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
  private readonly preferencias = inject(PreferenciasUsuarioService);

  private readonly CLAVE_TOKEN = 'lexia.token';
  private readonly CLAVE_USUARIO = 'lexia.usuario';

  /** Usuario en sesion. Es signal para que el sidebar reaccione solo. */
  private readonly _usuario = signal<UsuarioAutenticado | null>(this.leerUsuarioGuardado());
  readonly usuario = this._usuario.asReadonly();

  readonly autenticado = computed(() => this._usuario() !== null);

  /** True mientras la contrasena sea temporal: el backend bloquea todo lo demas. */
  readonly debeCambiarPassword = computed(() => this._usuario()?.debeCambiarPassword ?? false);

  login(credenciales: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, credenciales)
      .pipe(
        tap(respuesta => this.guardarSesion(respuesta)),
        switchMap(respuesta => respuesta.usuario.debeCambiarPassword
          ? of(respuesta)
          : this.cargarPreferencias().pipe(map(() => respuesta))),
      );
  }

  /**
   * Cambia la contrasena del usuario en sesion.
   *
   * El backend responde con un token NUEVO: al cambiarla, todos los anteriores
   * (incluido el que se esta usando) dejan de valer. Por eso se guarda la
   * respuesta igual que en el login; si no, la siguiente peticion daria 401 y
   * el interceptor lo sacaria de la aplicacion justo despues de cambiarla.
   */
  cambiarPassword(peticion: CambiarPasswordRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/cambiar-password`, peticion)
      .pipe(
        tap(respuesta => this.guardarSesion(respuesta)),
        switchMap(respuesta => this.cargarPreferencias().pipe(map(() => respuesta))),
      );
  }

  /**
   * Comprueba contra el backend que el token guardado siga sirviendo. Se ejecuta
   * al arrancar la aplicacion.
   *
   * Sin esto, el guard solo verifica que EXISTA un token, no que sea valido: con
   * un token expirado se entraria a la aplicacion entera con una sesion muerta y
   * cada peticion fallaria despues.
   *
   * De paso refresca los datos del usuario, asi que si el Administrador le cambia
   * el rol a alguien, lo ve al recargar y no cuando expire su token.
   */
  restaurarSesion(): Observable<UsuarioAutenticado | null> {
    if (!this.token) {
      return of(null);
    }

    return this.http.get<UsuarioAutenticado>(`${environment.apiUrl}/auth/yo`).pipe(
      tap(usuario => {
        this._usuario.set(usuario);
        localStorage.setItem(this.CLAVE_USUARIO, JSON.stringify(usuario));
      }),
      switchMap(usuario => usuario.debeCambiarPassword
        ? of(usuario)
        : this.cargarPreferencias().pipe(map(() => usuario))),
      catchError((error: HttpErrorResponse) => {
        // Solo se cierra la sesion si el servidor dice que el token no sirve.
        // Un fallo de red (status 0, backend apagado) no significa que la sesion
        // sea invalida: expulsar al usuario por un corte momentaneo seria peor.
        if (error.status === 401 || error.status === 403) {
          this.cerrarSesion();
        }
        return of(null);
      })
    );
  }

  /** Borra la sesion local. No llama al backend: el token es stateless. */
  cerrarSesion(): void {
    localStorage.removeItem(this.CLAVE_TOKEN);
    localStorage.removeItem(this.CLAVE_USUARIO);
    this._usuario.set(null);
    this.preferencias.restablecer();
  }

  get token(): string | null {
    return localStorage.getItem(this.CLAVE_TOKEN);
  }

  estaAutenticado(): boolean {
    return this.token !== null;
  }

  /**
   * True si el usuario tiene ese codigo de PERMISO (p.ej. "CLIENTE_CREAR").
   * Comprueba contra `permisos`, no contra `roles`: un permiso nunca aparece en
   * `roles`, asi que antes de que existiera este campo esta comprobacion
   * siempre devolvia false.
   */
  tienePermiso(codigo: string): boolean {
    return this._usuario()?.permisos.includes(codigo) ?? false;
  }

  /** True si el usuario tiene ese codigo de ROL (p.ej. "ADMINISTRADOR", "ABOGADO"). */
  tieneRol(codigo: string): boolean {
    return this._usuario()?.roles.includes(codigo) ?? false;
  }

  private cargarPreferencias(): Observable<unknown> {
    // Un fallo de preferencias no debe impedir entrar en LEXIA; el backend sigue
    // aplicando sus permisos independientemente de la personalización visual.
    return this.preferencias.cargar().pipe(catchError(() => of(null)));
  }

  private guardarSesion(respuesta: LoginResponse): void {
    // No reutilizar tema/menú de la cuenta anterior en una sesión diferente.
    this.preferencias.restablecer();
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
