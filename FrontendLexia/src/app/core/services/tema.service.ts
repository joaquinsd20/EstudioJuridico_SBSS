import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly document = inject(DOCUMENT);
  private readonly clave = 'lexia.tema';

  readonly oscuro = signal(this.leerTemaGuardado());

  constructor() {
    this.aplicarTema(this.oscuro());
  }

  alternar(): void {
    this.establecer(!this.oscuro());
  }

  establecer(oscuro: boolean): void {
    this.oscuro.set(oscuro);

    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(this.clave, oscuro ? 'oscuro' : 'claro');
    }

    this.aplicarTema(oscuro);
  }

  private leerTemaGuardado(): boolean {
    if (typeof localStorage === 'undefined') {
      return false;
    }

    return localStorage.getItem(this.clave) === 'oscuro';
  }

  private aplicarTema(oscuro: boolean): void {
    const html = this.document.documentElement;
    const body = this.document.body;

    html.classList.toggle('tema-oscuro-global', oscuro);
    body?.classList.toggle('tema-oscuro-global', oscuro);
    html.style.colorScheme = oscuro ? 'dark' : 'light';
  }
}
