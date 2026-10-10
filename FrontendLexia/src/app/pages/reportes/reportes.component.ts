import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  OnInit,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import {
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  of,
  switchMap,
  tap
} from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import {
  ItemReporteDetalle,
  OpcionReporte,
  PaginaReporte,
  ReporteService,
  ResumenReporteBackend
} from '../../core/services/reporte.service';
import { TemaService } from '../../core/services/tema.service';
import { PanelComponent } from '../panel/panel.component';
import { ReportesExcelService } from './excelreportes/reportes-excel.service';

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './reportes.component.html',
  styleUrl: './reportes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReportesComponent implements OnInit {
  readonly temaSvc = inject(TemaService);
  private readonly reporteSvc = inject(ReporteService);
  private readonly authSvc = inject(AuthService);
  private readonly excelSvc = inject(ReportesExcelService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly recargarResumen$ = new Subject<void>();
  private readonly recargarDetalle$ = new Subject<void>();
  private readonly busquedaDetalle$ = new Subject<string>();
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  readonly temaOscuro = this.temaSvc.oscuro;

  // ─── FILTROS ──────────────────────────────────────────────────────────────
  readonly presetRango = signal<string>('3meses');
  readonly fechaInicio = signal<string>(this.obtenerFecha3MesesAtras());
  readonly fechaFin = signal<string>(this.fechaLocal(new Date()));
  readonly filtroAbogado = signal<string>('todos');
  readonly filtroMateria = signal<string>('todas');
  readonly textoBusqueda = signal<string>('');

  // ─── RESUMEN / GRÁFICOS ──────────────────────────────────────────────────
  readonly datosBackend = signal<ResumenReporteBackend | null>(null);
  readonly cargando = signal(false);
  readonly errorCarga = signal<string | null>(null);

  // ─── DETALLE PAGINADO ────────────────────────────────────────────────────
  readonly tabla = signal<ItemReporteDetalle[]>([]);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal<string | null>(null);
  readonly paginaDetalle = signal(0);
  readonly tamanoDetalle = signal(20);
  readonly tamanosDetalle = [10, 20, 40, 100] as const;
  readonly totalElementosDetalle = signal(0);
  readonly totalPaginasDetalle = signal(0);

  readonly desdeDetalle = computed(() =>
    this.totalElementosDetalle() === 0
      ? 0
      : this.paginaDetalle() * this.tamanoDetalle() + 1
  );

  readonly hastaDetalle = computed(() =>
    Math.min(
      (this.paginaDetalle() + 1) * this.tamanoDetalle(),
      this.totalElementosDetalle()
    )
  );

  // ─── CATÁLOGOS LIGEROS DEL PROPIO MÓDULO ─────────────────────────────────
  readonly materias = signal<OpcionReporte[]>([]);
  readonly abogados = signal<OpcionReporte[]>([]);
  readonly cargandoCatalogos = signal(false);

  // ─── PERMISOS / EXPORTACIÓN ──────────────────────────────────────────────
  readonly puedeLeer = signal(this.authSvc.tienePermiso('REPORTE_LEER'));
  readonly puedeExportar = signal(this.authSvc.tienePermiso('REPORTE_EXPORTAR'));
  readonly modalExportarAbierto = signal(false);
  readonly formatoExportacion = signal<'excel' | 'pdf'>('excel');
  readonly exportando = signal(false);

  // ─── TOAST ────────────────────────────────────────────────────────────────
  readonly toastVisible = signal(false);
  readonly toastTexto = signal('');
  readonly toastError = signal(false);

  ngOnInit(): void {
    if (!this.puedeLeer()) return;
    this.configurarFlujosReactivos();
    this.cargarCatalogos();
    this.solicitarRecargaCompleta();
  }

  private configurarFlujosReactivos(): void {
    this.recargarResumen$
      .pipe(
        tap(() => {
          this.cargando.set(true);
          this.errorCarga.set(null);
        }),
        switchMap(() => {
          if (!this.rangoFechasValido()) {
            return of(null);
          }
          return this.reporteSvc.getResumen({
            ...this.filtrosBackend(),
            preset: this.presetRango()
          }).pipe(
            catchError(err => {
              console.error('Error al cargar resumen de reportes:', err);
              this.errorCarga.set('No se pudo cargar el reporte. Intenta de nuevo.');
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(resp => {
        this.cargando.set(false);
        if (resp) this.datosBackend.set(resp);
      });

    this.recargarDetalle$
      .pipe(
        tap(() => {
          this.cargandoDetalle.set(true);
          this.errorDetalle.set(null);
        }),
        switchMap(() => {
          if (!this.rangoFechasValido()) {
            return of(null);
          }
          return this.reporteSvc.getDetalle(
            this.filtrosBackend(),
            this.paginaDetalle(),
            this.tamanoDetalle(),
            this.textoBusqueda()
          ).pipe(
            catchError(err => {
              console.error('Error al cargar detalle de reportes:', err);
              this.errorDetalle.set('No se pudo cargar el detalle del reporte.');
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(resp => {
        this.cargandoDetalle.set(false);
        if (resp) this.aplicarPaginaDetalle(resp);
      });

    this.busquedaDetalle$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        this.paginaDetalle.set(0);
        this.recargarDetalle$.next();
      });

    this.destroyRef.onDestroy(() => {
      if (this.toastTimer) clearTimeout(this.toastTimer);
    });
  }

  cargarCatalogos(): void {
    this.cargandoCatalogos.set(true);
    this.reporteSvc.getCatalogos()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: res => {
          this.materias.set(res.materias ?? []);
          this.abogados.set(res.abogados ?? []);
          this.cargandoCatalogos.set(false);
        },
        error: err => {
          console.error('Error al cargar catálogos de reportes:', err);
          this.materias.set([]);
          this.abogados.set([]);
          this.cargandoCatalogos.set(false);
        }
      });
  }

  cargarDatos(): void {
    this.paginaDetalle.set(0);
    this.solicitarRecargaCompleta();
  }

  private solicitarRecargaCompleta(): void {
    this.recargarResumen$.next();
    this.recargarDetalle$.next();
  }

  private readonly abogadoIdActivo = computed<number | undefined>(() => {
    const v = this.filtroAbogado();
    return v === 'todos' ? undefined : Number(v);
  });

  readonly abogadoNombreSeleccionado = computed(() => {
    const id = this.abogadoIdActivo();
    if (!id) return 'Todos';
    return this.abogados().find(a => a.id === id)?.nombre ?? `#${id}`;
  });

  private readonly filtrosBackend = computed(() => ({
    fechaInicio: this.fechaInicio(),
    fechaFin: this.fechaFin(),
    materia: this.filtroMateria(),
    abogadoId: this.abogadoIdActivo()
  }));

  // ─── KPIs ─────────────────────────────────────────────────────────────────
  readonly kpis = computed(() => this.datosBackend()?.kpis ?? null);
  readonly kpiIngresos = computed(() => this.kpis()?.ingresosTotales ?? 0);
  readonly kpiCobrado = computed(() => this.kpis()?.cobradoTotal ?? 0);
  readonly kpiSaldo = computed(() => this.kpis()?.saldoPendiente ?? 0);
  readonly kpiTotal = computed(() => this.kpis()?.totalExpedientes ?? 0);
  readonly kpiTramite = computed(() => this.kpis()?.expedientesEnTramite ?? 0);
  readonly kpiCriticos = computed(() => this.kpis()?.expedientesVencidos ?? 0);
  readonly kpiCerrados = computed(() => this.kpis()?.expedientesConcluidos ?? 0);
  readonly kpiTareas = computed(() => this.kpis()?.tareasPendientes ?? 0);
  readonly kpiHoras = computed(() => this.kpis()?.horasFacturables ?? 0);
  readonly kpiMeses = computed(() => this.kpis()?.tiempoPromedioMeses ?? 0);

  readonly pctCerrados = computed(() => {
    const total = this.kpiTotal();
    return total > 0 ? Math.round((this.kpiCerrados() / total) * 100) : 0;
  });

  readonly pctCobranza = computed(() => {
    const facturado = this.kpiIngresos();
    return facturado > 0 ? Math.round((this.kpiCobrado() / facturado) * 100) : 0;
  });

  // ─── SERIES ────────────────────────────────────────────────────────────────
  readonly graficoPorMateria = computed(() => this.datosBackend()?.expedientesPorMateria ?? []);
  readonly graficoPorEstado = computed(() => this.datosBackend()?.expedientesPorEstado ?? []);
  readonly graficoIngresos = computed(() => this.datosBackend()?.ingresosPorMes ?? []);
  readonly cargaPorUsuario = computed(() => this.datosBackend()?.cargaPorUsuario ?? []);

  readonly totalPorMateria = computed(() =>
    this.graficoPorMateria().reduce((sum, m) => sum + m.total, 0)
  );

  readonly totalCarga = computed(() =>
    this.cargaPorUsuario().reduce((sum, u) => sum + u.valor, 0)
  );

  private readonly maxIngresos = computed(() => {
    const serie = this.graficoIngresos();
    return serie.length > 0 ? Math.max(...serie.map(b => b.valor)) : 0;
  });

  porcentajeSobreTotal(parte: number, total: number): number {
    return total > 0 ? Math.round((parte / total) * 100) : 0;
  }

  alturaBarra(valor: number): number {
    const max = this.maxIngresos();
    if (max <= 0) return 0;
    return Math.max(4, Math.round((valor / max) * 100));
  }

  etiquetaMes(mes: string): string {
    const [anio, m] = mes.split('-');
    const nombres = ['ene', 'feb', 'mar', 'abr', 'may', 'jun',
      'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    const idx = Number(m) - 1;
    return nombres[idx] ? `${nombres[idx]} ${anio.slice(2)}` : mes;
  }

  // ─── FILTROS ──────────────────────────────────────────────────────────────
  cambiarPresetRango(e: Event): void {
    const val = (e.target as HTMLSelectElement).value;
    this.presetRango.set(val);

    const hoy = new Date();
    const hoyIso = this.fechaLocal(hoy);
    if (val === 'mes') {
      this.fechaInicio.set(this.fechaLocal(new Date(hoy.getFullYear(), hoy.getMonth(), 1)));
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
    this.fechaFin.set(this.fechaLocal(new Date()));
    this.fechaInicio.set(this.obtenerFecha3MesesAtras());
    this.paginaDetalle.set(0);
    this.solicitarRecargaCompleta();
  }

  actualizarBusquedaDetalle(e: Event): void {
    const valor = (e.target as HTMLInputElement).value;
    this.textoBusqueda.set(valor);
    this.busquedaDetalle$.next(valor.trim());
  }

  cambiarTamanoDetalle(e: Event): void {
    const valor = Number((e.target as HTMLSelectElement).value);
    if (![10, 20, 40, 100].includes(valor) || valor === this.tamanoDetalle()) return;
    this.tamanoDetalle.set(valor);
    this.paginaDetalle.set(0);
    this.recargarDetalle$.next();
  }

  cambiarPaginaDetalle(delta: number): void {
    const nueva = this.paginaDetalle() + delta;
    if (this.cargandoDetalle() || nueva < 0 || nueva >= this.totalPaginasDetalle()) return;
    this.paginaDetalle.set(nueva);
    this.recargarDetalle$.next();
  }

  // ─── EXPORTACIÓN ──────────────────────────────────────────────────────────
  abrirModalExportar(): void {
    this.modalExportarAbierto.set(true);
  }

  cerrarModalExportar(): void {
    if (!this.exportando()) this.modalExportarAbierto.set(false);
  }

  ejecutarExportacion(): void {
    if (this.exportando() || !this.rangoFechasValido()) return;
    this.exportando.set(true);

    const filtros = this.filtrosBackend();

    if (this.formatoExportacion() === 'excel') {
      this.reporteSvc.getDatosExportacion(filtros)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: filas => {
            void this.excelSvc.exportar(filas, {
              fechaInicio: filtros.fechaInicio,
              fechaFin: filtros.fechaFin,
              materia: this.filtroMateria() === 'todas' ? 'Todas las materias' : this.filtroMateria(),
              abogado: this.abogadoNombreSeleccionado()
            })
              .then(() => {
                this.exportando.set(false);
                this.modalExportarAbierto.set(false);
                this.mostrarToast('Archivo XLSX generado correctamente.');
              })
              .catch(err => {
                console.error('Error al generar Excel:', err);
                this.exportando.set(false);
                this.mostrarToast('No se pudo generar el archivo Excel.', true);
              });
          },
          error: err => {
            console.error('Error al obtener datos para Excel:', err);
            this.exportando.set(false);
            this.mostrarToast('No se pudieron obtener los datos del reporte.', true);
          }
        });
      return;
    }

    this.reporteSvc.descargarPdf(filtros)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: blob => {
          this.exportando.set(false);
          this.modalExportarAbierto.set(false);
          this.descargarBlob(blob, `Reporte_Estudio_Lexia_${this.fechaArchivo(new Date())}.pdf`);
          this.mostrarToast('Archivo PDF descargado.');
        },
        error: err => {
          console.error('Error al exportar PDF:', err);
          this.exportando.set(false);
          this.mostrarToast('No se pudo generar el PDF. Revisa los filtros e intenta de nuevo.', true);
        }
      });
  }

  descargarBlob(blob: Blob, nombreArchivo: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nombreArchivo;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  // ─── UTILIDADES ───────────────────────────────────────────────────────────
  formatearMoneda(valor: number): string {
    return valor.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  formatearNumero(valor: number): string {
    return valor.toLocaleString('es-PE', { maximumFractionDigits: 1 });
  }

  iniciales(nombre: string): string {
    const partes = (nombre || '').trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return '';
    if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
    return (partes[0][0] + partes[1][0]).toUpperCase();
  }

  claseRiesgo(riesgo: string): string {
    return 'riesgo-' + (riesgo || 'sin').toLowerCase();
  }

  mostrarToast(mensaje: string, esError = false): void {
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toastTexto.set(mensaje);
    this.toastError.set(esError);
    this.toastVisible.set(true);
    this.toastTimer = setTimeout(() => {
      this.toastVisible.set(false);
      this.toastTimer = null;
    }, 4000);
  }

  private aplicarPaginaDetalle(resp: PaginaReporte<ItemReporteDetalle>): void {
    this.tabla.set(resp.contenido ?? []);
    this.paginaDetalle.set(resp.pagina ?? 0);
    this.tamanoDetalle.set(resp.tamano ?? 20);
    this.totalElementosDetalle.set(resp.totalElementos ?? 0);
    this.totalPaginasDetalle.set(resp.totalPaginas ?? 0);
  }

  private fechaArchivo(fecha: Date): string {
    const p = (numero: number) => String(numero).padStart(2, '0');
    return `${fecha.getFullYear()}-${p(fecha.getMonth() + 1)}-${p(fecha.getDate())}_${p(fecha.getHours())}-${p(fecha.getMinutes())}`;
  }

  private rangoFechasValido(): boolean {
    const inicio = this.fechaInicio();
    const fin = this.fechaFin();
    if (inicio && fin && inicio > fin) {
      this.cargando.set(false);
      this.cargandoDetalle.set(false);
      this.errorCarga.set('La fecha desde no puede ser posterior a la fecha hasta.');
      return false;
    }
    return true;
  }

  private obtenerFecha3MesesAtras(): string {
    const d = new Date();
    d.setMonth(d.getMonth() - 3);
    return this.fechaLocal(d);
  }

  private fechaLocal(fecha: Date): string {
    const y = fecha.getFullYear();
    const m = String(fecha.getMonth() + 1).padStart(2, '0');
    const d = String(fecha.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
