import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { PanelComponent } from '../panel/panel.component';
import { TemaService } from '../../core/services/tema.service';
import { AuthService } from '../../core/services/auth.service';
import {
  ItemReporteDetalle,
  ReporteService,
  ResumenReporteBackend
} from '../../core/services/reporte.service';

interface OpcionCatalogo {
  id: number;
  nombre: string;
  detalle?: string | null;
}

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './reportes.component.html',
  styleUrl: './reportes.component.css'
})
export class ReportesComponent implements OnInit {

  readonly temaSvc = inject(TemaService);
  private readonly reporteSvc = inject(ReporteService);
  private readonly authSvc = inject(AuthService);
  private readonly http = inject(HttpClient);

  readonly temaOscuro = this.temaSvc.oscuro;

  // ─── FILTROS ──────────────────────────────────────────────────────────────
  readonly presetRango   = signal<string>('3meses');
  readonly fechaInicio   = signal<string>(this.obtenerFecha3MesesAtras());
  readonly fechaFin      = signal<string>(new Date().toISOString().slice(0, 10));
  readonly filtroAbogado = signal<string>('todos');
  readonly filtroMateria = signal<string>('todas');
  readonly textoBusqueda = signal<string>('');

  // ─── DATOS DEL BACKEND ────────────────────────────────────────────────────
  readonly datosBackend = signal<ResumenReporteBackend | null>(null);
  readonly cargando     = signal<boolean>(false);
  readonly errorCarga   = signal<string | null>(null);

  // ─── CATÁLOGOS REALES (para los selects de materia y abogado) ────────────
  readonly materias  = signal<OpcionCatalogo[]>([]);
  readonly abogados = signal<OpcionCatalogo[]>([]);
  readonly cargandoCatalogos = signal<boolean>(false);

  // ─── PERMISOS ─────────────────────────────────────────────────────────────
  readonly puedeExportar = signal(this.authSvc.tienePermiso('REPORTE_EXPORTAR'));

  // ─── ESTADO DEL MODAL DE EXPORTACIÓN ─────────────────────────────────────
  readonly modalExportarAbierto = signal(false);
  readonly formatoExportacion   = signal<'excel' | 'pdf'>('excel');
  readonly exportando          = signal(false);

  // ─── TOAST ────────────────────────────────────────────────────────────────
  readonly toastVisible = signal(false);
  readonly toastTexto   = signal('');
  readonly toastError   = signal(false);

  ngOnInit(): void {
    this.cargarCatalogos();
    this.cargarDatos();
  }

  // ─── CARGA DE DATOS ───────────────────────────────────────────────────────
  cargarDatos(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);

