import { Component, HostBinding, computed, inject, signal } from '@angular/core';
import {
  Router,
  RouterLink,
  RouterLinkActive
} from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { TemaService } from '../../core/services/tema.service';
import { FotoUsuarioComponent } from '../../core/components/foto-usuario.component';

@Component({
  selector: 'app-panel',
  standalone: true,
  imports: [
    RouterLink,
    RouterLinkActive,
    FotoUsuarioComponent
  ],
  templateUrl: './panel.component.html',
  styleUrl: './panel.component.css'
})
export class PanelComponent {

  /** Estado persistente del menú para conservar la preferencia al cambiar de módulo. */
  readonly colapsado = signal(false);

  @HostBinding('class.sidebar-collapsed')
  get claseSidebarColapsado(): boolean {
    return this.colapsado();
  }

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

  constructor() {
    try {
      this.colapsado.set(localStorage.getItem('lexia-sidebar-collapsed') === 'true');
    } catch {
      // Si el almacenamiento está bloqueado, el menú sigue funcionando en memoria.
    }
  }

  alternarSidebar(): void {
    const siguiente = !this.colapsado();
    this.colapsado.set(siguiente);
    try {
      localStorage.setItem('lexia-sidebar-collapsed', String(siguiente));
    } catch {
      // La preferencia no se persiste, pero el cambio visual se aplica.
    }
  }

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