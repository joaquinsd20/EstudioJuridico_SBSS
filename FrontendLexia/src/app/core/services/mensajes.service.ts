import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Observable,
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  map,
  of,
  switchMap,
  timer,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { MensajeriaTiempoRealService } from './mensajeria-tiempo-real.service';

export interface MensajeChat {
  id: number;
  conversacionId: number;
  texto: string;
  fechaHora: string;
  esMio: boolean;
  leido: boolean;
}

export interface ContactoChat {
  /** ID del usuario destinatario. Nunca es el ID de la conversación. */
  id: number;
  /** ID del hilo abierto; null hasta que el backend lo resuelve. */
  conversacionId: number | null;
  nombre: string;
  rol: string;
  avatar: string;
  ultimoMensaje: string | null;
  noLeidos: number;
  mensajes: MensajeChat[];
}

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
  conversacionId: number | null;
  nombre: string;
  cargo: string | null;
  foto: string | null;
  ultimoMensaje: string | null;
  fechaUltimoMensaje: string | null;
  noLeidos: number;
}

interface PaginaContactosApi {
  items: ContactoApi[];
  pagina: number;
  tamano: number;
  total: number;
  hayMas: boolean;
}

interface ErrorChat {
  contactoId: number;
  mensaje: string;
}

const TAMANO_PAGINA = 10;
// Sin WebSocket/SSE, el sondeo corto mantiene los avisos actualizados sin esperar 5 s.
const INTERVALO_SINCRONIZACION_MS = 2500;
const HORA = new Intl.DateTimeFormat('es-PE', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
});

