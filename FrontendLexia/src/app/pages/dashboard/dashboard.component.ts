import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
  NuevaResolucionItem
} from '../../core/services/dashboard.service';
import { BusquedaService } from '../../core/services/busqueda.service';
import {
  GraficosDashboardService,
  GraficosDashboard,
  BarraGrafico,
  EstadoCobranza
} from '../../core/services/graficos-dashboard.service';
import { NotificacionService, Notificacion } from '../../core/services/notificacion.service';
import { MensajesService, ContactoChat } from '../../core/services/mensajes.service';
import { TemaService } from '../../core/services/tema.service';
import { BusquedaRespuesta, ResultadoBusqueda } from '../../core/models/busqueda.models';

interface SegmentoDonut {
  nombre: string;
  color: string;
  dash: string;
  offset: string;
  porcentaje: number;
}

interface ItemAgendaVista {
  hora: string;
  titulo: string;
  detalle: string;
  etiqueta: string;
  tonoEtiqueta: 'morado' | 'naranja' | 'rojo' | 'azul';
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PanelComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, OnDestroy {

  @ViewChild('inputBusqueda') inputBusqueda?: ElementRef<HTMLInputElement>;

  // ─── Servicios ────────────────────────────────────────────────────────────
  private readonly dashboardSvc = inject(DashboardService);
  private readonly busquedaSvc  = inject(BusquedaService);
  private readonly graficosSvc  = inject(GraficosDashboardService);
  private readonly notifSvc     = inject(NotificacionService);
  private readonly temaSvc      = inject(TemaService);
  readonly mensajesSvc          = inject(MensajesService);
  private readonly auth         = inject(AuthService);
  private readonly router       = inject(Router);

  private readonly destruido = new Subject<void>();

  // ─── Usuario y Sesión ─────────────────────────────────────────────────────
  readonly usuario = this.auth.usuario;

  readonly primerNombre = computed(() => {
    const nombre = this.usuario()?.nombreCompleto?.trim();
    if (!nombre) { return 'Sofía'; }
    return nombre.split(/\s+/)[0];
  });

  readonly estudioNombre = computed(() => {
    const u = this.usuario();
    return u?.rolPrincipal === 'ADMINISTRADOR'
      ? 'ESTUDIO RAMÍREZ ABOGADOS'
      : 'ESTUDIO JURÍDICO SBSS';
  });

  readonly saludo = computed(() => {
    const hora = Number(
      new Intl.DateTimeFormat('en-GB', { timeZone: 'America/Lima', hour: '2-digit', hour12: false }).format(new Date())
    );
    if (hora < 12) { return 'Buenos días'; }
    return hora < 19 ? 'Buenas tardes' : 'Buenas noches';
  });

  // ─── Estado del Dashboard ─────────────────────────────────────────────────
  readonly cargando     = signal(true);
  readonly actualizando = signal(false);

  readonly indicadores        = signal<DashboardIndicadores | null>(null);
  readonly expedientesClave   = signal<ExpedienteClave[]>([]);
  readonly nuevasResoluciones = signal<NuevaResolucionItem[]>([]);
  readonly agendaHoy          = signal<AgendaHoy>({ eventos: [], realizados: 0, proximos: [] });
  readonly cargaEquipo        = signal<CargaEquipo[]>([]);

  // ─── 5 Indicadores KPI ────────────────────────────────────────────────────
  // Ninguno inventa un numero. Antes, con un 0 real o con el modulo caido,
  // mostraban 2, 5, 3, 1 y 4: los valores de la semilla de desarrollo. Un 0 se
  // muestra como 0, y `null` (sin permiso o sin respuesta) como "—".
  readonly kpiPlazosVencidos = computed(() => this.indicadores()?.plazos?.vencidos ?? null);

  /** `proximosSieteDias` ya incluye los que vencen hoy: sumarle `venceHoy` los contaba dos veces. */
  readonly kpiPlazosPorVencer = computed(() => this.indicadores()?.plazos?.proximosSieteDias ?? null);

  readonly kpiNuevasResoluciones = computed(() => this.indicadores()?.monitor?.nuevasResolucionesHoy ?? null);

  readonly kpiAudienciasProximas = computed(() => this.indicadores()?.agenda?.audienciasMes ?? null);

  readonly kpiTareasPendientes = computed(() => this.indicadores()?.tareas?.pendientes ?? null);

  // ─── Lista de Atención Inmediata ──────────────────────────────────────────
  // El color y la etiqueta salen del motivo de cada expediente, no de su
  // posicion en la lista (antes el tercero era siempre "Audiencia").
  readonly listaAtencionInmediata = computed(() =>
    this.expedientesClave().slice(0, 3).map(e => ({
      codigo: e.numeroExpediente || e.codigoInterno,
      materia: e.materia || e.clienteNombre,
      alerta: e.alertaTexto || e.motivo,
      alertaColor: e.badgeTipo === 'critico' ? 'rojo' : 'azul',
      badgeTexto: e.badgeDias ?? '',
      badgeTipo: e.badgeTipo ?? 'parado'
    }))
  );

  // ─── Lista de Nuevas Resoluciones ─────────────────────────────────────────
  readonly listaNuevasResoluciones = computed(() => {
    const reales = this.nuevasResoluciones();
    if (reales.length > 0) {
      return reales.slice(0, 3);
    }
    return [];
  });

  // ─── Lista de Agenda de Hoy ───────────────────────────────────────────────
  readonly listaAgendaHoy = computed<ItemAgendaVista[]>(() => {
    const eventos = this.agendaHoy().eventos;
    if (eventos.length > 0) {
      return eventos.slice(0, 4).map(e => {
        const hora = e.fechaInicio ? e.fechaInicio.slice(11, 16) : '09:00 AM';
        let tono: 'morado' | 'naranja' | 'rojo' | 'azul' = 'morado';
        let etiqueta = 'Audiencia';

        if (e.tipo === 'AUDIENCIA') {
          tono = 'morado';
          etiqueta = 'Tenencia';
        } else if (e.tipo === 'REUNION_CLIENTE') {
          tono = 'naranja';
          etiqueta = 'Reunión';
        } else if (e.tipo === 'VENCIMIENTO_PLAZO') {
          tono = 'rojo';
          etiqueta = 'Plazo';
        } else {
          tono = 'azul';
          etiqueta = 'Escrito';
        }

        return {
          hora,
          titulo: e.tipo === 'AUDIENCIA' ? 'Audiencia' : e.tipo === 'REUNION_CLIENTE' ? 'Reunión con cliente' : e.tipo === 'VENCIMIENTO_PLAZO' ? 'Vencimiento de plazo' : 'Elaborar escrito',
          detalle: e.expedienteCodigo || e.clienteNombre || e.titulo,
          etiqueta,
          tonoEtiqueta: tono
        };
      });
    }
    return [];
  });

  // ─── Gráficos Completos ───────────────────────────────────────────────────
  readonly graficos = signal<GraficosDashboard>({
    expedientesPorEstado: [],
    expedientesPorEspecialidad: [],
    expedientesPorAbogado: [],
    cargaPorUsuario: [],
    tareasPorUsuario: [],
    ingresosPorMes: [],
    cobranza: { facturado: 0, cobrado: 0, pendiente: 0, vencido: 0, cuentasPendientes: 0, cuentasVencidas: 0 },
    puedeVerFacturacion: false
  });

  // 1. Resumen de expedientes (Donut chart)
  readonly donutItems = computed(() => {
    const raw = this.graficos().expedientesPorEstado;
    if (raw && raw.length > 0) {
      const colores = ['#6366f1', '#38bdf8', '#4ade80', '#fb923c'];
      return raw.slice(0, 4).map((item, i) => ({
        nombre: item.nombre,
        valor: item.valor,
        color: colores[i % colores.length]
      }));
    }
    return [];
  });

  readonly totalExpedientesDonut = computed(() => {
    return this.donutItems().reduce((acc, curr) => acc + curr.valor, 0);
  });

  readonly donutSegmentos = computed<SegmentoDonut[]>(() => {
    const items = this.donutItems();
    const total = this.totalExpedientesDonut() || 1;
    const circunferencia = 2 * Math.PI * 65;
    let acumulado = 0;

    return items.map(it => {
      const pct = Math.round((it.valor / total) * 100);
      const dash = (it.valor / total) * circunferencia;
      const seg: SegmentoDonut = {
        nombre: it.nombre,
        color: it.color,
        dash: `${dash} ${circunferencia - dash}`,
        offset: `${circunferencia - acumulado}`,
        porcentaje: pct
      };
      acumulado += dash;
      return seg;
    });
  });

  // 2. Expedientes por especialidad (Barras verticales)
  readonly especialidadBarras = computed(() => {
    const raw = this.graficos().expedientesPorEspecialidad;
    if (raw && raw.length > 0) {
      return raw.slice(0, 5).map(e => ({ nombre: e.nombre, valor: e.valor }));
    }
    return [];
  });

  /**
   * Escala del eje Y del grafico de especialidades.
   *
   * El axis estaba fijo en 12, lo cual solo encajaba con los datos de
   * ejemplo: con el maximo real (4 expedientes) las barras ocupaban un
   * tercio y las etiquetas mentian. Ahora el tope sale de los datos.
   *
   * Se elige un paso limpio (1, 2, 5, 10...) y el tope es ese paso por
   * las 6 divisiones del eje, para que las siete etiquetas salgan
   * enteras y distintas en lugar de repetirse.
   */
  readonly especialidadEscala = computed(() => {
    const maximoReal = this.especialidadBarras().reduce((acc, b) => Math.max(acc, b.valor), 0);
    const pasos = [1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000];
    const paso = pasos.find(p => p * 6 >= maximoReal) ?? Math.ceil(maximoReal / 6);
    const maximo = maximoReal <= 0 ? 6 : paso * 6;
    return { maximo, ticks: [0, 1, 2, 3, 4, 5, 6].map(n => maximo - (paso * n)) };
  });

  // 3. Carga de trabajo del equipo (Puntos de riesgo ponderado)
  readonly cargaEquipoVista = computed(() => {
    const raw = this.graficos().cargaPorUsuario;
    if (raw && raw.length > 0) {
      const maxVal = Math.max(...raw.map(r => r.valor), 1);
      return raw.slice(0, 4).map(r => ({
        nombre: r.nombre,
        expedientes: r.valor,
        porcentaje: Math.round((r.valor / maxVal) * 80) + 20
      }));
    }
    return [];
  });

  // 4. Tareas por usuario (Abiertas y vencidas)
  readonly tareasPorUsuarioVista = computed(() => {
    const raw = this.graficos().tareasPorUsuario;
    if (raw && raw.length > 0) {
      const maxVal = Math.max(...raw.map(r => r.valor), 1);
      return raw.slice(0, 4).map(r => ({
        nombre: r.nombre,
        total: r.valor,
        detalle: r.detalle || `${r.valor} tareas`,
        esVencida: r.detalle ? r.detalle.includes('vencida') : false,
        porcentaje: Math.round((r.valor / maxVal) * 100)
      }));
    }
    return [];
  });

  // 5. Ingresos por mes (Facturación últimos 6 meses)
  readonly ingresosPorMesVista = computed(() => {
    const raw = this.graficos().ingresosPorMes;
    if (raw && raw.length > 0) {
      const maxVal = Math.max(...raw.map(r => r.valor), 1);
      return raw.slice(-6).map(r => ({
        mes: this.mesCorto(r.nombre),
        valor: r.valor,
        porcentaje: Math.round((r.valor / maxVal) * 100)
      }));
    }
    return [];
  });

  // 6. Cuentas por cobrar y cobranza
  readonly cobranzaVista = computed(() => this.graficos().cobranza);

  // ─── Buscador Global ──────────────────────────────────────────────────────
  readonly busquedaAbierta    = signal(false);
  readonly textoBusqueda      = signal('');
  readonly buscando           = signal(false);
  readonly resultadosBusqueda = signal<BusquedaRespuesta>({
    texto: '', total: 0, grupos: []
  });

  private busquedaTimer?: ReturnType<typeof setTimeout>;

  @HostListener('window:keydown', ['$event'])
  manejarAtajoTeclado(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      this.inputBusqueda?.nativeElement?.focus();
      this.busquedaAbierta.set(true);
    }
  }

