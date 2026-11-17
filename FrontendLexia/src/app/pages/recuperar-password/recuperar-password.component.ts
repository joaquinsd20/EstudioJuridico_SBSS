import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FondoLexiaComponent } from '../../core/components/fondo-lexia.component';
import { AuthService } from '../../core/services/auth.service';

type Modo = 'solicitar' | 'restablecer';
type Estado = 'formulario' | 'enviado' | 'validando' | 'enlace-invalido' | 'listo';

/** Debe coincidir con PoliticaPassword.LONGITUD_MINIMA del backend. */
const LONGITUD_MINIMA = 12;
const ESPERA_REENVIO_S = 60;

/**
 * Las dos pantallas de "¿Olvidaste tu contraseña?":
 *  - /recuperar-password: se escribe el correo y se envía el enlace.
 *  - /restablecer-password?token=...: se elige la contraseña nueva.
 * Comparten el aspecto del login (mismo CSS y mismo fondo).
 */
@Component({
  selector: 'app-recuperar-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, FondoLexiaComponent],
  templateUrl: './recuperar-password.component.html',
  styleUrls: ['../login/login.component.css', './recuperar-password.component.css']
})
export class RecuperarPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly ruta = inject(ActivatedRoute);

  readonly modo: Modo = this.ruta.snapshot.data['modo'] === 'restablecer' ? 'restablecer' : 'solicitar';
  private readonly token = this.ruta.snapshot.queryParamMap.get('token') ?? '';

  readonly estado = signal<Estado>(this.modo === 'restablecer' ? 'validando' : 'formulario');
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly verPassword = signal(false);
  readonly esperaReenvio = signal(0);
  readonly anio = new Date().getFullYear();

  private temporizador: ReturnType<typeof setInterval> | null = null;

  readonly formCorreo = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]]
  });

  readonly formPassword = this.fb.nonNullable.group({
    nueva: ['', [Validators.required, Validators.minLength(LONGITUD_MINIMA)]],
    confirmar: ['', [Validators.required]]
  });

  /** Valores en señales para que la lista de requisitos se actualice al escribir. */
  private readonly nueva = signal('');
  private readonly confirmar = signal('');
  readonly requisitos = computed(() => {
    const p = this.nueva();
    return [
      { texto: `Al menos ${LONGITUD_MINIMA} caracteres`, ok: p.length >= LONGITUD_MINIMA },
      { texto: 'No es un mismo carácter repetido', ok: new Set(p).size >= 4 },
      { texto: 'Ambas contraseñas coinciden', ok: p.length > 0 && p === this.confirmar() }
    ];
  });
  readonly passwordLista = computed(() => this.requisitos().every(r => r.ok));

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detenerTemporizador());
    this.formPassword.controls.nueva.valueChanges.subscribe(v => this.nueva.set(v));
    this.formPassword.controls.confirmar.valueChanges.subscribe(v => this.confirmar.set(v));

    if (this.modo === 'restablecer') {
      if (!this.token) {
        this.estado.set('enlace-invalido');
      } else {
        this.auth.validarEnlaceRecuperacion(this.token).subscribe({
          next: r => this.estado.set(r.valido ? 'formulario' : 'enlace-invalido'),
          error: () => this.estado.set('formulario') // si no se pudo comprobar, se intenta al enviar
        });
      }
    }
  }

  enviarEnlace(): void {
    if (this.formCorreo.invalid) {
      this.formCorreo.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.auth.solicitarRecuperacion(this.formCorreo.controls.email.value.trim()).subscribe({
      next: () => {
        this.cargando.set(false);
        this.estado.set('enviado');
        this.iniciarEspera();
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.mensaje(e));
      }
    });
  }

  reenviar(): void {
    if (this.esperaReenvio() > 0) return;
    this.enviarEnlace();
  }

  guardarPassword(): void {
    if (!this.passwordLista()) {
      this.formPassword.markAllAsTouched();
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    this.auth.restablecerPassword(this.token, this.formPassword.controls.nueva.value).subscribe({
      next: () => {
        this.cargando.set(false);
        this.estado.set('listo');
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        if (e.error?.codigo === 'ENLACE_INVALIDO') {
          this.estado.set('enlace-invalido');
        } else {
          this.error.set(this.mensaje(e));
        }
      }
    });
  }

  private iniciarEspera(): void {
    this.detenerTemporizador();
    this.esperaReenvio.set(ESPERA_REENVIO_S);
    this.temporizador = setInterval(() => {
      const quedan = this.esperaReenvio() - 1;
      this.esperaReenvio.set(Math.max(0, quedan));
      if (quedan <= 0) this.detenerTemporizador();
    }, 1000);
  }

  private detenerTemporizador(): void {
    if (this.temporizador) {
      clearInterval(this.temporizador);
      this.temporizador = null;
    }
  }

  private mensaje(e: HttpErrorResponse): string {
    if (e.status === 0) return 'No se pudo conectar con el servidor. Intenta de nuevo.';
    if (e.status >= 502 && e.status <= 504) return 'El servidor se está activando. Espera un momento e intenta de nuevo.';
    return e.error?.mensaje ?? 'Ocurrió un error inesperado. Intenta nuevamente.';
  }
}
