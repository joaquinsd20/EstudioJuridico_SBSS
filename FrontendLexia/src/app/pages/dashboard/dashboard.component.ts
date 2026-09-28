import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { PanelComponent } from '../panel/panel.component';
import { AuthService } from '../../core/services/auth.service';
import {
  AgendaHoy,
  CargaEquipo,
  DashboardIndicadores,
  DashboardService,
  ExpedienteClave,
  Sugerencia
} from '../../core/services/dashboard.service';
import { EventoAgenda } from '../../core/models/agenda.models';

/**
 * Seccion del panel. El orden va de lo urgente a lo informativo, para que lo
 * que exige accion quede arriba sin tener que buscarlo.
 */
type Seccion = 'prioritario' | 'operacion' | 'cliente' | 'economia';

interface Tarjeta {
  seccion: Seccion;
  titulo: string;
  valor: string;
  /** Que significa el numero, en palabras. Para quien no conoce el estudio. */
  explicacion: string;
  routerLink: string;
  icono: string;
  /**
   * `critico` es lo que ya paso de fecha, `alerta` lo que esta por pasar.
   * `tranquilo` marca que el numero es cero, y asi se dice con palabras en vez
   * de dejar que un 0 en rojo parezca una emergencia.
   */
  tono: 'neutro' | 'alerta' | 'critico' | 'exito';
  tranquilo: boolean;
}

