import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  ConsultaMonitor,
  ExpedienteMonitor,
  MonitorCatalogos,
  MonitorFiltro,
  MonitorResumen,
  OrigenMonitor,
  RegistrarConsultaMonitorRequest
} from '../../core/models/monitor-judicial.models';
import { AuthService } from '../../core/services/auth.service';
import { MonitorJudicialService } from '../../core/services/monitor-judicial.service';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-monitor-judicial',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './monitor-judicial.component.html',
  styleUrl: './monitor-judicial.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitorJudicialComponent implements OnInit, OnDestroy {
  private readonly api = inject(MonitorJudicialService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  private filtroTimer: ReturnType<typeof setTimeout> | null = null;
  private catalogoTimer: ReturnType<typeof setTimeout> | null = null;
  private exitoTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly formatoFechaHora = new Intl.DateTimeFormat('es-PE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });

  readonly tamanosPagina = [10, 20, 40, 100];

  cargando = false;
  cargandoConsultas = false;
  cargandoCatalogo = false;
  guardando = false;
  eliminando = false;

  error = '';
  exito = '';

  resumen: MonitorResumen = {
    expedientesMonitoreados: 0,
    movimientosHoy: 0,
    nuevasResolucionesHoy: 0,
    pendientesVerificacion: 0
  };

  monitoreados: ExpedienteMonitor[] = [];
  consultas: ConsultaMonitor[] = [];
  catalogos: MonitorCatalogos = { expedientes: [] };

  filtro: MonitorFiltro = {};

  paginaMonitoreados = 0;
  tamanoMonitoreados = 20;
  totalMonitoreados = 0;
  totalPaginasMonitoreados = 0;

  paginaConsultas = 0;
  tamanoConsultas = 20;
  totalConsultas = 0;
  totalPaginasConsultas = 0;

  busquedaCatalogo = '';

  modalAgregar = false;
  modalConsulta = false;
  modalConfirmarQuitar = false;

  itemAQuitar: ExpedienteMonitor | null = null;

  agregarForm = {
    expedienteId: null as number | null,
    fuentePlataforma: 'CEJ - Poder Judicial'
  };

  consultaForm: RegistrarConsultaMonitorRequest = this.nuevaConsulta();

  readonly tiposMovimiento = [
    'RESOLUCION',
    'AUTO',
    'SENTENCIA',
    'PROVIDENCIA',
    'NOTIFICACION',
    'AUDIENCIA',
    'ESCRITO',
    'TRAMITE',
    'OTRO'
  ];

  readonly tiposResolucion = [
    'AUTO',
    'SENTENCIA',
    'DECRETO',
    'PROVIDENCIA',
    'CASACION',
    'ACUERDO',
    'RESOLUCION_ADMIN'
  ];

  readonly origenes: OrigenMonitor[] = [
    'MANUAL',
    'DESCARGADA',
    'CORREO',
    'CLIENTE',
    'IA'
  ];

  get puedeGestionar(): boolean {
    return this.auth.tienePermiso('MONITOR_REGISTRAR');
  }

  get expedientesSugeridos(): string[] {
    return [
      ...new Set(
        this.catalogos.expedientes
          .flatMap(exp => [exp.codigoInterno, exp.numeroExpediente ?? ''])
          .filter(Boolean)
      )
    ].sort();
  }

  get clientesSugeridos(): string[] {
    return [
      ...new Set(
        this.catalogos.expedientes
          .map(exp => exp.cliente)
          .filter(Boolean)
      )
    ].sort();
  }

  get abogadosSugeridos(): string[] {
    return [
      ...new Set(
        this.catalogos.expedientes
          .map(exp => exp.abogadoResponsable)
          .filter((valor): valor is string => !!valor)
      )
    ].sort();
  }

  get organosSugeridos(): string[] {
    return [
      ...new Set(
        this.catalogos.expedientes
          .map(exp => exp.organo)
          .filter((valor): valor is string => !!valor)
      )
    ].sort();
  }

  get inicioMonitoreados(): number {
    return this.totalMonitoreados === 0
      ? 0
      : this.paginaMonitoreados * this.tamanoMonitoreados + 1;
  }

  get finMonitoreados(): number {
    return Math.min(
      (this.paginaMonitoreados + 1) * this.tamanoMonitoreados,
      this.totalMonitoreados
    );
  }

  get inicioConsultas(): number {
    return this.totalConsultas === 0
      ? 0
      : this.paginaConsultas * this.tamanoConsultas + 1;
  }

  get finConsultas(): number {
    return Math.min(
      (this.paginaConsultas + 1) * this.tamanoConsultas,
      this.totalConsultas
    );
  }

  ngOnInit(): void {
    this.cargarTodo();
  }

  ngOnDestroy(): void {
    if (this.filtroTimer) {
      clearTimeout(this.filtroTimer);
    }
    if (this.catalogoTimer) {
      clearTimeout(this.catalogoTimer);
    }
    if (this.exitoTimer) {
      clearTimeout(this.exitoTimer);
    }
  }

  /** Carga inicial en paralelo; despues cada accion refresca solo lo necesario. */
  cargarTodo(): void {
    this.cargando = true;
    this.cargandoConsultas = true;
    this.cargandoCatalogo = true;
    this.error = '';
    this.actualizarVista();

    forkJoin({
      resumen: this.api.resumen(),
      monitoreados: this.api.listarPagina(
        this.filtro,
        this.paginaMonitoreados,
        this.tamanoMonitoreados
      ),
      consultas: this.api.consultasPagina(
        {},
        this.paginaConsultas,
        this.tamanoConsultas
      ),
      catalogos: this.api.catalogos('', 50)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => {
          this.resumen = data.resumen;
          this.asignarPaginaMonitoreados(data.monitoreados);
          this.asignarPaginaConsultas(data.consultas);
          this.catalogos = data.catalogos;
          this.cargando = false;
          this.cargandoConsultas = false;
          this.cargandoCatalogo = false;
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.cargando = false;
          this.cargandoConsultas = false;
          this.cargandoCatalogo = false;
          this.actualizarVista();
        }
      });
  }

  programarFiltro(): void {
    if (this.filtroTimer) {
      clearTimeout(this.filtroTimer);
    }

    this.filtroTimer = setTimeout(() => {
      this.paginaMonitoreados = 0;
      this.aplicarFiltros(false);
    }, 350);
  }

  aplicarFiltros(reiniciarPagina = true): void {
    if (reiniciarPagina) {
      this.paginaMonitoreados = 0;
    }
    this.cargarMonitoreados();
  }

  limpiarFiltros(): void {
    if (this.filtroTimer) {
      clearTimeout(this.filtroTimer);
      this.filtroTimer = null;
    }

    this.filtro = {};
    this.paginaMonitoreados = 0;
    this.cargarMonitoreados();
  }

  paginaAnteriorMonitoreados(): void {
    if (this.paginaMonitoreados <= 0 || this.cargando) {
      return;
    }
    this.paginaMonitoreados--;
    this.cargarMonitoreados();
  }

  paginaSiguienteMonitoreados(): void {
    if (
      this.cargando ||
      this.totalPaginasMonitoreados === 0 ||
      this.paginaMonitoreados >= this.totalPaginasMonitoreados - 1
    ) {
      return;
    }
    this.paginaMonitoreados++;
    this.cargarMonitoreados();
  }

  cambiarTamanoMonitoreados(): void {
    this.paginaMonitoreados = 0;
    this.cargarMonitoreados();
  }

  paginaAnteriorConsultas(): void {
    if (this.paginaConsultas <= 0 || this.cargandoConsultas) {
      return;
    }
    this.paginaConsultas--;
    this.cargarConsultas();
  }

  paginaSiguienteConsultas(): void {
    if (
      this.cargandoConsultas ||
      this.totalPaginasConsultas === 0 ||
      this.paginaConsultas >= this.totalPaginasConsultas - 1
    ) {
      return;
    }
    this.paginaConsultas++;
    this.cargarConsultas();
  }

  cambiarTamanoConsultas(): void {
    this.paginaConsultas = 0;
    this.cargarConsultas();
  }

  programarBusquedaCatalogo(): void {
    if (this.catalogoTimer) {
      clearTimeout(this.catalogoTimer);
    }

    this.catalogoTimer = setTimeout(() => {
      this.cargarCatalogos(this.busquedaCatalogo);
    }, 300);
  }

  abrirAgregar(): void {
    this.error = '';
    this.exito = '';
    this.busquedaCatalogo = '';
    this.agregarForm = {
      expedienteId: null,
      fuentePlataforma: 'CEJ - Poder Judicial'
    };
    this.modalAgregar = true;
    this.cargarCatalogos('');
  }

  guardarSeguimiento(): void {
    if (
      !this.agregarForm.expedienteId ||
      !this.agregarForm.fuentePlataforma.trim()
    ) {
      this.error = 'Selecciona un expediente e indica la fuente o plataforma.';
      this.actualizarVista();
      return;
    }

    this.guardando = true;
    this.error = '';
    this.actualizarVista();

    this.api
      .agregar({
        expedienteId: this.agregarForm.expedienteId,
        fuentePlataforma: this.agregarForm.fuentePlataforma.trim()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.guardando = false;
          this.modalAgregar = false;
          this.mostrarExito('Expediente añadido al Monitor Judicial.');
          this.paginaMonitoreados = 0;
          this.cargarMonitoreados();
          this.cargarResumen();
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.guardando = false;
          this.actualizarVista();
        }
      });
  }

  abrirConsulta(item: ExpedienteMonitor): void {
    this.error = '';
    this.exito = '';
    this.consultaForm = this.nuevaConsulta();
    this.consultaForm.expedienteId = item.expedienteId;
    this.consultaForm.fuentePlataforma = item.fuentePlataforma;
    this.modalConsulta = true;
  }

  registrarConsulta(): void {
    if (
      !this.consultaForm.expedienteId ||
      !this.consultaForm.fuentePlataforma.trim()
    ) {
      this.error = 'Falta el expediente o la fuente de consulta.';
      this.actualizarVista();
      return;
    }

    if (
      this.consultaForm.huboMovimiento &&
      !this.consultaForm.tipoMovimiento.trim() &&
      !this.consultaForm.nuevaResolucion
    ) {
      this.error = 'Selecciona el tipo de movimiento detectado.';
      this.actualizarVista();
      return;
    }

    if (
      this.consultaForm.nuevaResolucion &&
      !this.consultaForm.numeroResolucion.trim()
    ) {
      this.error = 'Indica el número de la nueva resolución.';
      this.actualizarVista();
      return;
    }

    if (
      this.consultaForm.nuevaResolucion &&
      !this.consultaForm.fechaNotificacion
    ) {
      this.error = 'Indica la fecha de notificación de la resolución.';
      this.actualizarVista();
      return;
    }

    if (this.consultaForm.origenInformacion === 'IA') {
      this.consultaForm.estadoVerificacion = 'PENDIENTE';
    }

    this.guardando = true;
    this.error = '';
    this.actualizarVista();

    this.api
      .registrarConsulta(this.consultaForm)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.guardando = false;
          this.modalConsulta = false;
          this.mostrarExito('Consulta judicial registrada correctamente.');
          this.paginaConsultas = 0;
          this.cargarConsultas();
          this.cargarMonitoreados();
          this.cargarResumen();
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.guardando = false;
          this.actualizarVista();
        }
      });
  }

  verificar(
    consulta: ConsultaMonitor,
    estado: 'VERIFICADA' | 'RECHAZADA'
  ): void {
    this.error = '';

    this.api
      .verificar(consulta.uuid, estado)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          consulta.estadoVerificacion = estado;
          this.mostrarExito(
            estado === 'VERIFICADA'
              ? 'Información verificada.'
              : 'Información rechazada.'
          );
          this.cargarConsultas();
          this.cargarMonitoreados();
          this.cargarResumen();
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.actualizarVista();
        }
      });
  }

  solicitarQuitar(item: ExpedienteMonitor): void {
    this.itemAQuitar = item;
    this.modalConfirmarQuitar = true;
    this.error = '';
  }

  cancelarQuitar(): void {
    if (this.eliminando) {
      return;
    }

    this.modalConfirmarQuitar = false;
    this.itemAQuitar = null;
  }

  confirmarQuitar(): void {
    const item = this.itemAQuitar;

    if (!item || this.eliminando) {
      return;
    }

    this.eliminando = true;
    this.error = '';
    this.actualizarVista();

    this.api
      .quitar(item.uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.modalConfirmarQuitar = false;
          this.itemAQuitar = null;
          this.eliminando = false;
          this.mostrarExito('Expediente retirado del Monitor Judicial.');

          if (this.monitoreados.length === 1 && this.paginaMonitoreados > 0) {
            this.paginaMonitoreados--;
          }
          this.cargarMonitoreados();
          this.cargarResumen();
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.eliminando = false;
          this.actualizarVista();
        }
      });
  }

  nombreExpediente(id: number): string {
    const monitor = this.monitoreados.find(item => item.expedienteId === id);
    if (monitor) {
      return `${monitor.codigoInterno} · ${monitor.cliente}`;
    }

    const expediente = this.catalogos.expedientes.find(item => item.id === id);
    return expediente
      ? `${expediente.codigoInterno} · ${expediente.cliente}`
      : `Expediente #${id}`;
  }

  formatoFecha(fecha: string | null): string {
    if (!fecha) {
      return '—';
    }

    const valor = new Date(fecha);
    return Number.isNaN(valor.getTime())
      ? '—'
      : this.formatoFechaHora.format(valor);
  }

  iniciales(nombre: string | null): string {
    if (!nombre) {
      return '—';
    }

    const partes = nombre.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes[1]?.[0] ?? '')).toUpperCase();
  }

  cerrarModales(): void {
    if (this.guardando || this.eliminando) {
      return;
    }

    this.modalAgregar = false;
    this.modalConsulta = false;
    this.modalConfirmarQuitar = false;
    this.itemAQuitar = null;
  }

  cerrarMensaje(tipo: 'error' | 'exito'): void {
    if (tipo === 'error') {
      this.error = '';
    } else {
      this.exito = '';

      if (this.exitoTimer) {
        clearTimeout(this.exitoTimer);
        this.exitoTimer = null;
      }
    }

    this.actualizarVista();
  }

  private cargarMonitoreados(): void {
    this.cargando = true;
    this.error = '';
    this.actualizarVista();

    this.api
      .listarPagina(this.filtro, this.paginaMonitoreados, this.tamanoMonitoreados)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: pagina => {
          if (
            pagina.totalPaginas > 0 &&
            this.paginaMonitoreados >= pagina.totalPaginas
          ) {
            this.paginaMonitoreados = Math.max(0, pagina.totalPaginas - 1);
            this.cargando = false;
            this.cargarMonitoreados();
            return;
          }
          this.asignarPaginaMonitoreados(pagina);
          this.cargando = false;
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.cargando = false;
          this.actualizarVista();
        }
      });
  }

  private cargarConsultas(): void {
    this.cargandoConsultas = true;
    this.error = '';
    this.actualizarVista();

    this.api
      .consultasPagina({}, this.paginaConsultas, this.tamanoConsultas)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: pagina => {
          if (
            pagina.totalPaginas > 0 &&
            this.paginaConsultas >= pagina.totalPaginas
          ) {
            this.paginaConsultas = Math.max(0, pagina.totalPaginas - 1);
            this.cargandoConsultas = false;
            this.cargarConsultas();
            return;
          }
          this.asignarPaginaConsultas(pagina);
          this.cargandoConsultas = false;
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.cargandoConsultas = false;
          this.actualizarVista();
        }
      });
  }

  private cargarResumen(): void {
    this.api
      .resumen()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: resumen => {
          this.resumen = resumen;
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.actualizarVista();
        }
      });
  }

  private cargarCatalogos(busqueda: string): void {
    this.cargandoCatalogo = true;
    this.actualizarVista();

    this.api
      .catalogos(busqueda, 50)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: catalogos => {
          this.catalogos = catalogos;
          this.cargandoCatalogo = false;
          this.actualizarVista();
        },
        error: e => {
          this.error = this.mensajeError(e);
          this.cargandoCatalogo = false;
          this.actualizarVista();
        }
      });
  }

  private asignarPaginaMonitoreados(pagina: {
    contenido: ExpedienteMonitor[];
    pagina: number;
    totalElementos: number;
    totalPaginas: number;
  }): void {
    this.monitoreados = pagina.contenido;
    this.paginaMonitoreados = pagina.pagina;
    this.totalMonitoreados = pagina.totalElementos;
    this.totalPaginasMonitoreados = pagina.totalPaginas;
  }

  private asignarPaginaConsultas(pagina: {
    contenido: ConsultaMonitor[];
    pagina: number;
    totalElementos: number;
    totalPaginas: number;
  }): void {
    this.consultas = pagina.contenido;
    this.paginaConsultas = pagina.pagina;
    this.totalConsultas = pagina.totalElementos;
    this.totalPaginasConsultas = pagina.totalPaginas;
  }

  private mostrarExito(mensaje: string, duracionMs = 3000): void {
    this.exito = mensaje;

    if (this.exitoTimer) {
      clearTimeout(this.exitoTimer);
    }

    this.actualizarVista();

    this.exitoTimer = setTimeout(() => {
      this.exito = '';
      this.exitoTimer = null;
      this.actualizarVista();
    }, duracionMs);
  }

  private actualizarVista(): void {
    this.cdr.markForCheck();
  }

  private nuevaConsulta(): RegistrarConsultaMonitorRequest {
    return {
      expedienteId: 0,
      fuentePlataforma: '',
      huboMovimiento: false,
      tipoMovimiento: '',
      nuevaResolucion: false,
      numeroResolucion: '',
      tipoResolucion: '',
      fechaResolucion: null,
      fechaNotificacion: null,
      documentoDescargado: '',
      notificacion: '',
      plazo: '',
      observaciones: '',
      origenInformacion: 'MANUAL',
      estadoVerificacion: 'VERIFICADA'
    };
  }

  private mensajeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return (
        error.error?.mensaje ||
        error.error?.message ||
        error.message ||
        'Ocurrió un error al procesar la solicitud.'
      );
    }

    return 'Ocurrió un error inesperado.';
  }
}
