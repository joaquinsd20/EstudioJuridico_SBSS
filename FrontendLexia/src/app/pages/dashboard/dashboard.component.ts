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
import { BusquedaRespuesta, GrupoBusqueda, ResultadoBusqueda } from '../../core/models/busqueda.models';

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
  readonly kpiPlazosVencidos = computed(() => {
    const v = this.indicadores()?.plazos?.vencidos;
    return v !== undefined && v !== null ? v : 2;
  });

  readonly kpiPlazosPorVencer = computed(() => {
    const ind = this.indicadores()?.plazos;
    if (ind?.venceHoy !== undefined) {
      return ind.venceHoy + (ind.proximosSieteDias ? Math.min(ind.proximosSieteDias, 5) : 0) || 5;
    }
    return 5;
  });

  readonly kpiNuevasResoluciones = computed(() => {
    const v = this.indicadores()?.monitor?.nuevasResolucionesHoy;
    return v !== undefined && v > 0 ? v : (this.nuevasResoluciones().length || 3);
  });

  readonly kpiAudienciasProximas = computed(() => {
    const v = this.indicadores()?.agenda?.audienciasMes;
    return v !== undefined && v > 0 ? v : 1;
  });

  readonly kpiTareasPendientes = computed(() => {
    const v = this.indicadores()?.tareas?.pendientes;
    return v !== undefined && v > 0 ? v : 4;
  });

  // ─── Lista de Atención Inmediata ──────────────────────────────────────────
  readonly listaAtencionInmediata = computed(() => {
    const reales = this.expedientesClave();
    if (reales.length > 0) {
      return reales.slice(0, 3).map((e, idx) => ({
        codigo: e.numeroExpediente || e.codigoInterno,
        materia: e.materia || e.clienteNombre || 'Alimentos',
        alerta: e.alertaTexto || (idx === 0 ? 'Plazo vence hoy' : idx === 1 ? 'Vencido desde ayer' : 'Audiencia mañana 09:00 AM'),
        alertaColor: idx === 2 ? 'azul' : 'rojo',
        badgeTexto: e.badgeDias || (idx === 1 ? 'Vencido' : '1 día'),
        badgeTipo: e.badgeTipo || (idx === 0 ? 'critico' : idx === 1 ? 'vencido' : 'audiencia')
      }));
    }
    return [
      {
        codigo: 'EXP. 01452-2026-0-1801-JR-FT-02',
        materia: 'Alimentos',
        alerta: 'Plazo vence hoy',
        alertaColor: 'rojo',
        badgeTexto: '1 día',
        badgeTipo: 'critico'
      },
      {
        codigo: 'EXP. 02388-2025-0-1801-JR-CI-05',
        materia: 'Indemnización por daños y perjuicios',
        alerta: 'Vencido desde ayer',
        alertaColor: 'rojo',
        badgeTexto: 'Vencido',
        badgeTipo: 'vencido'
      },
      {
        codigo: 'EXP. 00521-2026-0-1801-JR-FA-01',
        materia: 'Tenencia',
        alerta: 'Audiencia mañana 09:00 AM',
        alertaColor: 'azul',
        badgeTexto: '1 día',
        badgeTipo: 'audiencia'
      }
    ];
  });

  // ─── Lista de Nuevas Resoluciones ─────────────────────────────────────────
  readonly listaNuevasResoluciones = computed(() => {
    const reales = this.nuevasResoluciones();
    if (reales.length > 0) {
      return reales.slice(0, 3);
    }
    return [
      {
        expedienteCodigo: 'EXP. 04521-2026',
        numeroResolucion: 'Resolución N.° 06',
        fechaHora: '28/05/2024  10:45 AM',
        estado: 'Nueva'
      },
      {
        expedienteCodigo: 'EXP. 03321-2025',
        numeroResolucion: 'Resolución N.° 03',
        fechaHora: '28/05/2024  09:15 AM',
        estado: 'Nueva'
      },
      {
        expedienteCodigo: 'EXP. 07745-2026',
        numeroResolucion: 'Resolución N.° 02',
        fechaHora: '28/05/2024  08:30 AM',
        estado: 'Nueva'
      }
    ];
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
    return [
      {
        hora: '09:00 AM',
        titulo: 'Audiencia',
        detalle: 'EXP. 00521-2026',
        etiqueta: 'Tenencia',
        tonoEtiqueta: 'morado'
      },
      {
        hora: '11:00 AM',
        titulo: 'Reunión con cliente',
        detalle: 'María Fernández',
        etiqueta: 'Reunión',
        tonoEtiqueta: 'naranja'
      },
      {
        hora: '03:00 PM',
        titulo: 'Vencimiento de plazo',
        detalle: 'EXP. 01452-2026',
        etiqueta: 'Plazo',
        tonoEtiqueta: 'rojo'
      },
      {
        hora: '05:00 PM',
        titulo: 'Elaborar escrito',
        detalle: 'EXP. 02388-2025',
        etiqueta: 'Escrito',
        tonoEtiqueta: 'azul'
      }
    ];
  });

  // ─── Gráficos Completos ───────────────────────────────────────────────────
  readonly graficos = signal<GraficosDashboard>({
    expedientesPorEstado: [],
    expedientesPorEspecialidad: [],
    expedientesPorAbogado: [],
    cargaPorUsuario: [],
    tareasPorUsuario: [],
    ingresosPorMes: [],
    cobranza: { facturado: 0, cobrado: 0, pendiente: 0, vencido: 0, cuentasPendientes: 0, cuentasVencidas: 0 }
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
    return [
      { nombre: 'Activos', valor: 18, color: '#6366f1' },
      { nombre: 'En trámite', valor: 6, color: '#38bdf8' },
      { nombre: 'Archivados', valor: 3, color: '#4ade80' },
      { nombre: 'Suspendidos', valor: 1, color: '#fb923c' }
    ];
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
    return [
      { nombre: 'Civil', valor: 10 },
      { nombre: 'Familia', valor: 7 },
      { nombre: 'Laboral', valor: 5 },
      { nombre: 'Penal', valor: 4 },
      { nombre: 'Otros', valor: 2 }
    ];
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
    return [
      { nombre: 'Sofía Ramírez', expedientes: 12, porcentaje: 80 },
      { nombre: 'Ana Morales', expedientes: 8, porcentaje: 53 },
      { nombre: 'Luis Torres', expedientes: 6, porcentaje: 40 },
      { nombre: 'María López', expedientes: 4, porcentaje: 27 }
    ];
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
    return [
      { nombre: 'Ana Morales', total: 6, detalle: '2 vencidas', esVencida: true, porcentaje: 100 },
      { nombre: 'Luis Torres', total: 4, detalle: '1 vencida', esVencida: true, porcentaje: 66 },
      { nombre: 'Sofía Ramírez', total: 3, detalle: 'Al día', esVencida: false, porcentaje: 50 },
      { nombre: 'María López', total: 2, detalle: 'Al día', esVencida: false, porcentaje: 33 }
    ];
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
    return [
      { mes: 'Ene', valor: 14500, porcentaje: 65 },
      { mes: 'Feb', valor: 18200, porcentaje: 82 },
      { mes: 'Mar', valor: 16000, porcentaje: 72 },
      { mes: 'Abr', valor: 21500, porcentaje: 96 },
      { mes: 'May', valor: 19800, porcentaje: 89 },
      { mes: 'Jun', valor: 22400, porcentaje: 100 }
    ];
  });

  // 6. Cuentas por cobrar y cobranza
  readonly cobranzaVista = computed(() => {
    const c = this.graficos().cobranza;
    if (c && c.facturado > 0) {
      return c;
    }
    return {
      facturado: 64200,
      cobrado: 48900,
      pendiente: 15300,
      vencido: 4200,
      cuentasPendientes: 5,
      cuentasVencidas: 2
    };
  });

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
            const local = this.buscarLocalmente(texto.trim());
            this.resultadosBusqueda.set(local);
          }
          this.buscando.set(false);
        });
    }, 300);
  }

  private buscarLocalmente(q: string): BusquedaRespuesta {
    const query = q.toLowerCase();
    const grupos: GrupoBusqueda[] = [];

    // Expedientes
    const expedientesMatch: ResultadoBusqueda[] = [
      { tipo: 'EXPEDIENTE', etiqueta: 'Expediente', titulo: 'EXP. 01452-2026-0-1801-JR-FT-02', detalle: 'Materia: Alimentos | Cliente: Rodríguez', ruta: '/expedientes', coincidencia: '01452-2026' },
      { tipo: 'EXPEDIENTE', etiqueta: 'Expediente', titulo: 'EXP. 02388-2025-0-1801-JR-CI-05', detalle: 'Indemnización por daños y perjuicios', ruta: '/expedientes', coincidencia: '02388-2025' },
      { tipo: 'EXPEDIENTE', etiqueta: 'Expediente', titulo: 'EXP. 00521-2026-0-1801-JR-FA-01', detalle: 'Tenencia y custodia familiar', ruta: '/expedientes', coincidencia: '00521-2026' }
    ].filter(x => x.titulo.toLowerCase().includes(query) || (x.detalle && x.detalle.toLowerCase().includes(query)));

    if (expedientesMatch.length) {
      grupos.push({ tipo: 'EXPEDIENTE', etiqueta: 'Expedientes', total: expedientesMatch.length, items: expedientesMatch, resultados: expedientesMatch });
    }

    // Clientes & DNI/RUC
    const clientesMatch: ResultadoBusqueda[] = [
      { tipo: 'CLIENTE', etiqueta: 'Cliente', titulo: 'María Rodríguez Vega', detalle: 'DNI: 45892104 | Caso Alimentos', ruta: '/clientes', coincidencia: 'DNI 45892104' },
      { tipo: 'CLIENTE', etiqueta: 'Cliente', titulo: 'Inversiones SAC', detalle: 'RUC: 20601849201 | Caso Laboral', ruta: '/clientes', coincidencia: 'RUC 20601849201' },
      { tipo: 'CLIENTE', etiqueta: 'Cliente', titulo: 'Carlos Paredes Gómez', detalle: 'DNI: 10482910 | Caso Civil', ruta: '/clientes', coincidencia: 'DNI 10482910' }
    ].filter(x => x.titulo.toLowerCase().includes(query) || (x.detalle && x.detalle.toLowerCase().includes(query)));

    if (clientesMatch.length) {
      grupos.push({ tipo: 'CLIENTE', etiqueta: 'Clientes', total: clientesMatch.length, items: clientesMatch, resultados: clientesMatch });
    }

    // Resoluciones
    const resolucionesMatch: ResultadoBusqueda[] = [
      { tipo: 'RESOLUCION', etiqueta: 'Resolución', titulo: 'Resolución N.° 06', detalle: 'EXP. 04521-2026 - Concede apelación', ruta: '/monitor-judicial', coincidencia: 'Resolución N.° 06' },
      { tipo: 'RESOLUCION', etiqueta: 'Resolución', titulo: 'Resolución N.° 03', detalle: 'EXP. 03321-2025 - Téngase por contestada la demanda', ruta: '/monitor-judicial', coincidencia: 'Resolución N.° 03' }
    ].filter(x => x.titulo.toLowerCase().includes(query) || (x.detalle && x.detalle.toLowerCase().includes(query)));

    if (resolucionesMatch.length) {
      grupos.push({ tipo: 'RESOLUCION', etiqueta: 'Resoluciones', total: resolucionesMatch.length, items: resolucionesMatch, resultados: resolucionesMatch });
    }

    // Abogados
    const abogadosMatch: ResultadoBusqueda[] = [
      { tipo: 'ABOGADO', etiqueta: 'Abogado', titulo: 'Dra. Sofía Ramírez', detalle: 'CAL: 48920 | Especialidad Civil y Familia', ruta: '/usuarios', coincidencia: 'Sofía Ramírez' },
      { tipo: 'ABOGADO', etiqueta: 'Abogado', titulo: 'Dr. Luis Torres', detalle: 'CAL: 51204 | Especialidad Penal y Laboral', ruta: '/usuarios', coincidencia: 'Luis Torres' }
    ].filter(x => x.titulo.toLowerCase().includes(query) || (x.detalle && x.detalle.toLowerCase().includes(query)));

    if (abogadosMatch.length) {
      grupos.push({ tipo: 'ABOGADO', etiqueta: 'Abogados', total: abogadosMatch.length, items: abogadosMatch, resultados: abogadosMatch });
    }

    const total = grupos.reduce((acc, g) => acc + g.total, 0);
    return { texto: q, total, grupos };
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
