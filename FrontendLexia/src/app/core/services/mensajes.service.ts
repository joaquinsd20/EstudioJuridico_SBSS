import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Un mensaje tal y como lo pinta la burbuja del chat.
 *
 * `esMio`, `texto` y `fechaHora` no son los nombres del backend (`propio`,
 * `cuerpo`, `fechaCreacion`): se adaptan aqui para que la plantilla no tenga
 * que traducir y el HTML siga leyendose en Castellano.
 */
export interface MensajeChat {
  id: number;
  texto: string;
  fechaHora: string;
  esMio: boolean;
  leido: boolean;
}

/**
 * Un colega con el que se puede escribir.
 *
 * Es una ficha de persona, no una conversacion: por eso se puede tener a alguien
 * en la lista sin haberle escrito nunca. `mensajes` viene vacio hasta que se
 * selecciona el contacto; abrir el chat es lo que lo carga.
 */
export interface ContactoChat {
  id: number;
  nombre: string;
  rol: string;
  avatar: string;
  ultimoMensaje: string | null;
  noLeidos: number;
  mensajes: MensajeChat[];
}

/** Formas exactas que devuelve el backend (ver MensajeriaDtos). */
interface MensajeApi {
  id: number;
  conversacionId: number;
  emisorId: number;
  emisorNombre: string;
  propio: boolean;
  cuerpo: string;
  leido: boolean;
  fechaCreacion: string;
}

interface ConversacionApi {
  id: number;
  otroUsuarioId: number;
  otroUsuarioNombre: string;
  otroUsuarioCargo: string | null;
  otroUsuarioFoto: string | null;
  ultimoMensaje: string | null;
  fechaUltimoMensaje: string | null;
  noLeidos: number;
  mensajes: MensajeApi[] | null;
}

interface ContactoApi {
  id: number;
  nombre: string;
  cargo: string | null;
  foto: string | null;
  ultimoMensaje: string | null;
  fechaUltimoMensaje: string | null;
  noLeidos: number;
}

const HORA = new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit', hour12: true });