  alEscribir(e: Event): void {
    const texto = (e.target as HTMLInputElement).value;
    this.textoBusqueda.set(texto);
    this.busquedaAbierta.set(true);
    clearTimeout(this.busquedaTimer);

    if (texto.trim().length < 2) {
      this.resultadosBusqueda.set({ texto, total: 0, grupos: [] });
      return;
    }

    this.buscando.set(true);
    this.busquedaTimer = setTimeout(() => {
      this.busquedaSvc
        .buscar(texto)
        .pipe(takeUntil(this.destruido))
        .subscribe(r => {
          if (r && r.total > 0) {
            // Asegurar que cada grupo tenga su lista items poblada
            const normalizados = r.grupos.map(g => ({
              ...g,
              items: g.items || g.resultados || []
            }));
            this.resultadosBusqueda.set({ ...r, grupos: normalizados });
          } else {
            this.resultadosBusqueda.set({ texto: texto.trim(), total: 0, grupos: [] });
          }
          this.buscando.set(false);
        });
    }, 300);
  }

  cerrarBusqueda(): void {
    this.busquedaAbierta.set(false);
    this.textoBusqueda.set('');
    this.resultadosBusqueda.set({ texto: '', total: 0, grupos: [] });
  }

  navegarResultado(r: ResultadoBusqueda): void {
    if (r.ruta) {
      this.router.navigateByUrl(r.ruta);
    }
    this.cerrarBusqueda();
  }

