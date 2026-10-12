import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, forkJoin } from 'rxjs';
import { environment } from '../../../environments/environment';

import {
  ActuacionCrear,
  ActuacionDetalle,
  ActuacionFiltros,
  ActuacionLista,
  ActuacionResumen,
  CatalogosActuacion,
  DocumentoActuacionOpcion,
  EstadoActuacion,
  TipoActuacionOpcion,
  TipoPlazoOpcion
} from '../../core/models/actuacion.models';
import { AuthService } from '../../core/services/auth.service';
import { ActuacionService } from '../../core/services/actuacion.service';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-actuaciones',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './actuaciones.component.html',
  styleUrl: './actuaciones.component.css'
})
export class ActuacionesComponent implements OnInit {
  private readonly service = inject(ActuacionService);
  private readonly http = inject(HttpClient);
  private solicitudListado = 0;
  private solicitudDetalle = 0;
  pagina = 0;
  tamanoPagina = 10;
  readonly tamanosPagina = [10, 20, 40, 100];
  hayPaginaSiguiente = false;
  private readonly cdr = inject(ChangeDetectorRef);
  readonly auth = inject(AuthService);
  readonly puedeLeer = () => this.auth.tienePermiso('ACTUACION_LEER');

  actuaciones: ActuacionLista[] = [];
  detalle: ActuacionDetalle | null = null;
  seleccionado: ActuacionLista | null = null;
  documentosDisponibles: DocumentoActuacionOpcion[] = [];
  catalogos: CatalogosActuacion = { expedientes: [], tiposActuacion: [], usuarios: [], tiposPlazo: [] };
  resumen: ActuacionResumen = { total: 0, pendientes: 0, enTramite: 0, completadas: 0, minutosFacturables: 0, costoTotal: 0 };

  filtros: ActuacionFiltros = {
    texto: '', tipoId: null, expedienteId: null, responsableId: null,
    estado: '', fechaDesde: '', fechaHasta: ''
  };

  cargando = false;
  cargandoDetalle = false;
  guardando = false;
  modalNueva = false;
  error = '';
  exito = '';
  mostrarFiltrosAvanzados = false;
  form: ActuacionCrear = this.nuevoFormulario();

  readonly estados: EstadoActuacion[] = ['PENDIENTE', 'EN_TRAMITE', 'COMPLETADO', 'CANCELADO'];
  readonly prioridades = ['BAJA', 'MEDIA', 'ALTA', 'URGENTE'];
  readonly tiposEvento = ['AUDIENCIA', 'REUNION_CLIENTE', 'LLAMADA', 'DILIGENCIA', 'PRESENTACION', 'VIDEOCONFERENCIA', 'REVISION', 'INTERNO'];
  readonly categoriasDocumento = ['DEMANDA', 'CONTESTACION', 'RECURSO', 'ESCRITO', 'RESOLUCION', 'NOTIFICACION', 'CARGO', 'CONTRATO', 'PODER', 'PRUEBA', 'INFORME', 'MULTIMEDIA', 'CORREO', 'CLIENTE', 'OTRO'];

  ngOnInit(): void { if (this.puedeLeer()) this.cargarTodo(); }

