import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, finalize, forkJoin, takeUntil } from 'rxjs';
import {
  CalculoPlazo,
  EstadoPlazo,
  OrigenPlazo,
  Plazo,
  PlazoAnalitica,
  PlazoCatalogos,
  PlazoDetalle,
  PlazoFiltro,
  PlazoGuardar,
  PlazoRelaciones,
  PlazoResumen,
  RiesgoPlazo,
  TipoComputoPlazo
} from '../../core/models/plazo.models';
import { AuthService } from '../../core/services/auth.service';
import { PlazoService } from '../../core/services/plazo.service';
import { PanelComponent } from '../panel/panel.component';

interface AlertaOpcion {
  dias: number;
  etiqueta: string;
}

interface BarraSemana {
  etiqueta: string;
  rojo: number;
  naranja: number;
  amarillo: number;
}

interface EvolucionMes {
  etiqueta: string;
  vencidos: number;
  cumplidos: number;
}

@Component({
  selector: 'app-plazos',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './plazos.component.html',
  styleUrl: './plazos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PlazosComponent implements OnInit, OnDestroy {
  private readonly plazoService = inject(PlazoService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly puedeLeer = this.auth.tienePermiso('PLAZO_LEER');
  readonly puedeGestionar = this.auth.tienePermiso('PLAZO_CREAR');
  readonly puedeAprobar = this.auth.tienePermiso('PLAZO_APROBAR');

  readonly estados: EstadoPlazo[] = [
    'PENDIENTE_CALCULO', 'CALCULADO', 'EN_PREPARACION', 'EN_REVISION', 'LISTO_PRESENTAR',
    'PRESENTADO', 'CUMPLIDO', 'VENCIDO', 'SUSPENDIDO', 'CANCELADO'
  ];
  readonly riesgos: RiesgoPlazo[] = ['BAJO', 'MEDIO', 'ALTO', 'CRITICO'];
  readonly computos: TipoComputoPlazo[] = ['HABILES', 'CALENDARIO'];
  readonly origenes: OrigenPlazo[] = ['MANUAL', 'RESOLUCION', 'NOTIFICACION', 'ACTUACION', 'MONITOR_JUDICIAL'];
  readonly opcionesAlertas: AlertaOpcion[] = [
    { dias: 15, etiqueta: '15 días antes' },
    { dias: 10, etiqueta: '10 días antes' },
    { dias: 7, etiqueta: '7 días antes' },
    { dias: 5, etiqueta: '5 días antes' },
    { dias: 3, etiqueta: '3 días antes' },
    { dias: 1, etiqueta: '1 día antes' },
    { dias: 0, etiqueta: 'Mismo día' },
    { dias: -1, etiqueta: '1 día después' },
    { dias: -3, etiqueta: '3 días después' },
    { dias: -5, etiqueta: '5 días después' },
    { dias: -7, etiqueta: '7 días después' }
  ];

  plazos: Plazo[] = [];
  detalle: PlazoDetalle | null = null;
  catalogos: PlazoCatalogos = { expedientes: [], usuarios: [], tiposPlazo: [] };
  relaciones: PlazoRelaciones = { resoluciones: [], actuaciones: [], documentos: [] };
  resumen: PlazoResumen = {
    totalActivos: 0,
    vencidos: 0,
    venceHoy: 0,
    proximosSieteDias: 0,
    criticos: 0,
    revisionProfesional: 0
  };
  calculo: CalculoPlazo | null = null;

  readonly tamanosPagina = [10, 20, 40, 100];
  pagina = 0;
  tamanoPagina = 10;
  haySiguiente = false;

  analitica: PlazoAnalitica = { semanas: [], evolucion: [] };

  private readonly destruir$ = new Subject<void>();
  private readonly filtroTexto$ = new Subject<string>();

  // Filtros visibles de la interfaz original.
  filtroTipoPlazoId: number | null = null;
  filtroJuzgado = '';
  filtroFechaNotificacion = '';
  filtroFechaVencimiento = '';

  private filtros: PlazoFiltro = {
    texto: '',
    expedienteId: null,
    responsableId: null,
    tipoPlazoId: null,
    estado: '',
    riesgo: '',
    computo: '',
    requiereRevision: null,
    desde: '',
    hasta: '',
    fechaNotificacion: '',
    limite: this.tamanoPagina,
    desplazamiento: 0
  };

  formulario: PlazoGuardar = this.formularioVacio();
  editandoUuid: string | null = null;
  modalFormulario = false;
  cargando = false;
  guardando = false;
  calculando = false;
  error = '';
  exito = '';
  private mensajeTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.filtroTexto$
      .pipe(debounceTime(350), distinctUntilChanged(), takeUntil(this.destruir$))
      .subscribe(() => this.aplicarFiltros(true));

    if (this.puedeLeer) this.cargarInicial();
  }

  ngOnDestroy(): void {
    this.destruir$.next();
    this.destruir$.complete();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
  }

  onFiltroTextoInput(): void {
    this.filtroTexto$.next(this.filtroJuzgado.trim());
  }

  cargarInicial(): void {
    this.pagina = 0;
    this.sincronizarFiltros();
    this.cargando = true;
    forkJoin({
      plazos: this.plazoService.listar(this.filtros),
      resumen: this.plazoService.resumen(),
      analitica: this.plazoService.analitica(),
      catalogos: this.plazoService.catalogos()
    }).pipe(finalize(() => {
      this.cargando = false;
      this.refrescarVista();
    })).subscribe({
      next: r => {
        this.plazos = r.plazos;
        this.haySiguiente = r.plazos.length === this.tamanoPagina;
        this.resumen = r.resumen;
        this.analitica = r.analitica;
        this.catalogos = r.catalogos;
        const primero = this.plazosVista[0];
        if (primero) this.seleccionarPlazo(primero, false);
      },
      error: e => this.mostrarError(e)
    });
  }

  aplicarFiltros(reiniciarPagina = true): void {
    if (reiniciarPagina) this.pagina = 0;
    this.sincronizarFiltros();
    this.cargarPagina();
  }

  limpiarFiltros(): void {
    this.filtroTipoPlazoId = null;
    this.filtroJuzgado = '';
    this.filtroFechaNotificacion = '';
    this.filtroFechaVencimiento = '';
    this.pagina = 0;
    this.aplicarFiltros(false);
  }

  cambiarTamanoPagina(valor: number | string): void {
    this.tamanoPagina = Number(valor) || 20;
    this.pagina = 0;
    this.aplicarFiltros(false);
  }

  paginaAnterior(): void {
    if (this.pagina === 0 || this.cargando) return;
    this.pagina--;
    this.aplicarFiltros(false);
  }

  paginaSiguiente(): void {
    if (!this.haySiguiente || this.cargando) return;
    this.pagina++;
    this.aplicarFiltros(false);
  }

  private sincronizarFiltros(): void {
    this.filtros = {
      ...this.filtros,
      tipoPlazoId: this.filtroTipoPlazoId,
      texto: this.filtroJuzgado.trim(),
      fechaNotificacion: this.filtroFechaNotificacion || '',
      desde: this.filtroFechaVencimiento || '',
      hasta: this.filtroFechaVencimiento || '',
      limite: this.tamanoPagina,
      desplazamiento: this.pagina * this.tamanoPagina
    };
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.plazoService.listar(this.filtros)
      .pipe(finalize(() => {
        this.cargando = false;
        this.refrescarVista();
      }))
      .subscribe({
        next: r => {
          this.plazos = r;
          this.haySiguiente = r.length === this.tamanoPagina;
          if (this.pagina > 0 && r.length === 0) {
            this.pagina--;
            this.aplicarFiltros(false);
            return;
          }
          if (r.length && !r.some(p => p.uuid === this.detalle?.plazo.uuid)) {
            this.seleccionarPlazo(r[0], false);
          } else if (!r.length) {
            this.detalle = null;
          }
        },
        error: e => this.mostrarError(e)
      });
  }

  seleccionarPlazo(plazo: Plazo, mostrarCarga = true): void {
    if (mostrarCarga) this.cargando = true;
    this.plazoService.detalle(plazo.uuid)
      .pipe(finalize(() => {
        if (mostrarCarga) this.cargando = false;
        this.refrescarVista();
      }))
      .subscribe({
        next: d => this.detalle = d,
        error: e => this.mostrarError(e)
      });
  }

  cerrarDetalle(): void {
    this.detalle = null;
  }

  abrirNuevo(): void {
    this.editandoUuid = null;
    this.formulario = this.formularioVacio();
    this.relaciones = { resoluciones: [], actuaciones: [], documentos: [] };
    this.calculo = null;
    this.modalFormulario = true;
  }

  abrirEditarSeleccionado(): void {
    if (this.detalle) this.abrirEditar(this.detalle.plazo);
  }

  abrirEditar(plazo: Plazo): void {
    if (!this.puedeGestionar) return;
    this.cargando = true;
    forkJoin({
      detalle: this.plazoService.detalle(plazo.uuid),
      relaciones: this.plazoService.relaciones(plazo.expedienteId)
    }).pipe(finalize(() => {
      this.cargando = false;
      this.refrescarVista();
    })).subscribe({
      next: r => {
        this.editandoUuid = plazo.uuid;
        this.detalle = r.detalle;
        this.relaciones = r.relaciones;
        const p = r.detalle.plazo;
        this.formulario = {
          expedienteId: p.expedienteId,
          actuacionOrigenId: p.actuacionOrigenId,
          resolucionId: p.resolucionId,
          tipoPlazoId: p.tipoPlazoId,
          origen: p.origen,
          descripcion: p.descripcion,
          fechaNotificacion: p.fechaNotificacion,
          diasPlazo: p.diasPlazo,
          tipoComputo: p.tipoComputo,
          fechaRevisionInterna: p.fechaRevisionInterna,
          responsableId: p.responsableId,
          revisorId: p.revisorId,
          estado: p.estado,
          nivelRiesgo: p.nivelRiesgo,
          requiereRevisionProfesional: p.requiereRevisionProfesional,
          motivoRevision: p.motivoRevision ?? '',
          actuacionRequerida: p.actuacionRequerida ?? '',
          documentoId: p.documentoId,
          observaciones: p.observaciones ?? '',
          alertasDias: r.detalle.alertas
            .filter(a => a.diasAntes !== null && a.estado !== 'CANCELADA')
            .map(a => a.diasAntes as number)
        };
        this.calculo = {
          fechaInicioComputo: p.fechaInicioComputo,
          fechaVencimiento: p.fechaVencimiento,
          fechaRevisionInternaSugerida: p.fechaRevisionInterna ?? p.fechaVencimiento,
          distritoJudicial: p.distritoJudicial,
          requiereRevisionSugerida: p.requiereRevisionProfesional,
          advertencias: p.motivoRevision ? [p.motivoRevision] : []
        };
        this.modalFormulario = true;
      },
      error: e => this.mostrarError(e)
    });
  }

  cerrarFormulario(): void {
    if (this.guardando) return;
    this.modalFormulario = false;
    this.calculo = null;
  }

  onExpedienteChange(): void {
    this.relaciones = { resoluciones: [], actuaciones: [], documentos: [] };
    this.formulario.resolucionId = null;
    this.formulario.actuacionOrigenId = null;
    this.formulario.documentoId = null;
    this.calculo = null;
    const expediente = this.expedienteSeleccionado;
    if (!expediente) return;
    if (!this.editandoUuid) this.formulario.responsableId = expediente.responsableId;
    this.plazoService.relaciones(expediente.id).subscribe({
      next: r => {
        this.relaciones = r;
        this.refrescarVista();
      },
      error: e => this.mostrarError(e)
    });
  }

  onOrigenChange(): void {
    if (this.formulario.origen !== 'RESOLUCION') this.formulario.resolucionId = null;
    if (this.formulario.origen !== 'ACTUACION') this.formulario.actuacionOrigenId = null;
  }

  onTipoPlazoChange(): void {
    const tipo = this.catalogos.tiposPlazo.find(t => t.id === this.formulario.tipoPlazoId);
    if (!tipo) return;
    if (!this.editandoUuid || !this.formulario.diasPlazo) this.formulario.diasPlazo = tipo.diasPorDefecto;
    if (!this.editandoUuid) {
      this.formulario.tipoComputo = tipo.tipoComputoDefecto;
      this.formulario.nivelRiesgo = tipo.nivelRiesgoDefecto;
      if (!this.formulario.descripcion) this.formulario.descripcion = tipo.nombre;
    }
    this.calculo = null;
  }

  onResolucionChange(): void {
    const resolucion = this.relaciones.resoluciones.find(r => r.id === this.formulario.resolucionId);
    if (!resolucion) return;
    this.formulario.origen = 'RESOLUCION';
    this.formulario.fechaNotificacion = resolucion.fechaNotificacion;
    if (!this.formulario.descripcion) {
      this.formulario.descripcion = `${resolucion.numero}${resolucion.sumilla ? ' · ' + resolucion.sumilla : ''}`;
    }
    if (resolucion.estadoVerificacion !== 'VERIFICADA') {
      this.formulario.requiereRevisionProfesional = true;
      this.agregarMotivo('Notificación o resolución pendiente de verificación');
    }
    this.calculo = null;
  }

  recalcular(): void {
    const f = this.formulario;
    if (!f.expedienteId || !f.fechaNotificacion || !f.diasPlazo || !f.tipoComputo) {
      this.mostrarMensaje('error', 'Completa expediente, fecha de notificación, días y tipo de cómputo.');
      return;
    }
    this.calculando = true;
    this.plazoService.calcular(f.expedienteId, f.fechaNotificacion, f.diasPlazo, f.tipoComputo)
      .pipe(finalize(() => {
        this.calculando = false;
        this.refrescarVista();
      }))
      .subscribe({
        next: r => {
          this.calculo = r;
          this.formulario.fechaRevisionInterna = r.fechaRevisionInternaSugerida;
          if (r.requiereRevisionSugerida) {
            this.formulario.requiereRevisionProfesional = true;
            if (r.advertencias.length) this.agregarMotivo(r.advertencias.join('; '));
          }
        },
        error: e => this.mostrarError(e)
      });
  }

  guardar(): void {
    const f = this.formulario;
    if (!f.expedienteId || !f.tipoPlazoId || !f.descripcion.trim() || !f.fechaNotificacion || !f.diasPlazo || !f.responsableId) {
      this.mostrarMensaje('error', 'Completa todos los campos obligatorios del plazo.');
      return;
    }
    if (f.origen === 'RESOLUCION' && !f.resolucionId) {
      this.mostrarMensaje('error', 'Selecciona la resolución/notificación que origina el plazo.');
      return;
    }
    if (f.origen === 'ACTUACION' && !f.actuacionOrigenId) {
      this.mostrarMensaje('error', 'Selecciona la actuación que origina el plazo.');
      return;
    }
    if (f.requiereRevisionProfesional && !f.motivoRevision.trim() && !(this.calculo?.advertencias.length)) {
      this.mostrarMensaje('error', 'Indica por qué el plazo requiere revisión profesional.');
      return;
    }

    this.guardando = true;
    const request: PlazoGuardar = { ...f, alertasDias: [...new Set(f.alertasDias)] };
    const llamada = this.editandoUuid
      ? this.plazoService.actualizar(this.editandoUuid, request)
      : this.plazoService.crear(request);

    llamada.pipe(finalize(() => {
      this.guardando = false;
      this.refrescarVista();
    })).subscribe({
      next: d => {
        this.modalFormulario = false;
        this.detalle = d;
        this.mostrarMensaje('exito', this.editandoUuid
          ? 'Plazo actualizado correctamente.'
          : 'Plazo registrado y alertas programadas.');
        this.cargarDatos();
      },
      error: e => this.mostrarError(e)
    });
  }

  cambiarEstado(estado: EstadoPlazo): void {
    const uuid = this.detalle?.plazo.uuid;
    if (!uuid) return;
    this.guardando = true;
    this.plazoService.cambiarEstado(uuid, estado)
      .pipe(finalize(() => {
        this.guardando = false;
        this.refrescarVista();
      }))
      .subscribe({
        next: d => {
          this.detalle = d;
          this.mostrarMensaje('exito', `Estado actualizado a ${this.etiquetaEstado(estado)}.`);
          this.cargarDatos(false);
        },
        error: e => this.mostrarError(e)
      });
  }

  avanzarEstado(): void {
    const siguiente = this.siguienteEstado;
    if (siguiente) this.cambiarEstado(siguiente);
  }

  toggleAlerta(dias: number): void {
    const actual = new Set(this.formulario.alertasDias);
    actual.has(dias) ? actual.delete(dias) : actual.add(dias);
    this.formulario.alertasDias = [...actual];
  }

  alertaActiva(dias: number): boolean {
    return this.formulario.alertasDias.includes(dias);
  }

  agregarMotivo(texto: string): void {
    if (!this.formulario.motivoRevision.includes(texto)) {
      this.formulario.motivoRevision = this.formulario.motivoRevision
        ? `${this.formulario.motivoRevision}; ${texto}`
        : texto;
    }
  }

  cargarDatos(reseleccionar = true): void {
    this.sincronizarFiltros();
    forkJoin({
      plazos: this.plazoService.listar(this.filtros),
      resumen: this.plazoService.resumen(),
      analitica: this.plazoService.analitica()
    }).subscribe({
      next: r => {
        this.plazos = r.plazos;
        this.haySiguiente = r.plazos.length === this.tamanoPagina;
        this.resumen = r.resumen;
        this.analitica = r.analitica;
        if (reseleccionar && this.detalle) {
          const mismo = this.plazos.find(p => p.uuid === this.detalle?.plazo.uuid);
          if (mismo) this.seleccionarPlazo(mismo, false);
        }
        this.refrescarVista();
      },
      error: e => this.mostrarError(e)
    });
  }

  get expedienteSeleccionado() {
    return this.catalogos.expedientes.find(e => e.id === this.formulario.expedienteId) ?? null;
  }

  get plazosVista(): Plazo[] {
    return this.plazos.slice().sort((a, b) => this.compararPrioridad(a, b));
  }

  get proximosSemana(): Plazo[] {
    return this.plazos
      .filter(p => !['CUMPLIDO', 'CANCELADO'].includes(p.estado) && p.diasRestantes >= 0 && p.diasRestantes <= 7)
      .sort((a, b) => a.diasRestantes - b.diasRestantes)
      .slice(0, 3);
  }

  get plazosCriticos(): Plazo[] {
    return this.plazos
      .filter(p => !['CUMPLIDO', 'CANCELADO'].includes(p.estado) && (p.diasRestantes <= 7 || p.nivelRiesgo === 'CRITICO'))
      .sort((a, b) => this.compararPrioridad(a, b))
      .slice(0, 6);
  }

  get graficoSemanas(): BarraSemana[] {
    const porSemana = new Map(this.analitica.semanas.map(s => [s.semana, s]));
    return Array.from({ length: 12 }, (_, i) => {
      const s = porSemana.get(i);
      return {
        etiqueta: `Sem. ${i + 1}`,
        rojo: s?.rojo ?? 0,
        naranja: s?.naranja ?? 0,
        amarillo: s?.amarillo ?? 0
      };
    });
  }

  alturaBarra(valor: number): number {
    return valor === 0 ? 0 : Math.min(105, 8 + valor * 14);
  }

  get evolucionMensual(): EvolucionMes[] {
    const ahora = new Date();
    const mapa = new Map(this.analitica.evolucion.map(m => [`${m.anio}-${m.mes}`, m]));
    const meses: EvolucionMes[] = [];
    for (let i = 7; i >= 0; i--) {
      const d = new Date(ahora.getFullYear(), ahora.getMonth() - i, 1);
      const dato = mapa.get(`${d.getFullYear()}-${d.getMonth() + 1}`);
      meses.push({
        etiqueta: new Intl.DateTimeFormat('es-PE', { month: 'short' }).format(d).replace('.', ''),
        vencidos: dato?.vencidos ?? 0,
        cumplidos: dato?.cumplidos ?? 0
      });
    }
    return meses;
  }

  puntosEvolucion(tipo: 'vencidos' | 'cumplidos'): string {
    const datos = this.evolucionMensual;
    const maximo = Math.max(1, ...datos.flatMap(d => [d.vencidos, d.cumplidos]));
    return datos.map((d, i) => {
      const x = datos.length === 1 ? 0 : (i * 600) / (datos.length - 1);
      const valor = tipo === 'vencidos' ? d.vencidos : d.cumplidos;
      const y = 135 - (valor / maximo) * 110;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  }

  get siguienteEstado(): EstadoPlazo | null {
    const estado = this.detalle?.plazo.estado;
    const mapa: Partial<Record<EstadoPlazo, EstadoPlazo>> = {
      PENDIENTE_CALCULO: 'CALCULADO',
      CALCULADO: 'EN_PREPARACION',
      EN_PREPARACION: 'EN_REVISION',
      EN_REVISION: 'LISTO_PRESENTAR',
      LISTO_PRESENTAR: 'PRESENTADO',
      PRESENTADO: 'CUMPLIDO'
    };
    const siguiente = estado ? mapa[estado] ?? null : null;
    if (siguiente === 'LISTO_PRESENTAR' && !this.puedeAprobar) return null;
    return siguiente;
  }

  etiquetaEstado(e: string): string {
    const m: Record<string, string> = {
      PENDIENTE_CALCULO: 'Pendiente de cálculo',
      CALCULADO: 'Calculado',
      EN_PREPARACION: 'Preparación',
      EN_REVISION: 'Revisión',
      LISTO_PRESENTAR: 'Listo para presentar',
      PRESENTADO: 'Presentado',
      CUMPLIDO: 'Cumplido',
      VENCIDO: 'Vencido',
      SUSPENDIDO: 'Suspendido',
      CANCELADO: 'Cancelado'
    };
    return m[e] ?? e;
  }

  etiquetaOrigen(o: string): string {
    const m: Record<string, string> = {
      MANUAL: 'Manual',
      RESOLUCION: 'Resolución',
      NOTIFICACION: 'Notificación',
      ACTUACION: 'Actuación',
      MONITOR_JUDICIAL: 'Monitor judicial'
    };
    return m[o] ?? o;
  }

  etiquetaComputo(c: string): string {
    return c === 'HABILES' ? 'Días hábiles' : 'Días calendario';
  }

  textoVencimiento(p: Plazo): string {
    if (p.estado === 'CUMPLIDO') return 'Cumplido';
    if (p.estado === 'CANCELADO') return 'Cancelado';
    if (p.estado === 'SUSPENDIDO') return 'Suspendido';
    if (p.diasRestantes < 0) return `Vencido hace ${Math.abs(p.diasRestantes)} día${Math.abs(p.diasRestantes) === 1 ? '' : 's'}`;
    if (p.diasRestantes === 0) return 'Vence hoy';
    if (p.diasRestantes === 1) return 'Vence mañana';
    return `${p.diasRestantes} días`;
  }

  claseTiempo(p: Plazo): 'warning' | 'normal' | 'danger' {
    if (p.diasRestantes < 0 || p.estado === 'VENCIDO') return 'danger';
    if (p.diasRestantes <= 5 || p.nivelRiesgo === 'CRITICO') return 'warning';
    return 'normal';
  }

  claseEstado(p: Plazo): string {
    if (p.estado === 'CUMPLIDO') return 'completed';
    if (p.estado === 'VENCIDO' || p.nivelRiesgo === 'CRITICO') return 'critical';
    if (p.estado === 'SUSPENDIDO' || p.estado === 'CANCELADO') return 'suspended';
    return 'process';
  }

  etiquetaAlerta(dias: number | null): string {
    if (dias === null) return 'Alerta';
    if (dias === 0) return 'Mismo día';
    if (dias > 0) return `${dias} día${dias === 1 ? '' : 's'} antes`;
    const posteriores = Math.abs(dias);
    return `${posteriores} día${posteriores === 1 ? '' : 's'} después`;
  }

  iniciales(nombre: string | null | undefined): string {
    if (!nombre) return '—';
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0]).join('').toUpperCase();
  }

  formatoFecha(fecha: string | null): string {
    if (!fecha) return '—';
    return new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric' })
      .format(new Date(`${fecha.substring(0, 10)}T00:00:00`));
  }

  formatoFechaHora(fecha: string | null): string {
    if (!fecha) return '—';
    return new Intl.DateTimeFormat('es-PE', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(new Date(fecha));
  }

  private compararPrioridad(a: Plazo, b: Plazo): number {
    const cerradoA = ['CUMPLIDO', 'CANCELADO'].includes(a.estado);
    const cerradoB = ['CUMPLIDO', 'CANCELADO'].includes(b.estado);
    if (cerradoA !== cerradoB) return cerradoA ? 1 : -1;

    const aPorVencer = a.diasRestantes >= 0;
    const bPorVencer = b.diasRestantes >= 0;
    if (aPorVencer && !bPorVencer) return -1;
    if (!aPorVencer && bPorVencer) return 1;
    if (aPorVencer && bPorVencer) return a.diasRestantes - b.diasRestantes;
    return b.diasRestantes - a.diasRestantes;
  }

  trackByPlazo(_: number, plazo: Plazo): string { return plazo.uuid; }
  trackById(_: number, item: { id: number }): number { return item.id; }
  trackBySemana(_: number, item: BarraSemana): string { return item.etiqueta; }
  trackByMes(_: number, item: EvolucionMes): string { return item.etiqueta; }

  private formularioVacio(): PlazoGuardar {
    return {
      expedienteId: null,
      actuacionOrigenId: null,
      resolucionId: null,
      tipoPlazoId: null,
      origen: 'MANUAL',
      descripcion: '',
      fechaNotificacion: '',
      diasPlazo: null,
      tipoComputo: 'HABILES',
      fechaRevisionInterna: null,
      responsableId: null,
      revisorId: null,
      estado: 'CALCULADO',
      nivelRiesgo: 'ALTO',
      requiereRevisionProfesional: false,
      motivoRevision: '',
      actuacionRequerida: '',
      documentoId: null,
      observaciones: '',
      alertasDias: [15, 10, 7, 5, 3, 1, 0, -1, -3]
    };
  }

  private mostrarError(error: unknown): void {
    const mensaje = error instanceof HttpErrorResponse
      ? (error.error?.mensaje || error.error?.message || error.message || 'No se pudo completar la operación.')
      : 'No se pudo completar la operación.';
    this.mostrarMensaje('error', mensaje);
  }

  private mostrarMensaje(tipo: 'error' | 'exito', mensaje: string): void {
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
    this.error = tipo === 'error' ? mensaje : '';
    this.exito = tipo === 'exito' ? mensaje : '';
    this.refrescarVista();
    this.mensajeTimer = setTimeout(() => {
      this.error = '';
      this.exito = '';
      this.refrescarVista();
    }, 4200);
  }

  private refrescarVista(): void {
    try {
      this.cdr.detectChanges();
    } catch {
      // El componente ya fue destruido.
    }
  }
}
