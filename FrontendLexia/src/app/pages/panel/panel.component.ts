import { Component, computed, inject } from '@angular/core';
import {
  Router,
  RouterLink,
  RouterLinkActive
} from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { TemaService } from '../../core/services/tema.service';

@Component({
  selector: 'app-panel',
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive
  ],
  templateUrl: './panel.component.html',
  styleUrl: './panel.component.css'
})
export class PanelComponent {

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /**
   * Público porque se utiliza directamente desde panel.component.html.
   * Se declara una sola vez.
   */
  readonly tema = inject(TemaService);

  /**
   * Signal reutilizado para el botón de tema.
   */
  readonly temaOscuro = this.tema.oscuro;

  /**
   * Usuario actualmente autenticado.
   */
  readonly usuario = this.auth.usuario;

  /**
   * Controla si el usuario puede acceder al módulo Usuarios.
   */
  readonly puedeGestionarUsuarios = computed(
    () =>
      this.usuario()?.permisos.includes('USUARIO_GESTIONAR')
      ?? false
  );

  /**
   * Iniciales utilizadas en el avatar del sidebar.
   */
  readonly iniciales = computed(() => {

    const nombre =
      this.usuario()?.nombreCompleto?.trim();

    if (!nombre) {
      return '';
    }

    const partes = nombre.split(/\s+/);

    const inicial1 =
      partes[0]?.[0] ?? '';

    const inicial2 =
      partes.length > 1
        ? partes[partes.length - 1]?.[0] ?? ''
        : '';

    return (
      inicial1 + inicial2
    ).toUpperCase();
  });

  /**
   * Cambia entre tema claro y oscuro.
   */
  alternarTema(): void {
    this.tema.alternar();
  }

  /**
   * Cierra la sesión actual.
   */
  cerrarSesion(): void {
    this.auth.cerrarSesion();

    this.router.navigate([
      '/login'
    ]);
  }
}