  // ─── Centro de Notificaciones ─────────────────────────────────────────────
  readonly notifAbierto   = signal(false);

  /**
   * Arranca VACIA a proposito.
   *
   * Antes se inicializaba con cuatro notificaciones escritas a mano
   * ("Nueva Resolucion N. 06 en EXP. 04521-2026", "Vencimiento critico
   * hoy"...), y nunca se llamo a la API. Se veian en pantalla como si
   * fueran reales, con su contador, su hora relativa y su enlace a un
   * expediente: cuatro filas falsas en un panel que el usuario toma
   * por fiable. Un array vacio es honesto mientras llega la respuesta;
   * uno relleno solo miente.
   */
  readonly notificaciones = signal<Notificacion[]>([]);

  readonly noLeidas = computed(() => this.notificaciones().filter(n => !n.leida).length);

  /**
   * Carga la bandeja de notificaciones.
   *
   * Va en ngOnInit y no al abrir el panel porque el contador del boton
   * tiene que ser correcto con el panel cerrado: ese numero es la
   * unica pista de que hay algo pendiente.
   */
  private cargarNotificaciones(): void {
    this.notifSvc
      .listar()
      .pipe(takeUntil(this.destruido))
      .subscribe(b => this.notificaciones.set(b.items));
  }

  alternarNotif(): void {
    this.notifAbierto.update(v => !v);
    this.mensajesAbierto.set(false);
    this.perfilAbierto.set(false);
  }

