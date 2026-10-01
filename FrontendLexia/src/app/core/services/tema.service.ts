import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

const CLAVE = 'lexia.tema';

/**
 * Estado del tema claro/oscuro para TODA la aplicacion.
 *
 * La clase se llama `tema-oscuro-global` y se pone en <html> porque es el
 * ancestro de los 16 modulos: una sola clase alcanza para todos.
 *
 * El nombre viene de la rama `correcciones-dashboard`, que escribio ~500 reglas
 * de tema oscuro por modulo (agenda 152, expedientes 193, tareas 120,
 * configuracion 56) usando `:host-context(.tema-oscuro-global)`. Habia dos
 * nombres en circulo, `tema-oscuro` y este, y con ambos a la vez las reglas de
 * uno no activaban las del otro: el tema se rompia a medias segun el modulo.
 *
 * POR QUE LOS MODULOS USAN :host-context Y NO .tema-oscuro
 * Angular le pone el atributo de encapsulacion a toda la cadena del selector,
 * asi que `.tema-oscuro .card` se compila como
 * `.tema-oscuro[_ngcontent-abc] .card[_ngcontent-abc]`. Como la clase ya no
 * esta dentro del componente sino en <html>, ese selector no coincide con
 * nada. `:host-context()` es la via que Angular da para depender de un
 * ancestro que esta fuera del componente.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly doc = inject(DOCUMENT);

  /** `true` = oscuro. Lee localStorage al construir; no hay cookie ni backend. */
  readonly oscuro = signal<boolean>(this.leerPreferencia());

  /** Para el atributo `aria-label` del boton. */
  readonly etiqueta = computed(() =>
    this.oscuro() ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro');

  constructor() {
    // Sincroniza la clase con el signal. `effect` corre una vez al crearse, asi
    // que tambien aplica la preferencia guardada: recargar en oscuro no
    // muestra un flash claro antes de que la pagina se pinte.
    effect(() => this.aplicar(this.oscuro()));
  }

  alternar(): void {
    this.establecer(!this.oscuro());
  }

  /** Para cuando un modulo quiere forzar un tema concreto. */
  establecer(oscuro: boolean): void {
    this.oscuro.set(oscuro);
    this.aplicar(oscuro);
    this.guardar(oscuro);
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
      // Modo privado o almacenamiento bloqueado: se usa el tema claro.
      return false;
    }
  }

  private guardar(oscuro: boolean): void {
    try {
      localStorage.setItem(CLAVE, oscuro ? 'oscuro' : 'claro');
    } catch {
      // Sin persistencia la preferencia dura lo que la pestana. No es motivo
      // para romper el toggle, asi que se ignora en silencio.
    }
  }
}