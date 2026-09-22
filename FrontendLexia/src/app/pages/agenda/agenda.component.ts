import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import {
  AgendaCatalogos,
  AgendaResumen,
  AgendaUsuarioOpcion,
  EstadoEvento,
  EventoAgenda,
  EventoAgendaActualizar,
  EventoAgendaDetalle,
  EventoAgendaGuardar,
  ModalidadEvento,
  OrigenInformacion,
  TipoEventoAgenda
} from '../../core/models/agenda.models';
import { AgendaService } from '../../core/services/agenda.service';
import { AuthService } from '../../core/services/auth.service';
import { PanelComponent } from '../panel/panel.component';

type VistaAgenda = 'MES' | 'SEMANA' | 'DIA';

interface DiaCalendario {
  fecha: Date;
  clave: string;
  numero: number;
  mesActual: boolean;
  hoy: boolean;
}

interface TipoVisual {
  codigo: TipoEventoAgenda;
  etiqueta: string;
  clase: string;
}

@Component({
  selector: 'app-agenda',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.css'
})
export class AgendaComponent implements OnInit, OnDestroy {
  private readonly agendaService = inject(AgendaService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly tipos: TipoVisual[] = [
    { codigo: 'AUDIENCIA', etiqueta: 'Audiencias', clase: 'audiencia' },
    { codigo: 'REUNION_CLIENTE', etiqueta: 'Reuniones', clase: 'reunion' },
    { codigo: 'LLAMADA', etiqueta: 'Llamadas', clase: 'llamada' },
    { codigo: 'VIDEOCONFERENCIA', etiqueta: 'Videoconferencias', clase: 'video' },
    { codigo: 'PRESENTACION', etiqueta: 'Presentaciones', clase: 'presentacion' },
    { codigo: 'VENCIMIENTO_PLAZO', etiqueta: 'Vencimientos', clase: 'plazo' },
    { codigo: 'DILIGENCIA', etiqueta: 'Diligencias', clase: 'diligencia' },
    { codigo: 'REVISION', etiqueta: 'Revisiones', clase: 'revision' },
    { codigo: 'INTERNO', etiqueta: 'Internos', clase: 'interno' }
  ];

  readonly modalidades: ModalidadEvento[] = ['PRESENCIAL', 'VIRTUAL', 'HIBRIDA'];
  readonly estados: EstadoEvento[] = ['PROGRAMADO', 'CONFIRMADO', 'REPROGRAMADO', 'REALIZADO', 'CANCELADO'];
  readonly origenes: OrigenInformacion[] = ['MANUAL', 'DESCARGADA', 'CORREO', 'CLIENTE', 'IA'];
  readonly recordatorios = [
    { valor: null, etiqueta: 'Sin recordatorio' },
    { valor: 15, etiqueta: '15 minutos antes' },
    { valor: 30, etiqueta: '30 minutos antes' },
    { valor: 60, etiqueta: '1 hora antes' },
    { valor: 120, etiqueta: '2 horas antes' },
    { valor: 1440, etiqueta: '1 día antes' },
    { valor: 2880, etiqueta: '2 días antes' },
    { valor: 10080, etiqueta: '1 semana antes' }
  ];

  readonly diasSemana = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  readonly puedeGestionar = this.auth.tienePermiso('AGENDA_CREAR');

  vista: VistaAgenda = 'MES';
  fechaReferencia = this.inicioDia(new Date());
  dias: DiaCalendario[] = [];
  eventos: EventoAgenda[] = [];
  detalle: EventoAgendaDetalle | null = null;
  catalogos: AgendaCatalogos = { expedientes: [], usuarios: [] };
  resumen: AgendaResumen = {
    eventosHoy: 0,
    proximosSieteDias: 0,
    audienciasMes: 0,
    pendientesVerificacion: 0,
    totalMes: 0
  };

  filtroTexto = '';
  filtroTipo = '';
  filtroEstado = '';
  filtroExpediente: number | null = null;
  filtroParticipante: number | null = null;

  cargando = false;
  guardando = false;
  error = '';
  exito = '';
  modalFormulario = false;
  modalDetalle = false;
  modalReprogramar = false;
  modalResultado = false;
  modalConfirmarCancelacion = false;
  editandoUuid: string | null = null;

  formulario: EventoAgendaGuardar & { estado: EstadoEvento; resultado: string } = this.formularioVacio();
  reprogramacion = { fechaInicio: '', fechaFin: '', motivo: '', recordatorioMinutos: null as number | null };
  resultadoForm = { resultado: '', verificado: true };

  private mensajeTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.reconstruirVista();
    this.cargando = true;
    forkJoin({
      catalogos: this.agendaService.catalogos(),
      resumen: this.agendaService.resumen()
    }).pipe(finalize(() => {
      this.cargando = false;
      this.actualizarVista();
    })).subscribe({
      next: ({ catalogos, resumen }) => {
        this.catalogos = catalogos;
        this.resumen = resumen;
        this.cargarEventos();
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  ngOnDestroy(): void {
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
  }

  cambiarVista(vista: VistaAgenda): void {
    if (this.vista === vista) return;
    this.vista = vista;
    this.reconstruirVista();
    this.cargarEventos();
  }

  periodoAnterior(): void {
    const f = new Date(this.fechaReferencia);
    if (this.vista === 'MES') f.setMonth(f.getMonth() - 1);
    else if (this.vista === 'SEMANA') f.setDate(f.getDate() - 7);
    else f.setDate(f.getDate() - 1);
    this.fechaReferencia = this.inicioDia(f);
    this.reconstruirVista();
    this.cargarEventos();
  }

  periodoSiguiente(): void {
    const f = new Date(this.fechaReferencia);
    if (this.vista === 'MES') f.setMonth(f.getMonth() + 1);
    else if (this.vista === 'SEMANA') f.setDate(f.getDate() + 7);
    else f.setDate(f.getDate() + 1);
    this.fechaReferencia = this.inicioDia(f);
    this.reconstruirVista();
    this.cargarEventos();
  }

  irHoy(): void {
    this.fechaReferencia = this.inicioDia(new Date());
    this.reconstruirVista();
    this.cargarEventos();
  }

  aplicarFiltros(): void {
    this.cargarEventos();
  }

  limpiarFiltros(): void {
    this.filtroTexto = '';
    this.filtroTipo = '';
    this.filtroEstado = '';
    this.filtroExpediente = null;
    this.filtroParticipante = null;
    this.cargarEventos();
  }

  filtrarTipo(tipo: string): void {
    this.filtroTipo = this.filtroTipo === tipo ? '' : tipo;
    this.cargarEventos();
  }

  cantidadTipo(tipo: string): number {
    return this.eventos.filter(e => e.tipo === tipo).length;
  }

  cargarEventos(): void {
    const { desde, hasta } = this.rangoConsulta();
    this.cargando = true;
    this.agendaService.listar({
      desde: this.isoLocal(desde),
      hasta: this.isoLocal(hasta),
      texto: this.filtroTexto,
      tipo: this.filtroTipo,
      estado: this.filtroEstado,
      expedienteId: this.filtroExpediente,
      participanteId: this.filtroParticipante
    }).pipe(finalize(() => {
      this.cargando = false;
      this.actualizarVista();
    })).subscribe({
      next: eventos => this.eventos = eventos,
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  eventosDelDia(dia: DiaCalendario): EventoAgenda[] {
    return this.eventos
      .filter(e => this.eventoIncluyeFecha(e, dia.clave))
      .sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));
  }

  get eventosDelDiaActual(): EventoAgenda[] {
    const clave = this.claveFecha(this.fechaReferencia);
    return this.eventos
      .filter(e => this.eventoIncluyeFecha(e, clave))
      .sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));
  }

  get eventosProximos(): EventoAgenda[] {
    const ahora = new Date();
    return this.eventos
      .filter(e => e.estado !== 'CANCELADO' && new Date(e.fechaFin) >= ahora)
      .sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio))
      .slice(0, 8);
  }

  get tituloPeriodo(): string {
    if (this.vista === 'MES') {
      return new Intl.DateTimeFormat('es-PE', { month: 'long', year: 'numeric' })
        .format(this.fechaReferencia).replace(/^./, s => s.toUpperCase());
    }
    if (this.vista === 'DIA') {
      return new Intl.DateTimeFormat('es-PE', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })
        .format(this.fechaReferencia).replace(/^./, s => s.toUpperCase());
    }
    const { desde, hasta } = this.rangoConsulta();
    const a = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short' }).format(desde);
    const b = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }).format(hasta);
    return `${a} – ${b}`;
  }

  abrirNuevo(fecha?: Date): void {
    if (!this.puedeGestionar) return;
    this.editandoUuid = null;
    this.formulario = this.formularioVacio(fecha);
    this.modalFormulario = true;
    this.modalDetalle = false;
  }

  abrirNuevoEnDia(dia: DiaCalendario): void {
    const fecha = new Date(dia.fecha);
    fecha.setHours(9, 0, 0, 0);
    this.abrirNuevo(fecha);
  }

  abrirDetalle(uuid: string): void {
    this.agendaService.detalle(uuid).subscribe({
      next: detalle => {
        this.detalle = detalle;
        this.modalDetalle = true;
        this.actualizarVista();
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  cerrarDetalle(): void {
    this.modalDetalle = false;
    this.detalle = null;
  }

  editarSeleccionado(): void {
    if (!this.detalle || !this.puedeGestionar) return;
    const e = this.detalle.evento;
    this.editandoUuid = e.uuid;
    this.formulario = {
      expedienteId: e.expedienteId,
      tipo: e.tipo,
      titulo: e.titulo,
      descripcion: e.descripcion ?? '',
      fechaInicio: this.paraInputFecha(e.fechaInicio),
      fechaFin: this.paraInputFecha(e.fechaFin),
      todoElDia: e.todoElDia,
      modalidad: e.modalidad,
      ubicacion: e.ubicacion ?? '',
      enlaceVirtual: e.enlaceVirtual ?? '',
      recordatorioMinutos: e.recordatorioMinutos,
      colorHex: e.colorHex ?? this.colorTipo(e.tipo),
      origenInformacion: e.origenInformacion,
      requiereVerificacion: e.requiereVerificacion,
      participanteIds: this.detalle.participantes
        .filter(p => p.tipoParticipacion !== 'ORGANIZADOR')
        .map(p => p.id),
      estado: e.estado,
      resultado: e.resultado ?? ''
    };
    this.modalDetalle = false;
    this.modalFormulario = true;
  }

  cerrarFormulario(): void {
    if (this.guardando) return;
    this.modalFormulario = false;
    this.editandoUuid = null;
  }

  guardarEvento(): void {
    if (!this.formulario.titulo.trim()) {
      this.mostrarError('El título del evento es obligatorio.');
      return;
    }
    if (!this.formulario.fechaInicio || !this.formulario.fechaFin) {
      this.mostrarError('Indica la fecha y hora de inicio y fin.');
      return;
    }
    if (this.formulario.fechaFin < this.formulario.fechaInicio) {
      this.mostrarError('La fecha fin no puede ser anterior a la fecha inicio.');
      return;
    }

    const base: EventoAgendaGuardar = {
      expedienteId: this.formulario.expedienteId || null,
      tipo: this.formulario.tipo,
      titulo: this.formulario.titulo.trim(),
      descripcion: this.formulario.descripcion.trim(),
      fechaInicio: this.formulario.fechaInicio,
      fechaFin: this.formulario.fechaFin,
      todoElDia: this.formulario.todoElDia,
      modalidad: this.formulario.modalidad,
      ubicacion: this.formulario.ubicacion.trim(),
      enlaceVirtual: this.formulario.enlaceVirtual.trim(),
      recordatorioMinutos: this.formulario.recordatorioMinutos,
      colorHex: this.formulario.colorHex,
      origenInformacion: this.formulario.origenInformacion,
      requiereVerificacion: this.formulario.requiereVerificacion,
      participanteIds: this.formulario.participanteIds
    };

    this.guardando = true;
    const peticion = this.editandoUuid
      ? this.agendaService.actualizar(this.editandoUuid, {
          ...base,
          estado: this.formulario.estado,
          resultado: this.formulario.resultado.trim()
        } satisfies EventoAgendaActualizar)
      : this.agendaService.crear(base);

    peticion.pipe(finalize(() => {
      this.guardando = false;
      this.actualizarVista();
    })).subscribe({
      next: detalle => {
        this.modalFormulario = false;
        this.editandoUuid = null;
        this.detalle = detalle;
        this.modalDetalle = true;
        this.mostrarExito('Evento guardado correctamente.');
        this.refrescarDespuesDeCambio();
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  abrirReprogramacion(): void {
    if (!this.detalle || !this.puedeGestionar) return;
    const e = this.detalle.evento;
    this.reprogramacion = {
      fechaInicio: this.paraInputFecha(e.fechaInicio),
      fechaFin: this.paraInputFecha(e.fechaFin),
      motivo: '',
      recordatorioMinutos: e.recordatorioMinutos
    };
    this.modalReprogramar = true;
  }

  guardarReprogramacion(): void {
    if (!this.detalle) return;
    if (!this.reprogramacion.motivo.trim()) {
      this.mostrarError('Indica el motivo de la reprogramación.');
      return;
    }
    if (this.reprogramacion.fechaFin < this.reprogramacion.fechaInicio) {
      this.mostrarError('La fecha fin no puede ser anterior a la fecha inicio.');
      return;
    }
    this.guardando = true;
    this.agendaService.reprogramar(
      this.detalle.evento.uuid,
      this.reprogramacion.fechaInicio,
      this.reprogramacion.fechaFin,
      this.reprogramacion.motivo.trim(),
      this.reprogramacion.recordatorioMinutos
    ).pipe(finalize(() => {
      this.guardando = false;
      this.actualizarVista();
    })).subscribe({
      next: detalle => {
        this.detalle = detalle;
        this.modalReprogramar = false;
        this.mostrarExito('Evento reprogramado y cambio registrado en el historial.');
        this.refrescarDespuesDeCambio();
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  abrirResultado(): void {
    if (!this.detalle || !this.puedeGestionar) return;
    this.resultadoForm = {
      resultado: this.detalle.evento.resultado ?? '',
      verificado: !this.detalle.evento.requiereVerificacion
    };
    this.modalResultado = true;
  }

  guardarResultado(): void {
    if (!this.detalle || !this.resultadoForm.resultado.trim()) {
      this.mostrarError('Escribe el resultado del evento.');
      return;
    }
    this.guardando = true;
    this.agendaService.registrarResultado(
      this.detalle.evento.uuid,
      this.resultadoForm.resultado.trim(),
      this.resultadoForm.verificado
    ).pipe(finalize(() => {
      this.guardando = false;
      this.actualizarVista();
    })).subscribe({
      next: detalle => {
        this.detalle = detalle;
        this.modalResultado = false;
        this.mostrarExito('Resultado registrado correctamente.');
        this.refrescarDespuesDeCambio();
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  abrirConfirmacionCancelacion(): void {
    if (!this.detalle || !this.puedeGestionar || this.detalle.evento.estado === 'CANCELADO') return;
    this.modalConfirmarCancelacion = true;
  }

  cerrarConfirmacionCancelacion(): void {
    if (this.guardando) return;
    this.modalConfirmarCancelacion = false;
  }

  confirmarCancelacion(): void {
    if (!this.detalle || !this.puedeGestionar || this.detalle.evento.estado === 'CANCELADO') return;

    this.guardando = true;
    this.agendaService.cancelar(this.detalle.evento.uuid)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: detalle => {
          this.detalle = detalle;
          this.modalConfirmarCancelacion = false;
          this.mostrarExito('Evento cancelado correctamente.');
          this.refrescarDespuesDeCambio();
        },
        error: e => {
          this.modalConfirmarCancelacion = false;
          this.mostrarError(this.mensajeError(e));
        }
      });
  }

  seleccionarParticipante(usuario: AgendaUsuarioOpcion, marcado: boolean): void {
    const ids = new Set(this.formulario.participanteIds);
    marcado ? ids.add(usuario.id) : ids.delete(usuario.id);
    this.formulario.participanteIds = [...ids];
  }

  participanteSeleccionado(id: number): boolean {
    return this.formulario.participanteIds.includes(id);
  }

  origenCambiado(): void {
    if (this.formulario.origenInformacion === 'IA') this.formulario.requiereVerificacion = true;
  }

  tipoCambiado(): void {
    this.formulario.colorHex = this.colorTipo(this.formulario.tipo);
    if (this.formulario.tipo === 'VIDEOCONFERENCIA') this.formulario.modalidad = 'VIRTUAL';
  }

  claseTipo(tipo: string): string {
    return this.tipos.find(t => t.codigo === tipo)?.clase ?? 'interno';
  }

  etiquetaTipo(tipo: string): string {
    return ({
      AUDIENCIA: 'Audiencia', REUNION_CLIENTE: 'Reunión', VENCIMIENTO_PLAZO: 'Vencimiento',
      LLAMADA: 'Llamada', DILIGENCIA: 'Diligencia', PRESENTACION: 'Presentación',
      VIDEOCONFERENCIA: 'Videoconferencia', REVISION: 'Revisión', INTERNO: 'Interno'
    } as Record<string, string>)[tipo] ?? tipo;
  }

  etiquetaEstado(estado: string): string {
    return ({
      PROGRAMADO: 'Programado', CONFIRMADO: 'Confirmado', REPROGRAMADO: 'Reprogramado',
      REALIZADO: 'Realizado', CANCELADO: 'Cancelado'
    } as Record<string, string>)[estado] ?? estado;
  }

  etiquetaModalidad(modalidad: string): string {
    return ({ PRESENCIAL: 'Presencial', VIRTUAL: 'Virtual', HIBRIDA: 'Híbrida' } as Record<string, string>)[modalidad] ?? modalidad;
  }

  etiquetaOrigen(origen: string): string {
    return ({
      MANUAL: 'Manual', DESCARGADA: 'Descargada', CORREO: 'Recibida por correo',
      CLIENTE: 'Enviada por cliente', IA: 'Generada por IA'
    } as Record<string, string>)[origen] ?? origen;
  }

  recordatorioTexto(minutos: number | null): string {
    return this.recordatorios.find(r => r.valor === minutos)?.etiqueta ?? (minutos == null ? 'Sin recordatorio' : `${minutos} min antes`);
  }

  formatoFechaHora(fecha: string): string {
    return new Intl.DateTimeFormat('es-PE', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(new Date(fecha));
  }

  formatoHora(fecha: string): string {
    return new Intl.DateTimeFormat('es-PE', { hour: '2-digit', minute: '2-digit' }).format(new Date(fecha));
  }

  expedienteTexto(e: EventoAgenda): string {
    if (!e.expedienteId) return 'Evento interno / sin expediente';
    return `${e.expedienteCodigo ?? 'Expediente'}${e.numeroExpediente ? ' · ' + e.numeroExpediente : ''}`;
  }

  private refrescarDespuesDeCambio(): void {
    forkJoin({ eventos: this.agendaService.listar({ ...this.filtroActual() }), resumen: this.agendaService.resumen() })
      .subscribe({
        next: ({ eventos, resumen }) => {
          this.eventos = eventos;
          this.resumen = resumen;
          this.actualizarVista();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  private filtroActual() {
    const { desde, hasta } = this.rangoConsulta();
    return {
      desde: this.isoLocal(desde),
      hasta: this.isoLocal(hasta),
      texto: this.filtroTexto,
      tipo: this.filtroTipo,
      estado: this.filtroEstado,
      expedienteId: this.filtroExpediente,
      participanteId: this.filtroParticipante
    };
  }

  private reconstruirVista(): void {
    if (this.vista === 'MES') this.dias = this.construirMes();
    else if (this.vista === 'SEMANA') this.dias = this.construirSemana();
    else this.dias = [this.diaCalendario(this.fechaReferencia, true)];
  }

  private construirMes(): DiaCalendario[] {
    const primero = new Date(this.fechaReferencia.getFullYear(), this.fechaReferencia.getMonth(), 1);
    const inicio = this.inicioSemana(primero);
    const dias: DiaCalendario[] = [];
    for (let i = 0; i < 42; i++) {
      const fecha = new Date(inicio);
      fecha.setDate(inicio.getDate() + i);
      dias.push(this.diaCalendario(fecha, fecha.getMonth() === this.fechaReferencia.getMonth()));
    }
    return dias;
  }

  private construirSemana(): DiaCalendario[] {
    const inicio = this.inicioSemana(this.fechaReferencia);
    return Array.from({ length: 7 }, (_, i) => {
      const fecha = new Date(inicio);
      fecha.setDate(inicio.getDate() + i);
      return this.diaCalendario(fecha, true);
    });
  }

  private diaCalendario(fecha: Date, mesActual: boolean): DiaCalendario {
    return {
      fecha: this.inicioDia(fecha),
      clave: this.claveFecha(fecha),
      numero: fecha.getDate(),
      mesActual,
      hoy: this.claveFecha(fecha) === this.claveFecha(new Date())
    };
  }

  private rangoConsulta(): { desde: Date; hasta: Date } {
    if (this.vista === 'MES') {
      const desde = this.dias[0]?.fecha ?? this.inicioDia(this.fechaReferencia);
      const hasta = this.finDia(this.dias[this.dias.length - 1]?.fecha ?? this.fechaReferencia);
      return { desde, hasta };
    }
    if (this.vista === 'SEMANA') {
      const desde = this.inicioSemana(this.fechaReferencia);
      const hasta = this.finDia(new Date(desde.getFullYear(), desde.getMonth(), desde.getDate() + 6));
      return { desde, hasta };
    }
    return { desde: this.inicioDia(this.fechaReferencia), hasta: this.finDia(this.fechaReferencia) };
  }

  private eventoIncluyeFecha(evento: EventoAgenda, clave: string): boolean {
    const inicio = evento.fechaInicio.slice(0, 10);
    const fin = evento.fechaFin.slice(0, 10);
    return clave >= inicio && clave <= fin;
  }

  private formularioVacio(fecha?: Date): EventoAgendaGuardar & { estado: EstadoEvento; resultado: string } {
    const inicio = fecha ? new Date(fecha) : new Date();
    inicio.setSeconds(0, 0);
    if (!fecha) {
      const minutos = inicio.getMinutes();
      inicio.setMinutes(minutos < 30 ? 30 : 0);
      if (minutos >= 30) inicio.setHours(inicio.getHours() + 1);
    }
    const fin = new Date(inicio);
    fin.setHours(fin.getHours() + 1);
    return {
      expedienteId: null,
      tipo: 'AUDIENCIA',
      titulo: '',
      descripcion: '',
      fechaInicio: this.paraInputFecha(inicio),
      fechaFin: this.paraInputFecha(fin),
      todoElDia: false,
      modalidad: 'PRESENCIAL',
      ubicacion: '',
      enlaceVirtual: '',
      recordatorioMinutos: 60,
      colorHex: this.colorTipo('AUDIENCIA'),
      origenInformacion: 'MANUAL',
      requiereVerificacion: false,
      participanteIds: [],
      estado: 'PROGRAMADO',
      resultado: ''
    };
  }

  private colorTipo(tipo: string): string {
    return ({
      AUDIENCIA: '#448EA2', REUNION_CLIENTE: '#7952A8', VENCIMIENTO_PLAZO: '#CB5B54',
      LLAMADA: '#D08A34', DILIGENCIA: '#58A96D', PRESENTACION: '#3976B8',
      VIDEOCONFERENCIA: '#5B67C8', REVISION: '#7D8795', INTERNO: '#64748B'
    } as Record<string, string>)[tipo] ?? '#64748B';
  }

  private inicioSemana(fecha: Date): Date {
    const f = this.inicioDia(fecha);
    const dia = f.getDay();
    const desplazamiento = dia === 0 ? -6 : 1 - dia;
    f.setDate(f.getDate() + desplazamiento);
    return f;
  }

  private inicioDia(fecha: Date): Date {
    const f = new Date(fecha);
    f.setHours(0, 0, 0, 0);
    return f;
  }

  private finDia(fecha: Date): Date {
    const f = new Date(fecha);
    f.setHours(23, 59, 59, 0);
    return f;
  }

  private claveFecha(fecha: Date): string {
    const y = fecha.getFullYear();
    const m = String(fecha.getMonth() + 1).padStart(2, '0');
    const d = String(fecha.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  private isoLocal(fecha: Date): string {
    const ymd = this.claveFecha(fecha);
    const h = String(fecha.getHours()).padStart(2, '0');
    const m = String(fecha.getMinutes()).padStart(2, '0');
    const s = String(fecha.getSeconds()).padStart(2, '0');
    return `${ymd}T${h}:${m}:${s}`;
  }

  private paraInputFecha(fecha: string | Date): string {
    const d = typeof fecha === 'string' ? new Date(fecha) : fecha;
    const h = String(d.getHours()).padStart(2, '0');
    const m = String(d.getMinutes()).padStart(2, '0');
    return `${this.claveFecha(d)}T${h}:${m}`;
  }

  private mostrarExito(mensaje: string): void {
    this.error = '';
    this.exito = mensaje;
    this.actualizarVista();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
    this.mensajeTimer = setTimeout(() => {
      this.exito = '';
      this.mensajeTimer = null;
      this.actualizarVista();
    }, 4500);
  }

  private mostrarError(mensaje: string): void {
    this.exito = '';
    this.error = mensaje;
    this.actualizarVista();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
    this.mensajeTimer = setTimeout(() => {
      this.error = '';
      this.mensajeTimer = null;
      this.actualizarVista();
    }, 6500);
  }

  private mensajeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const mensajeBackend = error.error?.mensaje || error.error?.detail || error.error?.message;
      if (mensajeBackend) return mensajeBackend;
      if (error.status === 0) return 'No se pudo conectar con el servidor. Verifica que el backend esté ejecutándose.';
      if (error.status === 403) return 'No tienes permisos para realizar esta operación en Agenda.';
      return `No se pudo completar la operación (${error.status || 'sin conexión'}).`;
    }
    return 'Ocurrió un error inesperado.';
  }

  private actualizarVista(): void {
    try { this.cdr.detectChanges(); } catch { /* componente destruido */ }
  }
}
