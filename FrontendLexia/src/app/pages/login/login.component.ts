import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ErrorRespuesta } from '../../core/models/auth.models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  entrar(): void {
    if (this.formulario.invalid) {
      // Marca los campos para que se vean los mensajes de validacion.
      this.formulario.markAllAsTouched();
      return;
    }

    this.cargando.set(true);
    this.error.set(null);

    this.auth.login(this.formulario.getRawValue()).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (respuesta: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(this.mensajeDeError(respuesta));
      }
    });
  }

  private mensajeDeError(respuesta: HttpErrorResponse): string {
    // status 0 = la peticion no llego al servidor (backend apagado, CORS, red).
    if (respuesta.status === 0) {
      return 'No se pudo conectar con el servidor. Verifica que el backend este encendido.';
    }

    const cuerpo = respuesta.error as ErrorRespuesta | null;
    return cuerpo?.mensaje ?? 'Ocurrio un error inesperado. Intenta nuevamente.';
  }
}
