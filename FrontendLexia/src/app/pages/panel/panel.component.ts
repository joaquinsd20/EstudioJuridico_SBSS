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
   * El selector de tema vive aqui y no en el dashboard. El sidebar es el
   * unico elemento presente en los 16 modulos, asi que es el unico sitio
   * desde el que se puede cambiar el tema sin volver al dashboard.
   */
  readonly tema = inject(TemaService);

  /** Usuario en sesion, para pintar nombre/rol reales en el sidebar. */
  readonly usuario = this.auth.usuario;

  /** Depende del signal del usuario, asi que se actualiza al cambiar la sesion. */
  readonly puedeGestionarUsuarios = computed(
    () => this.usuario()?.permisos.includes('USUARIO_GESTIONAR') ?? false);

  /** Iniciales para el avatar: primera letra del nombre y del primer apellido. */
  readonly iniciales = computed(() => {
    const nombre = this.usuario()?.nombreCompleto?.trim();
    if (!nombre) {
      return '';
    }
    const partes = nombre.split(/\s+/);
    const inicial1 = partes[0]?.[0] ?? '';
    const inicial2 = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (inicial1 + inicial2).toUpperCase();
  });

  cerrarSesion(): void {
    this.auth.cerrarSesion();
    this.router.navigate(['/login']);
  }
}
