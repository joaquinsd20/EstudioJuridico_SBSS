import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import {
  CatalogosExpediente,
  EventoLineaTiempo,
  ExpedienteDetalle,
  ExpedienteGuardar,
  ExpedienteLista,
  ExpedienteResumen
} from '../../core/models/expediente.models';
import { ExpedienteFiltros, ExpedienteService } from '../../core/services/expediente.service';
import { PanelComponent } from '../panel/panel.component';
import { environment } from '../../../environments/environment';

interface PaginaExpedientes {
  contenido: ExpedienteLista[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  esUltima: boolean;
}

@Component({
  selector: 'app-expedientes',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './expedientes.component.html',
  styleUrl: './expedientes.component.css'
})
export class ExpedientesComponent implements OnInit, OnDestroy {
  private readonly expedienteService = inject(ExpedienteService);
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private filtroTimer?: ReturnType<typeof setTimeout>;
  private listadoSolicitud = 0;
  private detalleSolicitud = 0;
  private mensajeTimer: ReturnType<typeof setTimeout> | null = null;

  cargando = true;
  cargandoDetalle = false;
  cargandoLineaTiempo = false;
  guardando = false;
  error = '';
  errorDetalle = '';
  errorLineaTiempo = '';
  mensaje = '';

  resumen: ExpedienteResumen = { total: 0, activos: 0, enTramite: 0, criticos: 0 };
  expedientes: ExpedienteLista[] = [];
  pagina = 0;
  tamanoPagina = 10;
  readonly tamanosPagina = [10, 20, 40, 100];
  totalElementos = 0;
  totalPaginas = 0;
  seleccionado: ExpedienteLista | null = null;
  detalle: ExpedienteDetalle | null = null;
  lineaTiempo: EventoLineaTiempo[] = [];
  seleccionadoUuid: string | null = null;
  catalogos: CatalogosExpediente = {
    clientes: [], especialidades: [], materias: [], organos: [], estados: [], usuarios: [], contrapartes: []
  };

  filtros: ExpedienteFiltros = {
    texto: '', cliente: '', estado: '', materiaId: null, organo: ''
  };

  modalDetalle = false;
  modalFormulario = false;
  editandoUuid: string | null = null;
  formulario: ExpedienteGuardar = this.formularioVacio();

  ngOnInit(): void {
    // Mismo patrón que Documentos: se cargan los datos al entrar y cada
    // respuesta asíncrona fuerza el refresco visual en modo zoneless.
    this.cargarCatalogos();
    this.cargarResumen();
    this.cargarListadoInicial();
  }

  ngOnDestroy(): void {
    if (this.filtroTimer) clearTimeout(this.filtroTimer);
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
  }

  private cargarListadoInicial(): void {
    this.cargarListado(true);
  }