@Injectable({ providedIn: 'root' })
export class MensajesService {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);
  private readonly tiempoReal = inject(MensajeriaTiempoRealService);
  private readonly base = environment.apiUrl;
  private readonly busqueda$ = new Subject<{ texto: string; forzar: boolean }>();

  private readonly _contactos = signal<ContactoChat[]>([]);
  private readonly _seleccionado = signal<ContactoChat | null>(null);
  private readonly _busquedaContactos = signal('');
  private readonly _cargandoContactos = signal(false);
  private readonly _cargandoMas = signal(false);
  private readonly _cargandoConversacion = signal(false);
  private readonly _enviando = signal(false);
  private readonly _hayMasContactos = signal(false);
  private readonly _totalContactos = signal(0);
  private readonly _paginaContactos = signal(-1);
  private readonly _totalNoLeidos = signal(0);
  private readonly _errorContactos = signal<string | null>(null);
  private readonly _errorChat = signal<ErrorChat | null>(null);
  private readonly _errorEnvio = signal<ErrorChat | null>(null);
  private secuenciaNoLeidos = 0;
  private revisionSesion = 0;
  /** Evita que una respuesta de contactos anterior a una lectura reponga badges viejos. */
  private revisionLectura = 0;
  private usuarioUuid: string | null = null;
  private sesionInicializada = false;
  private panelAbierto = false;
  private refrescandoContactos = false;
  private refrescandoConversacion = false;

  readonly contactos = this._contactos.asReadonly();
  readonly contactoSeleccionado = this._seleccionado.asReadonly();
  readonly busquedaContactos = this._busquedaContactos.asReadonly();
  readonly cargandoContactos = this._cargandoContactos.asReadonly();
  readonly cargandoMas = this._cargandoMas.asReadonly();
  readonly cargandoConversacion = this._cargandoConversacion.asReadonly();
  readonly enviando = this._enviando.asReadonly();
  readonly hayMasContactos = this._hayMasContactos.asReadonly();
  readonly totalContactos = this._totalContactos.asReadonly();
  readonly totalNoLeidos = this._totalNoLeidos.asReadonly();
  readonly errorContactos = this._errorContactos.asReadonly();
  readonly errorChat = this._errorChat.asReadonly();
  readonly errorEnvio = this._errorEnvio.asReadonly();

  constructor() {
    // El servicio es singleton y sobrevive a navegar de una cuenta a otra.
    // Reinicia sus datos al cambiar de usuario para que una conversacion que
    // quedo seleccionada no siga refrescandose bajo la nueva sesion.
    effect(() => {
      const uuid = this.auth.usuario()?.uuid ?? null;
      // El primer valor solo inicializa la identidad: reiniciar en este punto
      // invalidaria la primera consulta del contador y lo dejaria a cero hasta
      // el siguiente intervalo de sondeo.
      if (!this.sesionInicializada) {
        this.usuarioUuid = uuid;
        this.sesionInicializada = true;
        return;
      }
      if (uuid === this.usuarioUuid) return;
      this.usuarioUuid = uuid;
      this.reiniciarEstadoPorCambioDeSesion();
    });

    // Una búsqueda nueva cancela la petición anterior. El servidor devuelve
    // solo diez contactos y el navegador nunca recibe todo el directorio.
    this.busqueda$
      .pipe(
        debounceTime(250),
        distinctUntilChanged((anterior, actual) =>
          anterior.texto === actual.texto && !actual.forzar),
        switchMap(({ texto }) => {
          const revision = this.revisionSesion;
          const uuid = this.auth.usuario()?.uuid ?? null;
          const errorVacio = {
            texto,
            pagina: { items: [] as ContactoApi[], pagina: 0, tamano: TAMANO_PAGINA, total: 0, hayMas: false },
            error: 'No se pudo cargar la lista de contactos. Inténtalo nuevamente.',
            revision,
            uuid,
          };
          if (!uuid) return of({ ...errorVacio, error: null });
          return this.http.get<PaginaContactosApi>(
            `${this.base}/mensajeria/contactos/pagina`,
            { params: { pagina: 0, tamano: TAMANO_PAGINA, busqueda: texto } },
          ).pipe(
            map(pagina => ({ texto, pagina, error: null as string | null, revision, uuid })),
            catchError(() => of(errorVacio)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ texto, pagina, error, revision, uuid }) => {
        // No aplicar respuestas tardías de otra sesión ni de otra búsqueda.
        if (revision !== this.revisionSesion || uuid !== (this.auth.usuario()?.uuid ?? null)) return;
        if (texto !== this._busquedaContactos().trim()) return;

        this._contactos.set(pagina.items.map(c => this.aContacto(c)));
        this._paginaContactos.set(pagina.pagina);
        this._totalContactos.set(pagina.total);
        this._hayMasContactos.set(pagina.hayMas);
        this._cargandoContactos.set(false);
        this._cargandoMas.set(false);
        this._errorContactos.set(error);
      });

    // Sin WebSocket/SSE, el contador global se actualiza cada 2,5 s. La lista y
    // el hilo solo se refrescan si el panel está realmente abierto.
    timer(0, INTERVALO_SINCRONIZACION_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (!this.auth.usuario()) return;
        this.actualizarTotalNoLeidos();
        if (this.panelAbierto && this._paginaContactos() >= 0) {
          this.refrescarContactosEnSegundoPlano();
          this.refrescarConversacionEnSegundoPlano();
        }
      });
  }

  private reiniciarEstadoPorCambioDeSesion(): void {
    this.tiempoReal.detener();
    this.revisionSesion++;
    this.revisionLectura++;
    this.secuenciaNoLeidos++;
    this.panelAbierto = false;
    this.refrescandoContactos = false;
    this.refrescandoConversacion = false;
    this._contactos.set([]);
    this._seleccionado.set(null);
    this._busquedaContactos.set('');
    this._cargandoContactos.set(false);
    this._cargandoMas.set(false);
    this._cargandoConversacion.set(false);
    this._enviando.set(false);
    this._hayMasContactos.set(false);
    this._totalContactos.set(0);
    this._paginaContactos.set(-1);
    this._totalNoLeidos.set(0);
    this._errorContactos.set(null);
    this._errorChat.set(null);
    this._errorEnvio.set(null);
  }

  private iniciales(nombre: string): string {
    return nombre.trim().split(/\s+/).slice(0, 2)
      .map(p => p[0]?.toUpperCase() ?? '').join('');
  }

  private hora(iso: string | null): string {
    if (!iso) return '';
    const fecha = new Date(iso);
    return Number.isNaN(fecha.getTime()) ? '' : HORA.format(fecha);
  }

  private aMensaje(m: MensajeApi): MensajeChat {
    return {
      id: m.id,
      conversacionId: m.conversacionId,
      texto: m.cuerpo,
      fechaHora: this.hora(m.fechaCreacion),
      esMio: m.propio,
      leido: m.leido,
    };
  }

  private aContacto(c: ContactoApi): ContactoChat {
    return {
      id: c.id,
      conversacionId: c.conversacionId ?? null,
      nombre: c.nombre,
      rol: c.cargo ?? '',
      avatar: this.iniciales(c.nombre),
      ultimoMensaje: c.ultimoMensaje,
      noLeidos: c.noLeidos,
      mensajes: [],
    };
  }

  /** Se llama al abrir el panel: primera página (10) y contador independiente. */
  abrir(): void {
    this.panelAbierto = true;
    this.tiempoReal.iniciar(() => {
      if (!this.panelAbierto || !this.auth.usuario()) return;
      this.actualizarTotalNoLeidos();
      if (this._paginaContactos() >= 0) {
        this.refrescarContactosEnSegundoPlano();
        this.refrescarConversacionEnSegundoPlano();
      }
    });
    this._errorContactos.set(null);
    this._contactos.set([]);
    this._paginaContactos.set(-1);
    this._hayMasContactos.set(false);
    this._cargandoContactos.set(true);
    this._cargandoMas.set(false);
    this._busquedaContactos.set('');
    this._seleccionado.set(null);
    this._cargandoConversacion.set(false);
    this._errorChat.set(null);
    this._errorEnvio.set(null);
    this.busqueda$.next({ texto: '', forzar: true });
    this.actualizarTotalNoLeidos();
  }

  cerrarPanel(): void {
    this.tiempoReal.detener();
    // Es importante apagar el sondeo: no se debe consultar/leer un hilo oculto.
    this.panelAbierto = false;
  }

  /** Actualiza contadores y previews sin borrar contactos ya paginados. */
  private refrescarContactosEnSegundoPlano(): void {
    if (this.refrescandoContactos || this._cargandoContactos() || this._cargandoMas()) return;
    this.refrescandoContactos = true;
    const revision = this.revisionSesion;
    const revisionLectura = this.revisionLectura;
    const uuid = this.auth.usuario()?.uuid ?? null;
    const busqueda = this._busquedaContactos().trim();
    const paginaActual = Math.max(0, this._paginaContactos());

    this.http.get<PaginaContactosApi>(`${this.base}/mensajeria/contactos/pagina`, {
      params: { pagina: 0, tamano: TAMANO_PAGINA, busqueda },
    }).pipe(
      catchError(() => of(null)),
      finalize(() => {
        if (revision === this.revisionSesion) this.refrescandoContactos = false;
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(pagina => {
      if (!pagina || revision !== this.revisionSesion || revisionLectura !== this.revisionLectura ||
          uuid !== (this.auth.usuario()?.uuid ?? null) ||
          busqueda !== this._busquedaContactos().trim() || !this.panelAbierto) return;
      const primeros = pagina.items.map(c => this.aContacto(c));
      const idsPrimeros = new Set(primeros.map(c => c.id));
      const anteriores = this._contactos().filter(c => !idsPrimeros.has(c.id));
      const seleccionado = this._seleccionado();
      const lista = [...primeros, ...anteriores];
      this._contactos.set(lista);
      this._totalContactos.set(pagina.total);
      this._hayMasContactos.set(pagina.hayMas || paginaActual > 0);

      // Si el hilo abierto está en la primera página, conservar su historial
      // pero sincronizar su contador y último mensaje con la lista.
      if (seleccionado) {
        const actualizado = lista.find(c => c.id === seleccionado.id);
        if (actualizado && !seleccionado.conversacionId) {
          this._seleccionado.set({ ...seleccionado, noLeidos: actualizado.noLeidos });
        }
      }
    });
  }

  /** Refresca un hilo visible. Consultarlo no marca mensajes como leidos: eso se hace con PATCH. */
  private refrescarConversacionEnSegundoPlano(): void {
    const actual = this._seleccionado();
    if (!this.panelAbierto || this.refrescandoConversacion || !actual?.conversacionId || this._cargandoConversacion()) return;

    this.refrescandoConversacion = true;
    const contactoId = actual.id;
    const conversacionId = actual.conversacionId;
    const revision = this.revisionSesion;
    const uuid = this.auth.usuario()?.uuid ?? null;

    this.http.get<ConversacionApi>(`${this.base}/mensajeria/conversaciones/${conversacionId}`)
      .pipe(
        catchError(() => of(null)),
        switchMap(conv => {
          if (!conv) return of({ conv: null, marcadaLeida: false });
          const seleccionadoAhora = this._seleccionado();
          const sigueVisible = this.panelAbierto && revision === this.revisionSesion &&
            uuid === (this.auth.usuario()?.uuid ?? null) && seleccionadoAhora?.id === contactoId &&
            seleccionadoAhora.conversacionId === conversacionId;
          if (!sigueVisible) return of({ conv, marcadaLeida: false });
          const lleganNoLeidos = (conv.mensajes ?? []).some(m => !m.propio && !m.leido);
          if (!lleganNoLeidos) return of({ conv, marcadaLeida: false });

          // Solo se marca leido si la conversacion sigue visible. El GET es puro
          // y ya no puede limpiar el contador como efecto secundario.
          return this.http.patch<number>(
            `${this.base}/mensajeria/conversaciones/${conversacionId}/leer`, null,
          ).pipe(
            map(() => ({ conv, marcadaLeida: true })),
            catchError(() => of({ conv, marcadaLeida: false })),
          );
        }),
        finalize(() => {
          if (revision === this.revisionSesion) this.refrescandoConversacion = false;
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ conv, marcadaLeida }) => {
        const seleccionado = this._seleccionado();
        if (!conv || revision !== this.revisionSesion || uuid !== (this.auth.usuario()?.uuid ?? null) ||
            !this.panelAbierto || !seleccionado || seleccionado.id !== contactoId ||
            seleccionado.conversacionId !== conversacionId || Number(conv.otroUsuarioId) !== Number(contactoId)) return;

        const mensajes = (conv.mensajes ?? []).map(m => this.aMensaje(m)).map(m =>
          marcadaLeida && !m.esMio ? { ...m, leido: true } : m,
        );
        this._seleccionado.set({
          ...seleccionado,
          ultimoMensaje: conv.ultimoMensaje,
          mensajes,
          noLeidos: marcadaLeida ? 0 : seleccionado.noLeidos,
        });

        if (marcadaLeida) {
          this.revisionLectura++;
          this._contactos.update(lista => lista.map(c => c.id === contactoId
            ? { ...c, ultimoMensaje: conv.ultimoMensaje, noLeidos: 0 }
            : c));
          this.actualizarTotalNoLeidos();
        } else {
          this._contactos.update(lista => lista.map(c => c.id === contactoId
            ? { ...c, ultimoMensaje: conv.ultimoMensaje }
            : c));
        }
      });
  }

  buscarContactos(texto: string): void {
    const busquedaAnterior = this._busquedaContactos().trim();
    const busquedaNueva = texto.trim();
    this._busquedaContactos.set(texto);
    if (busquedaNueva === busquedaAnterior) return;

    // Al cambiar de búsqueda se cierra el hilo anterior para que el cuadro de
    // escritura no pueda seguir enviando a una persona distinta de los nuevos
    // resultados que el usuario está buscando.
    if (busquedaNueva !== busquedaAnterior) {
      this._seleccionado.set(null);
      this._cargandoConversacion.set(false);
      this._errorChat.set(null);
      this._errorEnvio.set(null);
    }

    this._contactos.set([]);
    this._paginaContactos.set(-1);
    this._totalContactos.set(0);
    this._hayMasContactos.set(false);
    this._cargandoContactos.set(true);
    this._cargandoMas.set(false);
    this._errorContactos.set(null);
    this.busqueda$.next({ texto: texto.trim(), forzar: false });
  }

  /** Scroll infinito: solo pide la siguiente página al acercarse al final. */
  cargarMasContactosSiNecesario(evento: Event): void {
    const elemento = evento.target as HTMLElement | null;
    if (!elemento) return;
    const cercaDelFinal = elemento.scrollHeight - elemento.scrollTop - elemento.clientHeight < 100;
    if (cercaDelFinal) this.cargarMasContactos();
  }

  reintentarContactos(): void {
    this._errorContactos.set(null);
    this._cargandoContactos.set(true);
    this.busqueda$.next({ texto: this._busquedaContactos().trim(), forzar: true });
  }

  cargarMasContactos(): void {
    if (!this._hayMasContactos() || this._cargandoContactos() || this._cargandoMas()) return;

    const busqueda = this._busquedaContactos().trim();
    const paginaSolicitada = this._paginaContactos() + 1;
    this._cargandoMas.set(true);

    this.http.get<PaginaContactosApi>(`${this.base}/mensajeria/contactos/pagina`, {
      params: { pagina: paginaSolicitada, tamano: TAMANO_PAGINA, busqueda },
    }).pipe(
      catchError(() => {
        if (busqueda === this._busquedaContactos().trim()) {
          this._errorContactos.set('No se pudieron cargar más contactos. Vuelve a desplazarte para reintentar.');
        }
        return of(null);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(pagina => {
      if (busqueda !== this._busquedaContactos().trim()) return;
      if (!pagina) {
        this._cargandoMas.set(false);
        return;
      }
      if (paginaSolicitada !== this._paginaContactos() + 1) {
        this._cargandoMas.set(false);
        return;
      }

      const existentes = new Set(this._contactos().map(c => c.id));
      const nuevos = pagina.items
        .map(c => this.aContacto(c))
        .filter(c => !existentes.has(c.id));

      this._contactos.update(lista => [...lista, ...nuevos]);
      this._paginaContactos.set(pagina.pagina);
      this._totalContactos.set(pagina.total);
      this._hayMasContactos.set(pagina.hayMas);
      this._cargandoMas.set(false);
      this._errorContactos.set(null);
    });
  }

  private actualizarTotalNoLeidos(): void {
    const uuid = this.auth.usuario()?.uuid ?? null;
    if (!uuid) {
      this.secuenciaNoLeidos++;
      this._totalNoLeidos.set(0);
      return;
    }

    const revision = this.revisionSesion;
    const secuencia = ++this.secuenciaNoLeidos;
    this.http.get<number>(`${this.base}/mensajeria/no-leidos`)
      .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe(total => {
        // Descarta respuestas antiguas por orden de petición o por cambio de cuenta.
        if (secuencia !== this.secuenciaNoLeidos || revision !== this.revisionSesion ||
            uuid !== (this.auth.usuario()?.uuid ?? null)) return;
        if (total !== null) this._totalNoLeidos.set(Math.max(0, total));
      });
  }

  seleccionarContacto(contactoId: number): void {
    const contacto = this._contactos().find(c => c.id === contactoId);
    if (!contacto) return;
    const revision = this.revisionSesion;
    const uuid = this.auth.usuario()?.uuid ?? null;

    // Hasta que el backend confirme el hilo, no reutilizar un conversacionId
    // cacheado en el directorio: el envío permanece deshabilitado.
    this._seleccionado.set({ ...contacto, conversacionId: null, mensajes: [] });
    this._cargandoConversacion.set(true);
    this._errorChat.set(null);
    this._errorEnvio.set(null);

    this.http.get<ConversacionApi>(
      `${this.base}/mensajeria/contactos/${contactoId}/conversacion`,
    ).pipe(
      catchError(() => of(null)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(conv => {
      if (revision !== this.revisionSesion || uuid !== (this.auth.usuario()?.uuid ?? null)) return;
      const actual = this._seleccionado();
      if (!actual || actual.id !== contactoId) return;

      if (!conv) {
        this._cargandoConversacion.set(false);
        this._errorChat.set({
          contactoId,
          mensaje: 'No se pudo abrir esta conversación. Vuelve a seleccionar el contacto.',
        });
        return;
      }

      // Comprobación defensiva: si el backend devuelve otro interlocutor, no
      // mostramos ni aceptamos mensajes de ese hilo bajo el nombre equivocado.
      if (Number(conv.otroUsuarioId) !== Number(contactoId)) {
        this._cargandoConversacion.set(false);
        this._errorChat.set({
          contactoId,
          mensaje: 'La conversación devuelta no corresponde a este contacto. No se cargaron sus mensajes.',
        });
        return;
      }

      // Abrir un hilo es la acción explícita de lectura. No dependemos de que
      // un GET marque mensajes leídos, y comprobamos que el panel siga abierto
      // antes de enviar el PATCH para evitar lecturas en segundo plano.
      if (!this.panelAbierto) {
        this._cargandoConversacion.set(false);
        return;
      }
      this.http.patch<number>(`${this.base}/mensajeria/conversaciones/${conv.id}/leer`, null)
        .pipe(
          catchError(() => of(null)),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(marcadaLeida => {
          const seleccionado = this._seleccionado();
          if (revision !== this.revisionSesion || uuid !== (this.auth.usuario()?.uuid ?? null) ||
              !this.panelAbierto || !seleccionado || seleccionado.id !== contactoId) return;

          this._cargandoConversacion.set(false);
          const lecturaConfirmada = marcadaLeida !== null;
          if (lecturaConfirmada) this.revisionLectura++;
          const mensajes = (conv.mensajes ?? []).map(m => this.aMensaje(m)).map(m =>
            lecturaConfirmada && !m.esMio ? { ...m, leido: true } : m,
          );
          this._seleccionado.set({
            ...seleccionado,
            conversacionId: conv.id,
            nombre: conv.otroUsuarioNombre || seleccionado.nombre,
            rol: conv.otroUsuarioCargo ?? seleccionado.rol,
            avatar: this.iniciales(conv.otroUsuarioNombre || seleccionado.nombre),
            ultimoMensaje: conv.ultimoMensaje,
            mensajes,
            noLeidos: lecturaConfirmada ? 0 : seleccionado.noLeidos,
          });

          this._contactos.update(lista => lista.map(c => c.id === contactoId
            ? {
                ...c,
                conversacionId: conv.id,
                ultimoMensaje: conv.ultimoMensaje,
                noLeidos: lecturaConfirmada ? 0 : c.noLeidos,
              }
            : c));
          this.actualizarTotalNoLeidos();
        });
    });
  }

  enviarMensaje(contactoId: number, texto: string): Observable<boolean> {
    const cuerpo = texto.trim();
    const seleccionado = this._seleccionado();
    const conversacionId = seleccionado?.conversacionId ?? null;

    if (!cuerpo || !seleccionado || seleccionado.id !== contactoId || !conversacionId || this._enviando()) {
      return of(false);
    }

    const provisional: MensajeChat = {
      id: -Date.now(),
      conversacionId,
      texto: cuerpo,
      fechaHora: this.hora(new Date().toISOString()),
      esMio: true,
      leido: false,
    };

    this._errorEnvio.set(null);
    this._enviando.set(true);
    this._seleccionado.set({
      ...seleccionado,
      mensajes: [...seleccionado.mensajes, provisional],
    });

    // La petición queda vinculada al ID del hilo abierto, no al ID de usuario.
    // El backend verifica que el usuario actual participe en ese hilo.
    return this.http.post<MensajeApi>(
      `${this.base}/mensajeria/conversaciones/${conversacionId}/mensajes`,
      { cuerpo },
    ).pipe(
      map(guardado => {
        if (Number(guardado.conversacionId) !== Number(conversacionId) || !guardado.propio) {
          this.quitarProvisional(contactoId, conversacionId, provisional.id);
          this._errorEnvio.set({
            contactoId,
            mensaje: 'El servidor devolvió un mensaje de otro hilo. No se añadió a la conversación.',
          });
          return false;
        }

        const actual = this._seleccionado();
        if (actual?.id === contactoId && actual.conversacionId === conversacionId) {
          this._seleccionado.set({
            ...actual,
            mensajes: actual.mensajes.map(m => m.id === provisional.id ? this.aMensaje(guardado) : m),
          });
        }

        this.actualizarPreview(contactoId, guardado.cuerpo);
        return true;
      }),
      catchError(error => {
        this.quitarProvisional(contactoId, conversacionId, provisional.id);
        this._errorEnvio.set({
          contactoId,
          mensaje: this.mensajeErrorEnvio(error),
        });
        return of(false);
      }),
      finalize(() => this._enviando.set(false)),
    );
  }

  private quitarProvisional(contactoId: number, conversacionId: number, idProvisional: number): void {
    const actual = this._seleccionado();
    if (actual?.id === contactoId && actual.conversacionId === conversacionId) {
      this._seleccionado.set({
        ...actual,
        mensajes: actual.mensajes.filter(m => m.id !== idProvisional),
      });
    }
  }

  private mensajeErrorEnvio(error: any): string {
    const detalle = error?.error?.message ?? error?.error?.mensaje;
    if (typeof detalle === 'string' && detalle.trim()) return detalle;
    if (error?.status === 0) return 'No se pudo conectar con el servidor. El mensaje no se envió.';
    return 'No se pudo enviar el mensaje. Inténtalo nuevamente.';
  }

  private actualizarPreview(contactoId: number, cuerpo: string): void {
    this._contactos.update(lista => lista.map(c =>
      c.id === contactoId ? { ...c, ultimoMensaje: cuerpo } : c));
    const actual = this._seleccionado();
    if (actual?.id === contactoId) {
      this._seleccionado.set({ ...actual, ultimoMensaje: cuerpo });
    }
  }
}