  cargarTodo(seleccionarUuid?: string): void {
    this.pagina = 0;
    const solicitud = ++this.solicitudListado;
    this.cargando = true;
    this.error = '';
    forkJoin({
      actuaciones: this.listarPagina(),
      resumen: this.service.resumen(),
      catalogos: this.service.catalogos()
    }).subscribe({
      next: r => {
        if (solicitud !== this.solicitudListado) return;
        this.asignarPagina(r.actuaciones);
        this.resumen = r.resumen;
        this.catalogos = r.catalogos;
        this.cargando = false;
        const objetivo = seleccionarUuid
          ? this.actuaciones.find(a => a.uuid === seleccionarUuid)
          : (this.seleccionado ? this.actuaciones.find(a => a.uuid === this.seleccionado?.uuid) : this.actuaciones[0]);
        if (objetivo) this.seleccionar(objetivo);
        else { this.limpiarSeleccion(); }
        this.actualizarVista();
      },
      error: e => { if (solicitud !== this.solicitudListado) return; this.cargando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  buscar(): void {
    this.pagina = 0;
    this.cargarPagina();
  }

  cambiarTamanoPagina(nuevoTamano: number): void {
    if (this.cargando || !this.tamanosPagina.includes(nuevoTamano) || nuevoTamano === this.tamanoPagina) return;
    this.tamanoPagina = nuevoTamano;
    this.buscar();
  }

  cambiarPagina(nuevaPagina: number): void {
    if (this.cargando || nuevaPagina < 0 || nuevaPagina === this.pagina ||
        (nuevaPagina > this.pagina && !this.hayPaginaSiguiente)) return;
    this.pagina = nuevaPagina;
    this.cargarPagina();
  }

  private listarPagina(): Observable<ActuacionLista[]> {
    let params = new HttpParams()
      .set('limite', this.tamanoPagina + 1)
      .set('desplazamiento', this.pagina * this.tamanoPagina);
    Object.entries(this.filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') params = params.set(clave, String(valor));
    });
    return this.http.get<ActuacionLista[]>(`${environment.apiUrl}/actuaciones`, { params });
  }

  private asignarPagina(actuaciones: ActuacionLista[]): void {
    this.hayPaginaSiguiente = actuaciones.length > this.tamanoPagina;
    this.actuaciones = actuaciones.slice(0, this.tamanoPagina);
  }

  private limpiarSeleccion(): void {
    ++this.solicitudDetalle;
    this.seleccionado = null;
    this.detalle = null;
    this.cargandoDetalle = false;
  }

  private cargarPagina(): void {
    const solicitud = ++this.solicitudListado;
    this.error = '';
    this.cargando = true;
    this.listarPagina().subscribe({
      next: r => {
        if (solicitud !== this.solicitudListado) return;
        this.asignarPagina(r);
        this.cargando = false;
        if (this.actuaciones.length) this.seleccionar(this.actuaciones[0]); else { this.limpiarSeleccion(); }
        this.actualizarVista();
      },
      error: e => { if (solicitud !== this.solicitudListado) return; this.cargando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  limpiarFiltros(): void {
    this.filtros = { texto: '', tipoId: null, expedienteId: null, responsableId: null, estado: '', fechaDesde: '', fechaHasta: '' };
    this.buscar();
  }

  seleccionar(a: ActuacionLista): void {
    const solicitud = ++this.solicitudDetalle;
    this.seleccionado = a;
    this.cargandoDetalle = true;
    this.service.detalle(a.uuid).subscribe({
      next: d => { if (solicitud !== this.solicitudDetalle) return; this.detalle = d; this.cargandoDetalle = false; this.actualizarVista(); },
      error: e => { if (solicitud !== this.solicitudDetalle) return; this.cargandoDetalle = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  abrirNueva(): void {
    this.form = this.nuevoFormulario();
    this.documentosDisponibles = [];
    this.modalNueva = true;
    this.error = '';
  }

  cerrarNueva(): void { if (!this.guardando) this.modalNueva = false; }

  cambiarExpediente(): void {
    this.form.documentoExistenteId = null;
    const exp = this.catalogos.expedientes.find(x => x.id === this.form.expedienteId);
    if (exp) this.form.responsableId = exp.responsableId;
    if (!this.form.expedienteId) { this.documentosDisponibles = []; return; }
    this.service.documentos(this.form.expedienteId).subscribe({
      next: d => { this.documentosDisponibles = d; this.actualizarVista(); },
      error: e => { this.documentosDisponibles = []; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  cambiarTipoActuacion(): void {
    const tipo = this.tipoSeleccionado;
    if (!tipo) return;
    this.form.tarea.generar = tipo.generaTarea;
    this.form.plazo.generar = tipo.generaPlazo;
    this.form.evento.generar = tipo.generaEventoAgenda;
    this.form.documentoPendiente.generar = tipo.requiereDocumento;

    this.form.evento.tipo = this.tipoEventoSugerido(tipo);
    this.form.evento.titulo = `${tipo.nombre}: ${this.form.asunto || 'Nueva actuación'}`;
    this.form.documentoPendiente.categoria = this.categoriaDocumentoSugerida(tipo);
    this.form.documentoPendiente.nombre = `Pendiente - ${tipo.nombre}`;

    if (tipo.generaPlazo) {
      const equivalente = this.catalogos.tiposPlazo.find(p => p.codigo === tipo.codigo)
        ?? (tipo.codigo === 'NOTIFICACION' ? null : this.catalogos.tiposPlazo[0]);
      this.form.plazo.tipoPlazoId = equivalente?.id ?? null;
      if (equivalente) this.aplicarTipoPlazo(equivalente);
    }
  }

  cambiarTipoPlazo(): void {
    const tipo = this.catalogos.tiposPlazo.find(x => x.id === this.form.plazo.tipoPlazoId);
    if (tipo) this.aplicarTipoPlazo(tipo);
  }

  guardar(): void {
    const validacion = this.validar();
    if (validacion) { this.mostrarError(validacion); return; }
    this.guardando = true;
    this.error = '';
    this.service.crear(this.form).subscribe({
      next: r => {
        this.guardando = false;
        this.modalNueva = false;
        const extras: string[] = [];
        if (r.tareaUuid) extras.push('tarea');
        if (r.plazoUuid) extras.push('plazo');
        if (r.eventoUuid) extras.push('evento de agenda');
        if (r.documentoPendienteUuid) extras.push('documento pendiente');
        if (r.alertasGeneradas) extras.push(`${r.alertasGeneradas} alerta(s)`);
        this.mostrarExito(`Actuación registrada${extras.length ? ' y se generó: ' + extras.join(', ') : ''}.`);
        this.cargarTodo(r.uuid);
      },
      error: e => { this.guardando = false; this.mostrarError(this.mensajeError(e)); this.actualizarVista(); }
    });
  }

  get tipoSeleccionado(): TipoActuacionOpcion | null {
    return this.catalogos.tiposActuacion.find(x => x.id === this.form.tipoActuacionId) ?? null;
  }

  horas(minutos: number | null | undefined): string {
    if (!minutos) return '0 h';
    const h = Math.floor(minutos / 60), m = minutos % 60;
    return m ? `${h} h ${m} min` : `${h} h`;
  }

  dinero(valor: number | null | undefined, moneda = 'PEN'): string {
    const n = Number(valor ?? 0);
    return new Intl.NumberFormat('es-PE', { style: 'currency', currency: moneda || 'PEN', maximumFractionDigits: 2 }).format(n);
  }

  etiqueta(v: string | null | undefined): string {
    if (!v) return '—';
    const mapa: Record<string, string> = {
      PENDIENTE: 'Pendiente', EN_TRAMITE: 'En trámite', COMPLETADO: 'Completado', CANCELADO: 'Cancelado',
      HABILES: 'Días hábiles', CALENDARIO: 'Días calendario', REUNION_CLIENTE: 'Reunión con cliente',
      EN_PROCESO: 'En proceso', EN_REVISION: 'En revisión', LISTO_PRESENTAR: 'Listo para presentar',
      PLAZO_PROXIMO: 'Plazo próximo', PLAZO_VENCIDO: 'Plazo vencido', GENERICA: 'Seguimiento'
    };
    return mapa[v] ?? v.toLowerCase().replaceAll('_', ' ').replace(/(^|\s)\S/g, s => s.toUpperCase());
  }

  iniciales(nombre: string): string {
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map(x => x[0]).join('').toUpperCase();
  }

  private nuevoFormulario(): ActuacionCrear {
    const ahora = this.fechaInput(new Date());
    const manana = new Date(); manana.setDate(manana.getDate() + 1); manana.setHours(18, 0, 0, 0);
    const fin = new Date(); fin.setHours(fin.getHours() + 1);
    return {
      expedienteId: null, tipoActuacionId: null, fechaHora: ahora, responsableId: null,
      asunto: '', descripcion: '', estado: 'PENDIENTE', resultado: '', proximaAccion: '',
      tiempoMinutos: null, facturable: true, costo: null, moneda: 'PEN', documentoExistenteId: null,
      tarea: { generar: false, titulo: '', descripcion: '', prioridad: 'MEDIA', fechaLimite: this.fechaInput(manana), supervisorId: null, tiempoEstimadoMinutos: null },
      plazo: { generar: false, tipoPlazoId: null, fechaNotificacion: ahora.slice(0, 10), diasPlazo: null, tipoComputo: 'HABILES', nivelRiesgo: 'ALTO', revisorId: null, requiereRevisionProfesional: false, motivoRevision: '', actuacionRequerida: '' },
      evento: { generar: false, tipo: 'PRESENTACION', titulo: '', descripcion: '', fechaInicio: ahora, fechaFin: this.fechaInput(fin), modalidad: 'PRESENCIAL', ubicacion: '', enlaceVirtual: '', recordatorioMinutos: 60 },
      alerta: { generar: false, fechaProgramada: this.fechaInput(manana), mensaje: '', severidad: 'MEDIA' },
      documentoPendiente: { generar: false, nombre: '', categoria: 'OTRO', descripcion: '', confidencial: false }
    };
  }

  private validar(): string | null {
    if (!this.form.expedienteId) return 'Selecciona el expediente.';
    if (!this.form.tipoActuacionId) return 'Selecciona el tipo de actuación.';
    if (!this.form.fechaHora) return 'Indica la fecha y hora de la actuación.';
    if (!this.form.responsableId) return 'Selecciona el responsable.';
    if (!this.form.asunto.trim()) return 'Ingresa el asunto de la actuación.';
    if (this.form.plazo.generar && !this.form.plazo.tipoPlazoId) return 'La actuación genera un plazo: selecciona el tipo de plazo.';
    if (this.form.plazo.generar && (!this.form.plazo.diasPlazo || this.form.plazo.diasPlazo < 1)) return 'Indica los días del plazo.';
    if (this.form.plazo.requiereRevisionProfesional && !this.form.plazo.motivoRevision.trim()) return 'Indica el motivo de la revisión profesional.';
    if (this.form.evento.generar && (!this.form.evento.fechaInicio || !this.form.evento.fechaFin)) return 'Completa la fecha de inicio y fin del evento.';
    if (this.form.alerta.generar && !this.form.alerta.fechaProgramada) return 'Indica cuándo debe mostrarse la alerta.';
    return null;
  }

  private aplicarTipoPlazo(t: TipoPlazoOpcion): void {
    this.form.plazo.diasPlazo = t.diasPorDefecto;
    this.form.plazo.tipoComputo = t.tipoComputoDefecto;
    this.form.plazo.nivelRiesgo = t.nivelRiesgoDefecto;
  }

  private tipoEventoSugerido(t: TipoActuacionOpcion): string {
    if (t.categoria === 'AUDIENCIA') return 'AUDIENCIA';
    if (t.categoria === 'DILIGENCIA') return 'DILIGENCIA';
    if (t.categoria === 'COMUNICACION') return t.codigo.includes('LLAMADA') ? 'LLAMADA' : 'REUNION_CLIENTE';
    return 'PRESENTACION';
  }

  private categoriaDocumentoSugerida(t: TipoActuacionOpcion): string {
    if (t.codigo.includes('DEMANDA')) return 'DEMANDA';
    if (t.codigo.includes('CONTESTACION')) return 'CONTESTACION';
    if (t.codigo.includes('RECURSO')) return 'RECURSO';
    if (t.categoria === 'NOTIFICACION') return 'NOTIFICACION';
    if (t.categoria === 'ESCRITO') return 'ESCRITO';
    return 'OTRO';
  }

  private fechaInput(fecha: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${fecha.getFullYear()}-${pad(fecha.getMonth() + 1)}-${pad(fecha.getDate())}T${pad(fecha.getHours())}:${pad(fecha.getMinutes())}`;
  }

  private mostrarError(m: string): void { this.error = m; this.exito = ''; }
  private mostrarExito(m: string): void { this.exito = m; this.error = ''; }
  private actualizarVista(): void { try { this.cdr.detectChanges(); } catch { /* componente destruido */ } }
  private mensajeError(e: unknown): string {
    if (e instanceof HttpErrorResponse) {
      const body = e.error as any;
      return body?.mensaje || body?.message || body?.detail || (typeof body === 'string' ? body : '') || `Error ${e.status}: no se pudo completar la operación.`;
    }
    return 'No se pudo completar la operación.';
  }
}