  leerTodas(): void {
    this.notificaciones.update(list => list.map(n => ({ ...n, leida: true })));
    this.notifSvc.marcarTodasLeidas().pipe(takeUntil(this.destruido)).subscribe();
  }

  abrirNotif(n: Notificacion): void {
    this.notificaciones.update(list => list.map(x => x.id === n.id ? { ...x, leida: true } : x));
    this.notifSvc.marcarLeida(n.id).pipe(takeUntil(this.destruido)).subscribe();
    if (n.urlDestino) {
      this.router.navigateByUrl(n.urlDestino);
      this.notifAbierto.set(false);
    }
  }

  // ─── Mensajes Internos ────────────────────────────────────────────────────
  readonly mensajesAbierto   = signal(false);
  textoNuevoMensaje          = '';

  alternarMensajes(): void {
    const abre = !this.mensajesAbierto();

    this.mensajesAbierto.set(abre);
    this.notifAbierto.set(false);
    this.perfilAbierto.set(false);

    // La lista de colegas se pide al ABRIR, no al montar. Es una llamada por
    // visita al dashboard para un panel que casi siempre esta cerrado.
    if (abre) {
      this.mensajesSvc.abrir();
    }
  }

  seleccionarChat(id: number): void {
    this.mensajesSvc.seleccionarContacto(id);
  }