    this.reporteSvc.getResumen({
      fechaInicio: this.fechaInicio(),
      fechaFin: this.fechaFin(),
      materia: this.filtroMateria(),
      abogadoId: this.filtroAbogado() === 'todos' ? undefined : Number(this.filtroAbogado()),
      preset: this.presetRango()
    }).subscribe({
      next: resp => {
        this.cargando.set(false);
        if (resp) {
          this.datosBackend.set(resp);
        } else {
          this.datosBackend.set(null);
          this.errorCarga.set('No se pudo cargar el reporte. Intenta de nuevo.');
        }
      },
      error: () => {
        this.cargando.set(false);
        this.datosBackend.set(null);
        this.errorCarga.set('No se pudo cargar el reporte. Intenta de nuevo.');
      }
    });
  }

  cargarCatalogos(): void {
    this.cargandoCatalogos.set(true);
    this.http.get<{ materias: OpcionCatalogo[]; usuarios: OpcionCatalogo[] }>(
      `${this.reporteSvc.baseUrlCatalogo}`
    ).subscribe({
      next: res => {
        this.materias.set(res.materias ?? []);
        this.abogados.set(res.usuarios ?? []);
        this.cargandoCatalogos.set(false);
      },
      error: () => {
        this.materias.set([]);
        this.abogados.set([]);
        this.cargandoCatalogos.set(false);
      }
    });
  }

  // ─── FILTROS DERIVADOS ────────────────────────────────────────────────────
  private readonly abogadoIdActivo = computed<number | undefined>(() => {
    const v = this.filtroAbogado();
    return v === 'todos' ? undefined : Number(v);
  });

  readonly abogadoNombreSeleccionado = computed(() => {
    const id = this.abogadoIdActivo();
    if (!id) return 'Todos';
    return this.abogados().find(a => a.id === id)?.nombre ?? `#${id}`;
  });

  /** Porcentaje de una parte sobre un total, protegido contra division por 0. */
  porcentajeSobreTotal(parte: number, total: number): number {
    return total > 0 ? Math.round((parte / total) * 100) : 0;
  }

  readonly totalPorMateria = computed(() =>
    this.graficoPorMateria().reduce((sum, m) => sum + m.total, 0)
  );

  readonly totalCarga = computed(() =>
    this.cargaPorUsuario().reduce((sum, u) => sum + u.valor, 0)
  );

  private readonly filtrosBackend = computed(() => ({
    fechaInicio: this.fechaInicio(),
    fechaFin: this.fechaFin(),
    materia: this.filtroMateria(),
    abogadoId: this.abogadoIdActivo()
  }));

  // ─── KPIs (solo datos reales; sin valores de relleno) ─────────────────────
  readonly kpis = computed(() => this.datosBackend()?.kpis ?? null);

  readonly kpiIngresos = computed(() => this.kpis()?.ingresosTotales ?? 0);
  readonly kpiCobrado  = computed(() => this.kpis()?.cobradoTotal ?? 0);
  readonly kpiSaldo    = computed(() => this.kpis()?.saldoPendiente ?? 0);
  readonly kpiTotal    = computed(() => this.kpis()?.totalExpedientes ?? 0);
  readonly kpiTramite  = computed(() => this.kpis()?.expedientesEnTramite ?? 0);
  readonly kpiCriticos = computed(() => this.kpis()?.expedientesVencidos ?? 0);
  readonly kpiCerrados = computed(() => this.kpis()?.expedientesConcluidos ?? 0);
  readonly kpiTareas   = computed(() => this.kpis()?.tareasPendientes ?? 0);
  readonly kpiHoras    = computed(() => this.kpis()?.horasFacturables ?? 0);
  readonly kpiMeses    = computed(() => this.kpis()?.tiempoPromedioMeses ?? 0);

  /** Cierre real: sobre el total filtrado, no un porcentaje inventado. */
  readonly pctCerrados = computed(() => {
    const total = this.kpiTotal();
    return total > 0 ? Math.round((this.kpiCerrados() / total) * 100) : 0;
  });

  /** Coleccion real: cobrado sobre facturado. */
  readonly pctCobranza = computed(() => {
    const f = this.kpiIngresos();
    return f > 0 ? Math.round((this.kpiCobrado() / f) * 100) : 0;
  });

  // ─── SERIES PARA GRÁFICOS ─────────────────────────────────────────────────
  readonly graficoPorMateria = computed(() => {
    const datos = this.datosBackend();
    if (!datos) return [];
    return datos.expedientesPorMateria;
  });

  readonly graficoPorEstado = computed(() => this.datosBackend()?.expedientesPorEstado ?? []);

  readonly graficoIngresos = computed(() => this.datosBackend()?.ingresosPorMes ?? []);

  readonly cargaPorUsuario = computed(() => this.datosBackend()?.cargaPorUsuario ?? []);

  /** Escala comun para que las barras del grafico sean proporcionales de verdad. */
  private readonly maxIngresos = computed(() => {
    const serie = this.graficoIngresos();
    return serie.length > 0 ? Math.max(...serie.map(b => b.valor)) : 0;
  });

  alturaBarra(valor: number): number {
    const max = this.maxIngresos();
    if (max <= 0) return 0;
    return Math.max(4, Math.round((valor / max) * 100));
  }

  /** "2026-06" -> "jun 26" */
  etiquetaMes(mes: string): string {
    const [anio, m] = mes.split('-');
    const nombres = ['ene', 'feb', 'mar', 'abr', 'may', 'jun',
                     'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    const idx = Number(m) - 1;
    return nombres[idx] ? `${nombres[idx]} ${anio.slice(2)}` : mes;
  }

  // ─── TABLA DE DETALLE (búsqueda en cliente, sobre datos ya cargados) ──────
  readonly tabla = computed<ItemReporteDetalle[]>(() => {
    const detalle = this.datosBackend()?.tablaDetalle ?? [];
    const q = this.textoBusqueda().toLowerCase().trim();
    if (!q) return detalle;
    return detalle.filter(d =>
      d.codigo.toLowerCase().includes(q) ||
      d.cliente.toLowerCase().includes(q) ||
      d.materia.toLowerCase().includes(q) ||
      d.abogado.toLowerCase().includes(q) ||
      d.estado.toLowerCase().includes(q)
    );
  });

  // ─── MANEJO DE FILTROS ────────────────────────────────────────────────────
  cambiarPresetRango(e: Event): void {
    const val = (e.target as HTMLSelectElement).value;
    this.presetRango.set(val);

    const hoy = new Date();
    const hoyIso = hoy.toISOString().slice(0, 10);

    if (val === 'mes') {
      this.fechaInicio.set(
        new Date(hoy.getFullYear(), hoy.getMonth(), 1).toISOString().slice(0, 10));
      this.fechaFin.set(hoyIso);
    } else if (val === '3meses') {
      this.fechaInicio.set(this.obtenerFecha3MesesAtras());
      this.fechaFin.set(hoyIso);
    } else if (val === 'anio') {
      this.fechaInicio.set(`${hoy.getFullYear()}-01-01`);
      this.fechaFin.set(hoyIso);
    } else if (val === 'todo') {
      this.fechaInicio.set('2000-01-01');
      this.fechaFin.set(hoyIso);
    }
    this.cargarDatos();
  }

  actualizarFechaInicio(e: Event): void {
    this.fechaInicio.set((e.target as HTMLInputElement).value);
    this.presetRango.set('custom');
    this.cargarDatos();
  }

  actualizarFechaFin(e: Event): void {
    this.fechaFin.set((e.target as HTMLInputElement).value);
    this.presetRango.set('custom');
    this.cargarDatos();
  }

  cambiarAbogado(e: Event): void {
    this.filtroAbogado.set((e.target as HTMLSelectElement).value);
    this.cargarDatos();
  }

  cambiarMateria(e: Event): void {
    this.filtroMateria.set((e.target as HTMLSelectElement).value);
    this.cargarDatos();
  }

  limpiarFiltros(): void {
    this.textoBusqueda.set('');
    this.filtroAbogado.set('todos');
    this.filtroMateria.set('todas');
    this.presetRango.set('3meses');
    const hoy = new Date().toISOString().slice(0, 10);
    this.fechaFin.set(hoy);
    this.fechaInicio.set(this.obtenerFecha3MesesAtras());
    this.cargarDatos();
  }

  // ─── EXPORTACIÓN (solo backend; sin fallback a datos del front) ───────────
  abrirModalExportar(): void {
    this.modalExportarAbierto.set(true);
  }

  cerrarModalExportar(): void {
    if (!this.exportando()) this.modalExportarAbierto.set(false);
  }

  ejecutarExportacion(): void {
    if (this.exportando()) return;
    this.exportando.set(true);

    const filtros = this.filtrosBackend();
    const petition = this.formatoExportacion() === 'excel'
      ? this.reporteSvc.descargarExcel(filtros)
      : this.reporteSvc.descargarPdf(filtros);

    petition.subscribe({
      next: blob => {
        this.exportando.set(false);
        this.modalExportarAbierto.set(false);
        if (!blob) {
          this.mostrarToast('No se pudo generar el archivo. Intenta de nuevo.', true);
          return;
        }
        const ext = this.formatoExportacion() === 'excel' ? 'xls' : 'pdf';
        this.descargarBlob(blob, `Reporte_Estudio_Lexia.${ext}`);
        this.mostrarToast(`Archivo ${ext.toUpperCase()} descargado.`);
      },
      error: () => {
        this.exportando.set(false);
        this.mostrarToast('No se pudo generar el archivo.', true);
      }
    });
  }

  descargarBlob(blob: Blob, nombreArchivo: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nombreArchivo;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ─── UTILIDADES DE FORMATO ────────────────────────────────────────────────
  formatearMoneda(valor: number): string {
    return valor.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  formatearNumero(valor: number): string {
    return valor.toLocaleString('es-PE', { maximumFractionDigits: 1 });
  }

  iniciales(nombre: string): string {
    const partes = nombre.trim().split(/\s+/);
    if (partes.length === 0) return '';
    if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
    return (partes[0][0] + partes[1][0]).toUpperCase();
  }

  claseRiesgo(riesgo: string): string {
    return 'riesgo-' + (riesgo || 'sin').toLowerCase();
  }

  // ─── TOAST ────────────────────────────────────────────────────────────────
  mostrarToast(mensaje: string, esError = false): void {
    this.toastTexto.set(mensaje);
    this.toastError.set(esError);
    this.toastVisible.set(true);
    setTimeout(() => this.toastVisible.set(false), 4000);
  }

  private obtenerFecha3MesesAtras(): string {
    const d = new Date();
    d.setMonth(d.getMonth() - 3);
    return d.toISOString().slice(0, 10);
  }
}
