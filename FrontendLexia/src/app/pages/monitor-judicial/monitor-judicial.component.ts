import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';
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
  styleUrl: './monitor-judicial.component.css'
})
export class MonitorJudicialComponent implements OnInit, OnDestroy {
  private readonly api = inject(MonitorJudicialService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  private filtroTimer: ReturnType<typeof setTimeout> | null = null;
  private exitoTimer: ReturnType<typeof setTimeout> | null = null;

  cargando = false;
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
          .flatMap(exp => [
            exp.codigoInterno,
            exp.numeroExpediente ?? ''
          ])
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

  ngOnInit(): void {
    this.cargarTodo();
  }

  ngOnDestroy(): void {
    if (this.filtroTimer) {
      clearTimeout(this.filtroTimer);
    }

    if (this.exitoTimer) {
      clearTimeout(this.exitoTimer);
    }
  }

  cargarTodo(): void {
    this.cargando = true;
    this.error = '';
    this.actualizarVista();

    forkJoin({
      resumen: this.api.resumen(),
      monitoreados: this.api.listar(this.filtro),
      consultas: this.api.consultas(),
      catalogos: this.api.catalogos()
    }).subscribe({
      next: data => {
        this.resumen = data.resumen;
        this.monitoreados = data.monitoreados;
        this.consultas = data.consultas;
        this.catalogos = data.catalogos;
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

  programarFiltro(): void {
    if (this.filtroTimer) {
      clearTimeout(this.filtroTimer);
    }

    this.filtroTimer = setTimeout(() => {
      this.aplicarFiltros();
    }, 350);
  }

  aplicarFiltros(): void {
    this.cargando = true;
    this.error = '';
    this.actualizarVista();

    this.api.listar(this.filtro).subscribe({
      next: datos => {
        this.monitoreados = datos;
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

  limpiarFiltros(): void {
    if (this.filtroTimer) {
      clearTimeout(this.filtroTimer);
      this.filtroTimer = null;
    }

    this.filtro = {};
    this.aplicarFiltros();
  }

  abrirAgregar(): void {
    this.error = '';
    this.exito = '';
    this.agregarForm = {
      expedienteId: null,
      fuentePlataforma: 'CEJ - Poder Judicial'
    };
    this.modalAgregar = true;
  }

  guardarSeguimiento(): void {
    if (
      !this.agregarForm.expedienteId ||
      !this.agregarForm.fuentePlataforma.trim()
    ) {
      this.error =
        'Selecciona un expediente e indica la fuente o plataforma.';
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
      .subscribe({
        next: () => {
          this.guardando = false;
          this.modalAgregar = false;
          this.mostrarExito('Expediente añadido al Monitor Judicial.');

          // En Angular 21 el proyecto trabaja sin Zone.js.
          // Notificamos el cambio para que el modal se cierre inmediatamente.
          this.actualizarVista();

          this.cargarTodo();
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

    this.api.registrarConsulta(this.consultaForm).subscribe({
      next: () => {
        this.guardando = false;
        this.modalConsulta = false;
        this.mostrarExito('Consulta judicial registrada correctamente.');

        // Evita que el botón se quede en "Guardando..." hasta hacer otro clic.
        this.actualizarVista();

        this.cargarTodo();
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

    this.api.verificar(consulta.uuid, estado).subscribe({
      next: () => {
        consulta.estadoVerificacion = estado;
        this.mostrarExito(
          estado === 'VERIFICADA'
            ? 'Información verificada.'
            : 'Información rechazada.'
        );

        this.actualizarVista();
        this.cargarTodo();
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

    this.api.quitar(item.uuid).subscribe({
      next: () => {
        // Actualización inmediata de la tabla.
        this.monitoreados = this.monitoreados.filter(
          actual => actual.uuid !== item.uuid
        );

        this.resumen = {
          ...this.resumen,
          expedientesMonitoreados: Math.max(
            0,
            this.resumen.expedientesMonitoreados - 1
          )
        };

        this.modalConfirmarQuitar = false;
        this.itemAQuitar = null;
        this.eliminando = false;
        this.mostrarExito('Expediente retirado del Monitor Judicial.');

        this.actualizarVista();

        // Luego sincronizamos con la base de datos.
        this.cargarTodo();
      },
      error: e => {
        this.error = this.mensajeError(e);
        this.eliminando = false;
        this.actualizarVista();
      }
    });
  }

  nombreExpediente(id: number): string {
    const expediente = this.catalogos.expedientes.find(
      item => item.id === id
    );

    return expediente
      ? `${expediente.codigoInterno} · ${expediente.cliente}`
      : `Expediente #${id}`;
  }

  formatoFecha(fecha: string | null): string {
    if (!fecha) {
      return '—';
    }

    return new Intl.DateTimeFormat('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(fecha));
  }

  iniciales(nombre: string | null): string {
    if (!nombre) {
      return '—';
    }

    const partes = nombre.trim().split(/\s+/);

    return (
      (partes[0]?.[0] ?? '') +
      (partes[1]?.[0] ?? '')
    ).toUpperCase();
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
    // Angular 21 funciona sin Zone.js por defecto.
    // markForCheck notifica a Angular que debe volver a renderizar
    // los cambios realizados dentro de las suscripciones HTTP.
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
