import { Injectable, computed, signal } from '@angular/core';

/**
 * Cuenta las peticiones al backend en curso. La barra fina de arriba de la
 * pantalla se enciende mientras haya alguna, para que el abogado sepa que el
 * sistema está trabajando y no vuelva a hacer clic.
 */
@Injectable({ providedIn: 'root' })
export class CargaGlobalService {
  private readonly pendientes = signal(0);
  readonly activa = computed(() => this.pendientes() > 0);

  iniciar(): void { this.pendientes.update(n => n + 1); }
  terminar(): void { this.pendientes.update(n => Math.max(0, n - 1)); }
}
