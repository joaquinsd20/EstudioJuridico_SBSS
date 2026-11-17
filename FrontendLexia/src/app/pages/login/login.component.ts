import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { FondoLexiaComponent } from '../../core/components/fondo-lexia.component';
import { AuthService } from '../../core/services/auth.service';
import { ErrorRespuesta } from '../../core/models/auth.models';
import { environment } from '../../../environments/environment';

/** Tras este tiempo sin respuesta se avisa que el servidor se esta activando. */
const AVISO_SERVIDOR_LENTO_MS = 6000;

/** Solo se guarda el correo (nunca la contrasena) y solo si la persona lo pide. */
const CLAVE_CORREO = 'lexia.correoRecordado';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, FondoLexiaComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly servidorLento = signal(false);
  readonly verPassword = signal(false);
  readonly mayusculas = signal(false);
  readonly recordarCorreo = signal(false);
  readonly anio = new Date().getFullYear();

  private temporizadorAviso: ReturnType<typeof setTimeout> | null = null;

  readonly formulario = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  constructor() {
    // En el hosting gratuito el backend se apaga tras un rato sin uso. Esta
    // peticion lo despierta apenas se abre el login, mientras la persona
    // escribe su correo. La respuesta (un 401 sin sesion) no importa, y va con
    // fetch para no pasar por el interceptor, que reaccionaria a ese 401.
    fetch(`${environment.apiUrl}/auth/yo`, { method: 'GET' }).catch(() => undefined);
    inject(DestroyRef).onDestroy(() => this.limpiarAviso());

    const recordado = this.leerCorreoRecordado();
    if (recordado) {
      this.formulario.controls.email.setValue(recordado);
      this.recordarCorreo.set(true);
    }
  }

  /** Avisa si Bloq Mayús está activo mientras se escribe la contraseña. */
  revisarMayusculas(evento: KeyboardEvent): void {
    if (typeof evento.getModifierState === 'function') {
      this.mayusculas.set(evento.getModifierState('CapsLock'));
    }
  }

  private leerCorreoRecordado(): string | null {
    try { return localStorage.getItem(CLAVE_CORREO); } catch { return null; }
  }

  private guardarCorreoRecordado(): void {
    try {
      if (this.recordarCorreo()) {
        localStorage.setItem(CLAVE_CORREO, this.formulario.controls.email.value.trim());
      } else {
        localStorage.removeItem(CLAVE_CORREO);
      }
    } catch {
      // Almacenamiento bloqueado: simplemente no se recuerda.
    }
  }

  entrar(): void {
    if (this.formulario.invalid) {
      // Marca los campos para que se vean los mensajes de validacion.
      this.formulario.markAllAsTouched();
      return;
    }

    this.cargando.set(true);
    this.error.set(null);
    this.limpiarAviso();
    this.temporizadorAviso = setTimeout(() => this.servidorLento.set(true), AVISO_SERVIDOR_LENTO_MS);

    this.auth.login(this.formulario.getRawValue()).subscribe({
      // Con contrasena temporal (alta nueva o restablecida) lo primero es
      // cambiarla: el backend no deja hacer nada mas hasta entonces.
      next: respuesta => {
        this.limpiarAviso();
        this.guardarCorreoRecordado();
        this.router.navigate(
          [respuesta.usuario.debeCambiarPassword ? '/cambiar-password' : '/dashboard']);
      },
      error: (respuesta: HttpErrorResponse) => {
        this.limpiarAviso();
        this.cargando.set(false);
        this.error.set(this.mensajeDeError(respuesta));
      }
    });
  }

  private limpiarAviso(): void {
    if (this.temporizadorAviso) {
      clearTimeout(this.temporizadorAviso);
      this.temporizadorAviso = null;
    }
    this.servidorLento.set(false);
  }

  private mensajeDeError(respuesta: HttpErrorResponse): string {
    // status 0 = la peticion no llego al servidor (backend apagado, CORS, red).
    if (respuesta.status === 0) {
      return 'No se pudo conectar con el servidor. Verifica que el backend este encendido.';
    }

    // 502/503/504: el proxy se canso de esperar mientras el servidor arrancaba.
    if (respuesta.status >= 502 && respuesta.status <= 504) {
      return 'El servidor todavia se esta activando. Espera un momento e intenta de nuevo.';
    }

    const cuerpo = respuesta.error as ErrorRespuesta | null;
    return cuerpo?.mensaje ?? 'Ocurrio un error inesperado. Intenta nuevamente.';
  }
}