@Injectable({ providedIn: 'root' })
export class MensajesService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  private readonly _contactos = signal<ContactoChat[]>([]);
  private readonly _seleccionado = signal<ContactoChat | null>(null);

  readonly contactos = this._contactos.asReadonly();
  readonly contactoSeleccionado = this._seleccionado.asReadonly();

  /**
   * Sin leer en toda la lista. Sale de `contactos()` y no de la bandeja: la
   * lista lateral incluye a gente sin conversacion, y quien tiene mensajes
   * pendientes de un colega nuevo solo aparece ahi.
   */
  readonly totalNoLeidos = computed(() =>
    this._contactos().reduce((total, c) => total + c.noLeidos, 0));

  /** Las iniciales van en el avatar porque la foto casi nunca viene. */
  private iniciales(nombre: string): string {
    return nombre.trim().split(/\s+/).slice(0, 2).map(p => p[0]?.toUpperCase() ?? '').join('');
  }

  private hora(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : HORA.format(d);
  }

  private aMensaje(m: MensajeApi): MensajeChat {
    return {
      id: m.id,
      texto: m.cuerpo,
      fechaHora: this.hora(m.fechaCreacion),
      esMio: m.propio,
      leido: m.leido,
    };
  }

  private aContacto(c: ContactoApi): ContactoChat {
    return {
      id: c.id,
      nombre: c.nombre,
      rol: c.cargo ?? '',
      avatar: this.iniciales(c.nombre),
      ultimoMensaje: c.ultimoMensaje,
      noLeidos: c.noLeidos,
      mensajes: [],
    };
  }

  /**
   * Descarga la lista de colegas. Se llama al abrir el panel, no al entrar al
   * dashboard: son 9 filas y solo hacen falta cuando se va a usar el chat.
   */
  abrir(): void {
    this.http.get<ContactoApi[]>(`${this.base}/mensajeria/contactos`)
      .pipe(catchError(() => of([] as ContactoApi[])))
      .subscribe(lista => this._contactos.set(lista.map(c => this.aContacto(c))));
  }

  /**
   * Abre el chat con un colega.
   *
   * Se pide por id de USUARIO, no de conversacion: con un id de conversacion
   * no habria forma de abrir el chat con alguien con quien nunca se ha hablado,
   * que es justo el caso de la mayoria de las entradas de la lista.
   */
  seleccionarContacto(contactoId: number): void {
    const contacto = this._contactos().find(c => c.id === contactoId);
    if (!contacto) return;

    // Se selecciona de inmediato para pintar la cabecera, y los mensajes llegan
    // despues. Marcarlo "cargando" haria parpadear la columna entera.
    this._seleccionado.set(contacto);

    this.http.get<ConversacionApi>(
      `${this.base}/mensajeria/contactos/${contactoId}/conversacion`)
      .pipe(catchError(() => of(null)))
      .subscribe(conv => {
        // Si mientras tanto el usuario eligio otro contacto, esta respuesta ya
        // no le corresponde: no debe pisar la seleccion ni limpiar sus
        // no leidos, que son los del otro.
        const actual = this._seleccionado();
        if (!actual || actual.id !== contactoId) return;

        if (!conv) return;

        // El servidor ya cerro la lectura en GET /conversacion, asi que aqui
        // no hace falta un PATCH extra: lo que se hace es bajar el contador
        // local para que la insignia no siga marcando lo que ya se leyo. Si
        // solo se hiciera en local, la insignia reapareceria al recargar.
        this.marcarLeidos(contactoId);

        this._seleccionado.set({
          ...actual, mensajes: (conv.mensajes ?? []).map(m => this.aMensaje(m))
        });
      });
  }

  /**
   * Pone a cero los no leidos del contacto tras abrir su hilo.
   *
   * Solo local, a proposito: GET /conversacion ya marco la conversacion como
   * leida en el servidor, de modo que no hace falta un PATCH .../leer que
   * seria una escritura redundante. Si esa peticcion fallara, el contador
   * local habria dicho una cosa y el servidor otra.
   */
  private marcarLeidos(contactoId: number): void {
    this._contactos.update(lista => lista.map(c => (c.id === contactoId ? { ...c, noLeidos: 0 } : c)));
  }

  /**
   * Envia un mensaje a un colega y lo anade al hilo sin volver a descargarlo.
   *
   * El alta optimista se hace solo con lo que ya sabemos (el texto y que es
   * nuestro): el id y la hora definitivos los trae el backend en la respuesta,
   * asi que se sustituye la burbuja provisional por la real en vez de dejar
   * una con id inventado que rompia el `@for ... track`.
   */
  enviarMensaje(contactoId: number, texto: string): void {
    const cuerpo = texto.trim();
    if (!cuerpo) return;

    const provisional: MensajeChat = {
      id: -Date.now(), texto: cuerpo, fechaHora: this.hora(new Date().toISOString()),
      esMio: true, leido: false,
    };

    const actual = this._seleccionado();
    if (actual && actual.id === contactoId) {
      this._seleccionado.set({ ...actual, mensajes: [...actual.mensajes, provisional] });
    }

    this.http.post<MensajeApi>(`${this.base}/mensajeria/mensajes`, {
      destinatarioId: contactoId,
      cuerpo
    }).pipe(catchError(() => of(null))).subscribe(guardado => {
      const sel = this._seleccionado();
      if (!sel || sel.id !== contactoId) return;

      if (!guardado) {
        // El backend lo rechazo: se retira la burbuja provisional en vez de
        // dejar un mensaje que el usuario cree enviado y no lo esta.
        this._seleccionado.set({
          ...sel, mensajes: sel.mensajes.filter(m => m.id !== provisional.id)
        });
        return;
      }

      this._seleccionado.set({
        ...sel,
        mensajes: sel.mensajes.map(m => (m.id === provisional.id ? this.aMensaje(guardado) : m)),
      });

      // El preview de la lista lateral lo pinta el backend, no el front: si se
      // actualizase aqui, dos pestanas abiertas mostraian frases distintas.
      this.actualizarPreview(contactoId, guardado.cuerpo);
    });
  }

  private actualizarPreview(contactoId: number, cuerpo: string): void {
    this._contactos.update(lista => lista.map(c =>
      c.id === contactoId ? { ...c, ultimoMensaje: cuerpo } : c));
  }
}