const REFRESCO_MS = 60_000;

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, PanelComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {

  private readonly dashboard = inject(DashboardService);
  private readonly auth = inject(AuthService);

  /** Corta las suscripciones al salir de la pantalla. */
  private readonly destruido = new Subject<void>();

  readonly usuario = this.auth.usuario;
  readonly cargando = signal(true);
  /** Fallo total: no se pudo leer nada. Ahi si se muestra error limpio. */
  readonly error = signal(false);
  /** Fallo parcial: llego algo, pero un modulo no. Se dibuja con una banda. */
  readonly parcialError = signal(false);
  /** Marca el boton mientras corre una lectura, sea la primera o un refresco. */
  readonly actualizando = signal(false);

  readonly tarjetas = signal<Tarjeta[]>([]);
  readonly agendaHoy = signal<AgendaHoy>({ eventos: [], realizados: 0, proximos: [] });
  readonly cargaEquipo = signal<CargaEquipo[]>([]);
  readonly expedientesClave = signal<ExpedienteClave[]>([]);
  readonly indicadores = signal<DashboardIndicadores | null>(null);

  /** Momento de la ultima lectura, para el texto "Actualizado hace X". */
  readonly actualizadoEn = signal<Date | null>(null);

  /** Segundos desde la ultima lectura. Tambien lleva la cuenta regresiva. */
  readonly segundos = signal(0);

  /** Cuenta regresiva hasta la proxima lectura automatica. */
  readonly proximaEn = computed(() => Math.max(0, REFRESCO_MS / 1000 - this.segundos()));

  /** Evita que el boton se pueda pulsar mil veces y dispare mil lecturas. */
  private leyendo = false;

  /** Primera palabra del nombre. Antes estaba escrita a mano en el HTML. */
  readonly primerNombre = computed(
    () => this.usuario()?.nombreCompleto?.trim().split(/\s+/)[0] ?? ''
  );

  readonly saludo = computed(() => {
    const hora = Number(
      new Intl.DateTimeFormat('en-GB', {
        timeZone: 'America/Lima',
        hour: '2-digit',
        hour12: false
      }).format(new Date())
    );
    if (hora < 12) {
      return 'Buenos días';
    }
    return hora < 19 ? 'Buenas tardes' : 'Buenas noches';
  });

  /** La fecha se formatea en Lima, no en la zona de la maquina del usuario. */
  readonly hoy = computed(() =>
    new Intl.DateTimeFormat('es-PE', {
      timeZone: 'America/Lima',
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(new Date())
  );

  readonly resumenActualizacion = computed(() => {
    const s = this.segundos();
    if (s < 10) {
      return 'Actualizado ahora mismo';
    }
    if (s < 60) {
      return `Actualizado hace ${s} segundos`;
    }
    const m = Math.floor(s / 60);
    return m === 1 ? 'Actualizado hace 1 minuto' : `Actualizado hace ${m} minutos`;
  });

  /**
   * Tarjetas agrupadas por seccion, listas para pintar. Se recalcula solo
   * cuando llegan datos nuevos, porque `tarjetas()` es un signal.
   */
  readonly secciones = computed(() => {
    const todas = this.tarjetas();
    const defs: { id: Seccion; titulo: string }[] = [
      { id: 'prioritario', titulo: 'Requiere tu atención' },
      { id: 'operacion', titulo: 'Tu operación' },
      { id: 'cliente', titulo: 'Clientes y documentos' },
      { id: 'economia', titulo: 'Ingresos del estudio' }
    ];
    return defs
      .map(s => ({ ...s, items: todas.filter(t => t.seccion === s.id) }))
      .filter(s => s.items.length > 0);
  });

  /** Si hay algo ya vencido, la cabecera lo avisa. */
  readonly hayCritico = computed(() => this.tarjetas().some(t => t.tono === 'critico'));

  /** Cuantos eventos de hoy quedan por ocurrir, para el titulo de la seccion. */
  readonly agendaPendiente = computed(() => this.agendaHoy().proximos.length);

  /** Si el dia fue pesado, la cabecera de agenda lo dice. */
  readonly agendaDiaLleno = computed(() => this.agendaPendiente() >= 4);

  /**
   * Sugerencias de que hacer hoy, ordenadas por urgencia.
   *
   * Son reglas fijas sobre los indicadores, no un modelo de lenguaje: cada
   * sugerencia sale de un numero que el usuario ya puede ver en el panel. Se
   * limita a las tres mas urgentes para no convertir el tablero en una lista
   * de quejas.
   */
  readonly sugerencias = computed<Sugerencia[]>(() => this.construirSugerencias());

  ngOnInit(): void {
    document.addEventListener('visibilitychange', this.alVolverALaPestana);
    this.refrescar();
    this.iniciarReloj();
  }

  ngOnDestroy(): void {
    this.destruido.next();
    this.destruido.complete();
    document.removeEventListener('visibilitychange', this.alVolverALaPestana);
    this.detenerReloj();
  }

  /** Lectura inmediata: la primera carga, el boton, y el chequeo del reloj. */
  refrescar(): void {
    if (this.leyendo) {
      return;
    }
    this.leyendo = true;
    this.actualizando.set(true);

    this.dashboard
      .lectura()
      .pipe(takeUntil(this.destruido))
      .subscribe({
        next: r => {
          this.indicadores.set(r.indicadores);
          this.tarjetas.set(this.construirTarjetas(r.indicadores));
          this.agendaHoy.set(r.agendaHoy);
          this.cargaEquipo.set(r.cargaEquipo);
          this.expedientesClave.set(r.expedientesClave);
          this.actualizadoEn.set(r.fecha);
          this.segundos.set(0);
          this.cargando.set(false);
          this.error.set(r.totalError);
          this.parcialError.set(r.parcialError);
          this.leyendo = false;
          this.actualizando.set(false);
        },
        error: () => {
          this.error.set(true);
          this.cargando.set(false);
          this.leyendo = false;
          this.actualizando.set(false);
        }
      });
  }

  /**
   * Un solo `setInterval` por pantalla, no uno por lectura: el usuario navega
   * mucho entre modulos y asi los timers no se acumulan.
   */
  private reloj?: ReturnType<typeof setInterval>;

  private iniciarReloj(): void {
    if (this.reloj !== undefined) {
      return;
    }
    this.reloj = setInterval(() => {
      const s = this.segundos() + 1;
      this.segundos.set(s);
      if (s >= REFRESCO_MS / 1000) {
        this.refrescar();
      }
    }, 1000);
  }

  private detenerReloj(): void {
    if (this.reloj !== undefined) {
      clearInterval(this.reloj);
      this.reloj = undefined;
    }
  }

  /**
   * Al volver a la pestaña, si hacia mas de un minuto que no se releia, se
   * actualiza en el acto. Sin esto, el tablero de la mañana sigue mostrando
   * los numeros de ayer hasta que pase el intervalo completo.
   */
  private readonly alVolverALaPestana = (): void => {
    if (document.visibilityState === 'visible' && this.segundos() >= REFRESCO_MS / 1000) {
      this.refrescar();
    }
  };

  /** `08:30` a partir de un `LocalDateTime` sin zona del backend. */
  hora(evento: EventoAgenda): string {
    return evento.fechaInicio.slice(11, 16);
  }

  /** Etiqueta corta del tipo de evento, en palabras y no el codigo. */
  tipoEvento(evento: EventoAgenda): string {
    const mapa: Record<string, string> = {
      AUDIENCIA: 'Audiencia',
      REUNION_CLIENTE: 'Reunión con cliente',
      INTERNO: 'Reunión interna',
      VENCIMIENTO_PLAZO: 'Vence un plazo',
      LLAMADA: 'Llamada',
      CITA: 'Cita',
      OTRO: 'Otro'
    };
    return mapa[evento.tipo] ?? evento.tipo;
  }

  /** Un evento ya pasado se muestra tachado, para no confundir. */
  esPasado(evento: EventoAgenda): boolean {
    return evento.estado === 'REALIZADO' || new Date(evento.fechaFin) < new Date();
  }

  /**
   * Cuanto se llena la barra de carga, sobre la persona mas cargada del equipo.
   * Relativizar contra la maxima -y no contra un tope fijo- hace comparables
   * las barras: si la mas ocupada tiene 3 tareas, la de 2 se ve al 66%, y el
   * equipo ve de un vistazo quien se esta quedando atras.
   */
  cargaPorcentaje(c: CargaEquipo): number {
    const maximo = Math.max(...this.cargaEquipo().map(x => x.pendientes), 1);
    return Math.round((c.pendientes / maximo) * 100);
  }

  /**
   * Solo se anade una tarjeta si su resumen llego. Si al usuario le falta el
   * permiso de ese modulo, el backend devuelve 403, el servicio lo trata como
   * "no aplica" y la tarjeta no aparece: mejor sin dato que un 0 que parece real.
   */
  private construirTarjetas(d: DashboardIndicadores): Tarjeta[] {
    const t: Tarjeta[] = [];

    if (d.plazos) {
      t.push({
        seccion: 'prioritario',
        titulo: 'Plazos vencidos',
        valor: this.numero(d.plazos.vencidos),
        explicacion:
          d.plazos.vencidos === 0
            ? 'Ningún plazo se pasó de fecha'
            : 'Ya se pasó la fecha límite. Revísalos hoy',
        routerLink: '/plazos',
        icono: '⚠',
        tono: d.plazos.vencidos > 0 ? 'critico' : 'neutro',
        tranquilo: d.plazos.vencidos === 0
      });
      t.push({
        seccion: 'prioritario',
        titulo: 'Vencen hoy',
        valor: this.numero(d.plazos.venceHoy),
        explicacion:
          d.plazos.venceHoy === 0
            ? 'Ningún plazo vence el día de hoy'
            : 'Su fecha límite es hoy. Son los más urgentes',
        routerLink: '/plazos',
        icono: '⏰',
        tono: d.plazos.venceHoy > 0 ? 'critico' : 'neutro',
        tranquilo: d.plazos.venceHoy === 0
      });
      t.push({
        seccion: 'operacion',
        titulo: 'En los próximos 7 días',
        valor: this.numero(d.plazos.proximosSieteDias),
        explicacion: `Vencen esta semana. ${this.numero(
          d.plazos.criticos
        )} de ellos son críticos`,
        routerLink: '/plazos',
        icono: '📅',
        tono: 'neutro',
        tranquilo: false
      });
    }

    if (d.agenda) {
      t.push({
        seccion: 'operacion',
        titulo: 'Agenda de hoy',
        valor: this.numero(d.agenda.eventosHoy),
        explicacion:
          d.agenda.eventosHoy === 0
            ? 'No tienes audiencias ni reuniones hoy'
            : `${this.numero(d.agenda.audienciasMes)} audiencias este mes`,
        routerLink: '/agenda',
        icono: '📆',
        tono: d.agenda.eventosHoy > 0 ? 'alerta' : 'neutro',
        tranquilo: d.agenda.eventosHoy === 0
      });
    }

    if (d.tareas) {
      t.push({
        seccion: 'operacion',
        titulo: 'Tareas pendientes',
        valor: this.numero(d.tareas.pendientes),
        explicacion:
          d.tareas.vencidas > 0
            ? `Tienes ${this.numero(
                d.tareas.vencidas
              )} tareas vencidas. Atiéndelas primero`
            : `Ninguna vencida. ${this.numero(d.tareas.revision)} esperando revisión`,
        routerLink: '/tareas',
        icono: '✓',
        tono: d.tareas.vencidas > 0 ? 'alerta' : 'neutro',
        tranquilo: d.tareas.vencidas === 0
      });
    }

    if (d.expedientes) {
      t.push({
        seccion: 'operacion',
        titulo: 'Expedientes activos',
        valor: this.numero(d.expedientes.activos),
        explicacion: `Casos en curso de ${this.numero(
          d.expedientes.total
        )}. ${this.numero(d.expedientes.criticos)} son críticos`,
        routerLink: '/expedientes',
        icono: '📂',
        tono: 'neutro',
        tranquilo: false
      });
    }

    if (d.actuaciones) {
      t.push({
        seccion: 'operacion',
        titulo: 'Actuaciones en curso',
        valor: this.numero(d.actuaciones.enTramite),
        explicacion: `Trabajo pendiente. Completaste ${this.numero(
          d.actuaciones.completadas
        )} de ${this.numero(d.actuaciones.total)}`,
        routerLink: '/actuaciones',
        icono: '⚙',
        tono: 'neutro',
        tranquilo: false
      });
    }

    if (d.clientes) {
      t.push({
        seccion: 'cliente',
        titulo: 'Clientes activos',
        valor: this.numero(d.clientes.activos),
        explicacion: `${this.numero(
          d.clientes.nuevosEsteMes
        )} nuevos este mes. ${this.numero(d.clientes.conExpedientes)} tienen algún caso`,
        routerLink: '/clientes',
        icono: '👥',
        tono: 'neutro',
        tranquilo: false
      });
    }

    if (d.documentos) {
      t.push({
        seccion: 'cliente',
        titulo: 'Documentos',
        valor: this.numero(d.documentos.total),
        explicacion: `${this.numero(
          d.documentos.confidenciales
        )} confidenciales. ${this.numero(d.documentos.papelera)} en la papelera`,
        routerLink: '/documentos',
        icono: '📄',
        tono: 'neutro',
        tranquilo: false
      });
    }

    if (d.monitor) {
      t.push({
        seccion: 'cliente',
        titulo: 'Expedientes vigilados',
        valor: this.numero(d.monitor.expedientesMonitoreados),
        explicacion:
          d.monitor.expedientesMonitoreados === 0
            ? 'Ningún expediente en monitor judicial'
            : `${this.numero(
                d.monitor.pendientesVerificacion
              )} esperando que confirmes un movimiento`,
        routerLink: '/monitor-judicial',
        icono: '🔍',
        tono: d.monitor.pendientesVerificacion > 0 ? 'alerta' : 'neutro',
        tranquilo: d.monitor.pendientesVerificacion === 0
      });
    }

    if (d.facturacion) {
      t.push({
        seccion: 'economia',
        titulo: 'Por cobrar',
        valor: this.moneda(d.facturacion.totalPendiente),
        explicacion:
          d.facturacion.cuentasVencidas > 0
            ? `${this.numero(
                d.facturacion.cuentasVencidas
              )} cuentas vencidas (${this.moneda(d.facturacion.totalVencido)})`
            : `Nada vencido. Ya cobraste ${this.moneda(d.facturacion.totalCobrado)}`,
        routerLink: '/facturacion',
        icono: '💰',
        tono: d.facturacion.cuentasVencidas > 0 ? 'alerta' : 'exito',
        tranquilo: d.facturacion.cuentasVencidas === 0
      });
    }

    return t;
  }

  /**
   * Reglas fijas sobre los indicadores. Cada sugerencia cita el numero que la
   * sustenta, para que el usuario pueda comprobarla y no le parezca humo.
   */
  private construirSugerencias(): Sugerencia[] {
    const d = this.indicadores();
    if (!d) {
      return [];
    }
    const out: Sugerencia[] = [];

    if (d.plazos && d.plazos.vencidos > 0) {
      out.push({
        prioridad: 'alta',
        titulo: `Atiende ${this.numero(d.plazos.vencidos)} ${
          d.plazos.vencidos === 1 ? 'plazo vencido' : 'plazos vencidos'
        }`,
        detalle:
          'Un plazo vencido puede costarte el proceso, aunque el expediente siga activo. ' +
          'Revisa si conviene pedir la reprogramación antes de que el judge lo de por concluido.',
        routerLink: '/plazos'
      });
    }

    if (d.plazos && d.plazos.venceHoy > 0) {
      out.push({
        prioridad: 'alta',
        titulo: `Hay ${this.numero(d.plazos.venceHoy)} ${
          d.plazos.venceHoy === 1 ? 'plazo que vence' : 'plazos que vencen'
        } hoy`,
        detalle:
          'Son los que no admiten margen. Deja el resto del día para estos y avisa al equipo ' +
          'para repartir la carga.',
        routerLink: '/plazos'
      });
    }

    if (d.tareas && d.tareas.revision > 0) {
      out.push({
        prioridad: 'media',
        titulo: `${this.numero(d.tareas.revision)} ${
          d.tareas.revision === 1 ? 'tarea espera' : 'tareas esperan'
        } tu revisión`,
        detalle:
          'Nadie puede avanzar hasta que las apruebes. Es el cuello de botella más rápido de levantar.',
        routerLink: '/tareas'
      });
    }

    if (d.monitor && d.monitor.pendientesVerificacion > 0) {
      out.push({
        prioridad: 'media',
        titulo: `Confirma ${this.numero(d.monitor.pendientesVerificacion)} ${
          d.monitor.pendientesVerificacion === 1 ? 'movimiento judicial' : 'movimientos judiciales'
        }`,
        detalle:
          'El sistema detectó movimientos que aún no diste por buenos. Sin confirmarlos, ' +
          'la alerta puede llegar tarde.',
        routerLink: '/monitor-judicial'
      });
    }

    if (d.facturacion && d.facturacion.cuentasVencidas > 0) {
      out.push({
        prioridad: 'media',
        titulo: `Contacta a ${this.numero(d.facturacion.cuentasVencidas)} ${
          d.facturacion.cuentasVencidas === 1 ? 'cliente' : 'clientes'
        } con cuentas vencidas`,
        detalle: `Suman ${this.moneda(d.facturacion.totalVencido)} por cobrar. ` +
          'Un recordatorio temprano suele cobrar menos esfuerzo que una cobranza formal.',
        routerLink: '/facturacion'
      });
    }

    const primeraAudiencia = this.agendaHoy().proximos.find(
      e => e.tipo === 'AUDIENCIA' || e.tipo === 'REUNION_CLIENTE'
    );
    if (primeraAudiencia) {
      out.push({
        prioridad: 'info',
        titulo: `Tu próxima cita es a las ${this.hora(primeraAudiencia)}`,
        detalle: `"${primeraAudiencia.titulo}". Deja margen para revisar el expediente antes.`,
        routerLink: '/agenda'
      });
    }

    if (out.length === 0) {
      out.push({
        prioridad: 'info',
        titulo: 'Nada urgente por hoy',
        detalle: 'No hay plazos vencidos ni tareas atrasadas. Buen momento para avanzar lo pendiente.',
        routerLink: '/tareas'
      });
    }

    const peso = { alta: 0, media: 1, info: 2 };
    return out.sort((a, b) => peso[a.prioridad] - peso[b.prioridad]).slice(0, 3);
  }

  /** Formatea enteros: 1200 se ve como 1.200, que se lee mejor de un vistazo. */
  private numero(valor: number): string {
    return new Intl.NumberFormat('es-PE').format(valor);
  }

  /** Los importes llegan como number desde Jackson; el backend ya los redondea a 2. */
  private moneda(valor: number): string {
    return `S/ ${valor.toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })}`;
  }
}