  private listarPagina(): Observable<PaginaExpedientes> {
    let params = new HttpParams().set('page', this.pagina).set('size', this.tamanoPagina);
    Object.entries(this.filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(clave, String(valor));
      }
    });
    return this.http.get<PaginaExpedientes>(`${environment.apiUrl}/expedientes`, { params });
  }

  private cargarListado(seleccionarPrimero: boolean): void {
    const solicitud = ++this.listadoSolicitud;
    this.cargando = true;
    this.error = '';
    this.actualizarVista();

    this.listarPagina().subscribe({
      next: (resultado) => {
        if (solicitud !== this.listadoSolicitud) return;

        this.expedientes = resultado.contenido;
        this.totalElementos = resultado.totalElementos;
        this.totalPaginas = resultado.totalPaginas;
        this.cargando = false;

        if (!this.expedientes.length) {
          this.limpiarSeleccion();
          this.actualizarVista();
          return;
        }

        const actualSigueVisible = this.seleccionadoUuid
          ? this.expedientes.find(e => e.uuid === this.seleccionadoUuid) ?? null
          : null;

        if (actualSigueVisible) {
          this.seleccionado = actualSigueVisible;
          this.seleccionadoUuid = actualSigueVisible.uuid;
          this.actualizarVista();
          if (!this.detalle || this.detalle.uuid !== actualSigueVisible.uuid) {
            this.cargarDetalle(actualSigueVisible, null);
          }
          return;
        }

        if (seleccionarPrimero || !this.seleccionadoUuid) {
          this.seleccionar(this.expedientes[0]);
        } else {
          this.limpiarSeleccion();
        }
        this.actualizarVista();
      },
      error: (error) => {
        if (solicitud !== this.listadoSolicitud) return;
        this.cargando = false;
        this.expedientes = [];
        this.limpiarSeleccion();
        this.error = this.mensajeError(error, 'No se pudieron cargar los expedientes.');
        this.actualizarVista();
      }
    });
  }

  private cargarResumen(): void {
    this.expedienteService.resumen().subscribe({
      next: (resumen) => {
        this.resumen = resumen;
        this.actualizarVista();
      },
      error: () => {
        this.resumen = { total: 0, activos: 0, enTramite: 0, criticos: 0 };
        this.actualizarVista();
      }
    });
  }

  private cargarCatalogos(): void {
    this.expedienteService.catalogos().subscribe({
      next: (catalogos) => {
        this.catalogos = catalogos;
        this.actualizarVista();
      },
      error: (error) => {
        this.error = this.mensajeError(error, 'No se pudieron cargar los catálogos de expedientes.');
        this.actualizarVista();
      }
    });
  }

  reintentarCarga(): void {
    this.cargarCatalogos();
    this.cargarResumen();
    this.cargarListado(true);
  }

  aplicarFiltrosConEspera(): void {
    if (this.filtroTimer) clearTimeout(this.filtroTimer);
    this.filtroTimer = setTimeout(() => this.aplicarFiltros(), 250);
  }

  aplicarFiltros(): void {
    if (this.filtroTimer) {
      clearTimeout(this.filtroTimer);
      this.filtroTimer = undefined;
    }
    this.pagina = 0;
    this.cargarListado(true);
  }

  cambiarTamanoPagina(nuevoTamano: number): void {
    if (this.cargando || !this.tamanosPagina.includes(nuevoTamano) || nuevoTamano === this.tamanoPagina) return;
    this.tamanoPagina = nuevoTamano;
    this.aplicarFiltros();
  }

  cambiarPagina(nuevaPagina: number): void {
    if (this.cargando || nuevaPagina < 0 || nuevaPagina >= this.totalPaginas || nuevaPagina === this.pagina) return;
    this.pagina = nuevaPagina;
    this.cargarListado(true);
  }

  limpiarFiltros(): void {
    this.filtros = { texto: '', cliente: '', estado: '', materiaId: null, organo: '' };
    this.aplicarFiltros();
  }

  seleccionar(expediente: ExpedienteLista, forzarRecarga = false): void {
    if (!expediente?.uuid) return;

    // Igual que Documentos: la fila se selecciona y se muestra de inmediato.
    // Tras una edición se fuerza la recarga para no conservar en memoria el
    // detalle ni la línea de tiempo anteriores.
    this.seleccionado = expediente;
    this.seleccionadoUuid = expediente.uuid;
    this.actualizarVista();

    if (!forzarRecarga && this.detalle?.uuid === expediente.uuid && !this.cargandoDetalle) return;
    this.cargarDetalle(expediente, null);
  }

  abrirDesdeLista(expediente: ExpedienteLista): void {
    this.seleccionado = expediente;
    this.seleccionadoUuid = expediente.uuid;
    this.modalDetalle = true;
    this.actualizarVista();
    this.cargarDetalle(expediente, 'ver');
  }

  editarDesdeLista(expediente: ExpedienteLista): void {
    this.seleccionado = expediente;
    this.seleccionadoUuid = expediente.uuid;
    this.actualizarVista();
    this.cargarDetalle(expediente, 'editar');
  }

  private cargarDetalle(expediente: ExpedienteLista, accion: 'ver' | 'editar' | null): void {
    const solicitud = ++this.detalleSolicitud;
    this.seleccionado = expediente;
    this.seleccionadoUuid = expediente.uuid;
    this.cargandoDetalle = true;
    this.cargandoLineaTiempo = true;
    this.errorDetalle = '';
    this.errorLineaTiempo = '';
    this.detalle = null;
    this.lineaTiempo = [];
    if (accion === 'ver') this.modalDetalle = true;
    this.actualizarVista();

    this.expedienteService.obtener(expediente.uuid).subscribe({
      next: (detalle) => {
        if (solicitud !== this.detalleSolicitud) return;
        this.detalle = detalle;
        this.cargandoDetalle = false;

        if (accion === 'editar') this.editarExpediente();
        this.actualizarVista();
      },
      error: (error) => {
        if (solicitud !== this.detalleSolicitud) return;
        this.cargandoDetalle = false;
        this.detalle = null;
        this.errorDetalle = this.mensajeError(error, 'No se pudo cargar la ficha completa del expediente.');
        this.actualizarVista();
      }
    });

    this.expedienteService.lineaTiempo(expediente.uuid).subscribe({
      next: (lineaTiempo) => {
        if (solicitud !== this.detalleSolicitud) return;
        this.lineaTiempo = lineaTiempo ?? [];
        this.cargandoLineaTiempo = false;
        this.actualizarVista();
      },
      error: (error) => {
        if (solicitud !== this.detalleSolicitud) return;
        this.cargandoLineaTiempo = false;
        this.lineaTiempo = [];
        this.errorLineaTiempo = this.mensajeError(error, 'No se pudo cargar la línea de tiempo.');
        this.actualizarVista();
      }
    });
  }

  reintentarDetalle(): void {
    if (this.seleccionado) this.cargarDetalle(this.seleccionado, null);
  }

  private limpiarSeleccion(): void {
    ++this.detalleSolicitud;
    this.seleccionado = null;
    this.seleccionadoUuid = null;
    this.detalle = null;
    this.lineaTiempo = [];
    this.cargandoDetalle = false;
    this.cargandoLineaTiempo = false;
    this.errorDetalle = '';
    this.errorLineaTiempo = '';
    this.modalDetalle = false;
    this.actualizarVista();
  }

  abrirDetalleCompleto(): void {
    if (!this.seleccionado) return;
    this.modalDetalle = true;
    this.actualizarVista();
    if ((!this.detalle || this.detalle.uuid !== this.seleccionado.uuid) && !this.cargandoDetalle) {
      this.cargarDetalle(this.seleccionado, 'ver');
    }
  }

  cerrarDetalleCompleto(): void {
    this.modalDetalle = false;
    this.actualizarVista();
  }

  nuevoExpediente(): void {
    this.editandoUuid = null;
    this.formulario = this.formularioVacio();
    const estadoActivo = this.catalogos.estados.find(e => e.codigo === 'ACTIVO') ?? this.catalogos.estados[0];
    if (estadoActivo) this.formulario.estadoId = estadoActivo.id;
    this.modalFormulario = true;
    this.actualizarVista();
  }

  editarExpediente(): void {
    if (!this.detalle) {
      if (this.seleccionado && !this.cargandoDetalle) this.cargarDetalle(this.seleccionado, 'editar');
      return;
    }
    const d = this.detalle;
    this.editandoUuid = d.uuid;
    this.formulario = {
      numeroExpediente: d.numeroExpediente,
      numeroCorrelativo: d.numeroCorrelativo,
      anio: d.anio,
      clienteId: d.clienteId,
      especialidadId: d.especialidadId,
      materiaId: d.materiaId,
      organoId: d.organoId,
      estadoId: d.estadoId,
      etapaProcesal: d.etapaProcesal,
      instancia: d.instancia,
      abogadoResponsableId: d.abogadoResponsableId,
      socioSupervisorId: d.socioSupervisorId,
      nivelRiesgo: d.nivelRiesgo,
      cuantia: d.cuantia,
      moneda: d.moneda || 'PEN',
      fechaInicio: d.fechaInicio,
      fechaIngresoEstudio: d.fechaIngresoEstudio,
      fechaCierre: d.fechaCierre,
      resultado: d.resultado,
      confidencial: d.confidencial,
      resumen: d.resumen,
      equipoIds: d.equipo
        .filter(m => m.usuarioId !== d.abogadoResponsableId)
        .map(m => m.usuarioId),
      contraparteIds: d.contrapartes.map(c => c.id)
    };
    this.modalFormulario = true;
    this.actualizarVista();
  }

  cerrarFormulario(): void {
    if (!this.guardando) this.modalFormulario = false;
    this.actualizarVista();
  }

  guardarExpediente(): void {
    if (!this.formularioValido()) return;
    this.guardando = true;
    this.error = '';
    this.actualizarVista();

    const eraEdicion = !!this.editandoUuid;
    const peticion: Observable<unknown> = this.editandoUuid
      ? this.expedienteService.actualizar(this.editandoUuid, this.formulario)
      : this.expedienteService.crear(this.formulario);

    peticion.subscribe({
      next: (respuesta: any) => {
        const uuid = this.editandoUuid ?? respuesta?.uuid ?? null;
        this.guardando = false;
        this.modalFormulario = false;
        if (!eraEdicion) this.pagina = 0;
        this.mostrarExito(eraEdicion ? 'Expediente actualizado correctamente.' : 'Expediente creado correctamente.');
        this.recargar(uuid);
      },
      error: (error) => {
        this.guardando = false;
        this.error = this.mensajeError(error, 'No se pudo guardar el expediente.');
        this.actualizarVista();
      }
    });
  }

  private recargar(uuidSeleccionar: string | null): void {
    const solicitud = ++this.listadoSolicitud;
    this.cargando = true;
    this.actualizarVista();
    this.cargarResumen();

    this.listarPagina().subscribe({
      next: (resultado) => {
        if (solicitud !== this.listadoSolicitud) return;
        this.expedientes = resultado.contenido;
        this.totalElementos = resultado.totalElementos;
        this.totalPaginas = resultado.totalPaginas;
        this.cargando = false;
        const seleccionado = this.expedientes.find(e => e.uuid === uuidSeleccionar) ?? this.expedientes[0] ?? null;
        if (seleccionado) this.seleccionar(seleccionado, true);
        else this.limpiarSeleccion();
        this.actualizarVista();
      },
      error: (error) => {
        if (solicitud !== this.listadoSolicitud) return;
        this.cargando = false;
        this.error = this.mensajeError(error, 'El expediente se guardó, pero no se pudo refrescar el listado.');
        this.actualizarVista();
      }
    });
  }

  materiasFormulario() {
    if (!this.formulario.especialidadId) return this.catalogos.materias;
    return this.catalogos.materias.filter(m => m.especialidadId === Number(this.formulario.especialidadId));
  }

  onEspecialidadChange(): void {
    if (!this.materiasFormulario().some(m => m.id === Number(this.formulario.materiaId))) {
      this.formulario.materiaId = null;
    }
  }

  toggleEquipo(id: number, marcado: boolean): void {
    this.formulario.equipoIds = this.toggleId(this.formulario.equipoIds, id, marcado);
  }

  toggleContraparte(id: number, marcado: boolean): void {
    this.formulario.contraparteIds = this.toggleId(this.formulario.contraparteIds, id, marcado);
  }

  private toggleId(lista: number[], id: number, marcado: boolean): number[] {
    return marcado ? Array.from(new Set([...lista, id])) : lista.filter(x => x !== id);
  }

  contiene(lista: number[], id: number): boolean {
    return lista.includes(id);
  }

  esSeleccionado(uuid: string): boolean {
    return this.seleccionadoUuid === uuid;
  }

  eventosPanel(): EventoLineaTiempo[] {
    return this.lineaTiempo.slice(0, 5);
  }

  documentosRecientes(): EventoLineaTiempo[] {
    return this.lineaTiempo.filter(e => e.tipo === 'DOCUMENTO' || e.tipo === 'RESOLUCION').slice(0, 3);
  }

  iconoEvento(tipo: EventoLineaTiempo['tipo']): string {
    const iconos: Record<EventoLineaTiempo['tipo'], string> = {
      HECHO: '●', DOCUMENTO: '▤', RESOLUCION: '§', NOTIFICACION: '✉', AUDIENCIA: '⚖',
      TAREA: '✓', PLAZO: '◷', LLAMADA: '☎', REUNION: '◉', CAMBIO_RESPONSABLE: '↻', CAMBIO_EQUIPO: '👥'
    };
    return iconos[tipo];
  }

  claseEvento(tipo: EventoLineaTiempo['tipo']): string {
    if (tipo === 'PLAZO' || tipo === 'NOTIFICACION') return 'orange-event';
    if (tipo === 'CAMBIO_RESPONSABLE' || tipo === 'RESOLUCION') return 'red-event';
    if (tipo === 'CAMBIO_EQUIPO') return 'purple-event';
    return 'purple-event';
  }

  iniciales(nombre: string | null | undefined): string {
    if (!nombre) return '—';
    return nombre.trim().split(/\s+/).slice(0, 2).map(p => p[0]?.toUpperCase()).join('');
  }

  fecha(valor: string | null | undefined, conHora = false): string {
    if (!valor) return '—';
    const soloFecha = /^\d{4}-\d{2}-\d{2}$/.test(valor);
    const fecha = soloFecha
      ? new Date(Number(valor.slice(0, 4)), Number(valor.slice(5, 7)) - 1, Number(valor.slice(8, 10)))
      : new Date(valor);
    if (Number.isNaN(fecha.getTime())) return valor;
    return new Intl.DateTimeFormat('es-PE', conHora
      ? { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }
      : { day: '2-digit', month: '2-digit', year: 'numeric' }).format(fecha);
  }

  moneda(valor: number | null, codigo = 'PEN'): string {
    if (valor === null || valor === undefined) return 'Sin cuantía';
    return new Intl.NumberFormat('es-PE', { style: 'currency', currency: codigo || 'PEN' }).format(valor);
  }

  porcentajeActivos(): number {
    return this.resumen.total ? Math.round((this.resumen.activos * 100) / this.resumen.total) : 0;
  }

  private formularioValido(): boolean {
    const f = this.formulario;
    if (!f.clienteId || !f.especialidadId || !f.estadoId || !f.abogadoResponsableId || !f.fechaInicio || !f.anio) {
      this.error = 'Completa los campos obligatorios: cliente, especialidad, estado, responsable, año y fecha de inicio.';
      this.actualizarVista();
      return false;
    }
    if (f.fechaCierre && f.fechaCierre < f.fechaInicio) {
      this.error = 'La fecha de cierre no puede ser anterior a la fecha de inicio.';
      this.actualizarVista();
      return false;
    }
    return true;
  }

  private formularioVacio(): ExpedienteGuardar {
    const ahora = new Date();
    const hoy = `${ahora.getFullYear()}-${String(ahora.getMonth() + 1).padStart(2, '0')}-${String(ahora.getDate()).padStart(2, '0')}`;
    return {
      numeroExpediente: null,
      numeroCorrelativo: null,
      anio: ahora.getFullYear(),
      clienteId: null,
      especialidadId: null,
      materiaId: null,
      organoId: null,
      estadoId: null,
      etapaProcesal: null,
      instancia: 'PRIMERA',
      abogadoResponsableId: null,
      socioSupervisorId: null,
      nivelRiesgo: 'MEDIO',
      cuantia: null,
      moneda: 'PEN',
      fechaInicio: hoy,
      fechaIngresoEstudio: hoy,
      fechaCierre: null,
      resultado: null,
      confidencial: false,
      resumen: null,
      equipoIds: [],
      contraparteIds: []
    };
  }

  private mostrarExito(mensaje: string): void {
    this.error = '';
    this.mensaje = mensaje;
    this.actualizarVista();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
    this.mensajeTimer = setTimeout(() => {
      this.mensaje = '';
      this.mensajeTimer = null;
      this.actualizarVista();
    }, 4500);
  }

  private actualizarVista(): void {
    // El proyecto usa el mismo comportamiento zoneless que Documentos.
    // Sin esto una respuesta HTTP puede quedar en memoria y mostrarse recién
    // cuando el usuario hace otro clic o escribe en un filtro.
    try {
      this.cdr.detectChanges();
    } catch {
      // El componente pudo destruirse mientras terminaba una petición.
    }
  }

  private mensajeError(error: unknown, defecto: string): string {
    // Mismo criterio de mensajes que Documentos: primero se respeta el mensaje
    // útil enviado por el backend y solo después se usa un texto genérico.
    if (error instanceof HttpErrorResponse) {
      const mensajeBackend = error.error?.mensaje || error.error?.detail || error.error?.message;
      if (mensajeBackend) return mensajeBackend;
      if (error.status === 0) {
        return 'No se pudo conectar con el servidor. Verifica que el backend esté ejecutándose.';
      }
      if (error.status === 403) return 'No tienes permisos para realizar esta operación.';
      if (error.status === 404) return 'El expediente solicitado ya no está disponible.';
      return `${defecto} (${error.status || 'sin conexión'})`;
    }
    return defecto;
  }
}
