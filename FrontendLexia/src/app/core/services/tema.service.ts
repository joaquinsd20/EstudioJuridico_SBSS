import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

const CLAVE = 'lexia.tema';

/**
 * Estado del tema claro/oscuro para toda la aplicación.
 * La clase global permite que los módulos existentes reaccionen al tema.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly doc = inject(DOCUMENT);

  /** `true` = oscuro. La preferencia persistida se lee al iniciar. */
  readonly oscuro = signal<boolean>(this.leerPreferencia());
  readonly etiqueta = computed(() => this.oscuro() ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro');

  constructor() {
    effect(() => this.aplicar(this.oscuro()));
  }

  alternar(): void {
    this.establecer(!this.oscuro());
  }

  /** Cambia y persiste el tema; se conserva para las funciones existentes. */
  establecer(oscuro: boolean): void {
    this.previsualizar(oscuro);
    this.guardar(oscuro);
  }

  /** Aplica una vista previa global sin escribirla en el almacenamiento local. */
  previsualizar(oscuro: boolean): void {
    this.oscuro.set(oscuro);
    this.aplicar(oscuro);
  }

  private aplicar(oscuro: boolean): void {
    const raiz = this.doc.documentElement;
    raiz.classList.toggle('tema-oscuro-global', oscuro);
    raiz.style.colorScheme = oscuro ? 'dark' : 'light';
  }

  private leerPreferencia(): boolean {
    try {
      return localStorage.getItem(CLAVE) === 'oscuro';
    } catch {
      return false;
    }
  }

  private guardar(oscuro: boolean): void {
    try {
      localStorage.setItem(CLAVE, oscuro ? 'oscuro' : 'claro');
    } catch {
      // Si el almacenamiento está bloqueado, el tema continúa aplicado en memoria.
    }
  }
}