  enviarMensaje(): void {
    const contacto = this.mensajesSvc.contactoSeleccionado();
    const texto = this.textoNuevoMensaje;
    if (contacto && texto.trim()) {
      this.mensajesSvc.enviarMensaje(contacto.id, texto);
      this.textoNuevoMensaje = '';
    }
  }

  // ─── Perfil de Usuario ────────────────────────────────────────────────────
  readonly perfilAbierto = signal(false);

  alternarPerfil(): void {
    this.perfilAbierto.update(v => !v);
    this.notifAbierto.set(false);
    this.mensajesAbierto.set(false);
  }

  cerrarSesion(): void {
    this.auth.cerrarSesion();
    this.router.navigate(['/login']);
  }

  // ─── Tema Claro / Oscuro ──────────────────────────────────────────────────
  readonly temaOscuro = this.temaSvc.oscuro;

  alternarTema(): void {
    this.temaSvc.alternar();
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────
  mesCorto(aaaaMm: string): string {
    const partes = aaaaMm.split('-');
    const mm = partes.length > 1 ? partes[1] : aaaaMm;
    const meses = ['Ene','Feb','Mar','Abr','May','Jun','Jul','Ago','Sep','Oct','Nov','Dic'];
    return meses[parseInt(mm, 10) - 1] ?? aaaaMm;
  }

  moneda(v: number): string {
    return `S/ ${(v ?? 0).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  /**
   * Ancho de cada barra del gráfico de cuentas por cobrar, en porcentaje del
   * total facturado.
   *
   * La versión anterior hacía `(cobrado / facturado) * 100` en la plantilla.
   * Con `facturado` en 0 —un estudio que aún no ha facturado, o un periodo
   * sin movimientos— eso es 0/0: la plantilla pinta `NaN%`, la barra se
   * dibuja con un ancho inválido y el navegador la deja a cero sin avisar.
   * Aquí se resuelve a 0 a propósito, que es lo que se quiere ver: no hay
   * nada que representar.
   *
   * Se acotó además por arriba porque un saldo vencido puede superar lo
   * facturado en el periodo, y una barra de 130% se sale de su carril.
   */
  porcentaje(valor: number): number {
    const total = this.cobranzaVista().facturado;
    if (!total) return 0;
    const pct = (valor / total) * 100;
    if (!Number.isFinite(pct) || pct <= 0) return 0;
    return Math.min(pct, 100);
  }

  tiempoRelativo(fecha: string): string {
    const ms = Date.now() - new Date(fecha).getTime();
    if (ms < 60_000) return 'ahora mismo';
    const m = Math.floor(ms / 60_000);
    if (m < 60) return `hace ${m} min`;
    const h = Math.floor(m / 60);
    if (h < 24) return `hace ${h} h`;
    return `hace ${Math.floor(h / 24)} d`;
  }

  // ─── Ciclo de Vida ────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.refrescar();
    this.cargarGraficos();
    this.cargarNotificaciones();
  }

  ngOnDestroy(): void {
    this.destruido.next();
    this.destruido.complete();
    clearTimeout(this.busquedaTimer);
  }

  refrescar(): void {
    this.actualizando.set(true);
    this.dashboardSvc
      .lectura()
      .pipe(takeUntil(this.destruido))
      .subscribe({
        next: r => {
          this.indicadores.set(r.indicadores);
          this.expedientesClave.set(r.expedientesClave);
          this.nuevasResoluciones.set(r.nuevasResoluciones);
          this.agendaHoy.set(r.agendaHoy);
          this.cargaEquipo.set(r.cargaEquipo);
          this.cargando.set(false);
          this.actualizando.set(false);
        },
        error: () => {
          this.cargando.set(false);
          this.actualizando.set(false);
        }
      });
  }

  cargarGraficos(): void {
    this.graficosSvc
      .graficos()
      .pipe(takeUntil(this.destruido))
      .subscribe(g => {
        if (g && (g.expedientesPorEstado.length > 0 || g.expedientesPorEspecialidad.length > 0)) {
          this.graficos.set(g);
        }
      });
  }
}
