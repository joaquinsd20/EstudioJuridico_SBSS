import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { ErrorRespuesta } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';

/** Debe coincidir con PoliticaPassword.LONGITUD_MINIMA del backend. */
const LONGITUD_MINIMA = 12;

/** La confirmacion es solo del frontend: al backend le basta con la nueva. */
function confirmacionCoincide(grupo: AbstractControl): ValidationErrors | null {
  const nueva = grupo.get('passwordNueva')?.value;
  const confirmacion = grupo.get('confirmacion')?.value;
  return nueva && confirmacion && nueva !== confirmacion ? { noCoincide: true } : null;
}

/**
 * Cambio de la propia contrasena.
 *
 * Dos modos segun la bandera del usuario:
 * - OBLIGATORIO (contrasena temporal): no hay "Cancelar", porque el backend no
 *   deja hacer nada mas hasta cambiarla; solo se puede cambiar o cerrar sesion.
 * - VOLUNTARIO (desde el panel lateral): se puede volver atras.
 *
 * La politica real la valida el backend; aqui solo se adelanta el minimo de
 * longitud para no hacer un viaje por algo obvio. Los errores del backend se
 * pintan bajo el campo al que se refieren gracias a su `codigo`.
 */
@Component({
  selector: 'app-cambiar-password',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './cambiar-password.component.html',
  // Misma tarjeta que el login: se reutilizan sus estilos en vez de copiarlos.
  styleUrls: ['../login/login.component.css', './cambiar-password.component.css']
})
export class CambiarPasswordComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly longitudMinima = LONGITUD_MINIMA;

  /** Se fija al entrar: tras cambiarla la bandera pasa a false y el texto no debe saltar. */
  readonly obligatorio = this.auth.debeCambiarPassword();

  /** Correo real de la sesión. Se expone al formulario solo para que el gestor de\n   * contraseñas del navegador asocie el cambio a la cuenta correcta. */
  readonly emailSesion = this.auth.usuario()?.email ?? '';

  readonly cargando = signal(false);
  readonly errorActual = signal<string | null>(null);
  readonly errorNueva = signal<string | null>(null);
  readonly errorGeneral = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    passwordActual: ['', [Validators.required]],
    passwordNueva: ['', [Validators.required, Validators.minLength(LONGITUD_MINIMA)]],
    confirmacion: ['', [Validators.required]]
  }, { validators: confirmacionCoincide });

  guardar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.cargando.set(true);
    this.errorActual.set(null);
    this.errorNueva.set(null);
    this.errorGeneral.set(null);

    const { passwordActual, passwordNueva } = this.formulario.getRawValue();

    this.auth.cambiarPassword({ passwordActual, passwordNueva }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (respuesta: HttpErrorResponse) => {
        this.cargando.set(false);
        this.mostrarError(respuesta);
      }
    });
  }

  cancelar(): void {
    // Cancelar no modifica la contrasena ni la sesion: vuelve al espacio
    // desde el que se gestiona esta preferencia personal.
    this.router.navigate(['/mi-configuracion']);
  }

  cerrarSesion(): void {
    this.auth.cerrarSesion();
    this.router.navigate(['/login']);
  }

  private mostrarError(respuesta: HttpErrorResponse): void {
    if (respuesta.status === 0) {
      this.errorGeneral.set('No se pudo conectar con el servidor. Verifica que el backend este encendido.');
      return;
    }

    const cuerpo = respuesta.error as ErrorRespuesta | null;
    const mensaje = cuerpo?.mensaje ?? 'Ocurrio un error inesperado. Intenta nuevamente.';

    switch (cuerpo?.codigo) {
      case 'PASSWORD_ACTUAL_INCORRECTA':
        this.errorActual.set(mensaje);
        break;
      case 'PASSWORD_NO_VALIDA':
      case 'PASSWORD_REPETIDA':
        this.errorNueva.set(mensaje);
        break;
      default:
        this.errorGeneral.set(mensaje);
    }
  }
}
