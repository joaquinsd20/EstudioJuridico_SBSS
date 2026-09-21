export type EstadoActuacion = 'PENDIENTE' | 'EN_TRAMITE' | 'COMPLETADO' | 'CANCELADO';

export interface ActuacionResumen {
  total: number;
  pendientes: number;
  enTramite: number;
  completadas: number;
  minutosFacturables: number;
  costoTotal: number;
}

export interface ActuacionLista {
  uuid: string;
  fechaHora: string;
  tipoId: number;
  tipoCodigo: string;
  tipoNombre: string;
  categoria: string;
  expedienteId: number;
  expedienteCodigo: string;
  numeroExpediente: string | null;
  clienteNombre: string;
  asunto: string;
  responsableId: number;
  responsable: string;
  estado: EstadoActuacion;
  resultado: string | null;
  proximaAccion: string | null;
  tiempoMinutos: number | null;
  facturable: boolean;
  costo: number | null;
  moneda: string;
  documentos: number;
  tareas: number;
  plazos: number;
  eventos: number;
}

export interface DocumentoRelacionado {
  uuid: string; nombre: string; categoria: string; estado: string;
  nombreArchivo: string | null; numeroVersion: number | null;
}
export interface TareaRelacionada {
  uuid: string; titulo: string; estado: string; prioridad: string;
  fechaLimite: string; responsable: string;
}
export interface PlazoRelacionado {
  uuid: string; descripcion: string; estado: string; nivelRiesgo: string;
  fechaVencimiento: string; fechaVencimientoInterna: string | null;
  requiereRevisionProfesional: boolean;
}
export interface EventoRelacionado {
  uuid: string; tipo: string; titulo: string; fechaInicio: string; fechaFin: string;
  estado: string; modalidad: string;
}
export interface AlertaRelacionada {
  id: number; tipo: string; mensaje: string; severidad: string; fechaProgramada: string; estado: string;
}

export interface ActuacionDetalle {
  actuacion: ActuacionLista;
  descripcion: string | null;
  origen: string;
  requiereVerificacion: boolean;
  documentos: DocumentoRelacionado[];
  tareas: TareaRelacionada[];
  plazos: PlazoRelacionado[];
  eventos: EventoRelacionado[];
  alertas: AlertaRelacionada[];
}

export interface ExpedienteActuacionOpcion {
  id: number; codigo: string; numeroExpediente: string | null; clienteNombre: string;
  responsableId: number; responsable: string;
}
export interface TipoActuacionOpcion {
  id: number; codigo: string; nombre: string; categoria: string;
  generaPlazo: boolean; generaTarea: boolean; generaEventoAgenda: boolean; requiereDocumento: boolean;
}
export interface UsuarioActuacionOpcion { id: number; nombre: string; cargo: string | null; }
export interface TipoPlazoOpcion {
  id: number; codigo: string; nombre: string; diasPorDefecto: number | null;
  tipoComputoDefecto: 'HABILES' | 'CALENDARIO'; nivelRiesgoDefecto: string;
}
export interface DocumentoActuacionOpcion {
  id: number; uuid: string; nombre: string; categoria: string; estado: string; archivo: string | null;
}
export interface CatalogosActuacion {
  expedientes: ExpedienteActuacionOpcion[];
  tiposActuacion: TipoActuacionOpcion[];
  usuarios: UsuarioActuacionOpcion[];
  tiposPlazo: TipoPlazoOpcion[];
}

export interface ActuacionFiltros {
  texto?: string; tipoId?: number | null; expedienteId?: number | null; responsableId?: number | null;
  estado?: string; fechaDesde?: string; fechaHasta?: string;
}

export interface ActuacionCrear {
  expedienteId: number | null;
  tipoActuacionId: number | null;
  fechaHora: string;
  responsableId: number | null;
  asunto: string;
  descripcion: string;
  estado: EstadoActuacion;
  resultado: string;
  proximaAccion: string;
  tiempoMinutos: number | null;
  facturable: boolean;
  costo: number | null;
  moneda: string;
  documentoExistenteId: number | null;
  tarea: {
    generar: boolean; titulo: string; descripcion: string; prioridad: string;
    fechaLimite: string; supervisorId: number | null; tiempoEstimadoMinutos: number | null;
  };
  plazo: {
    generar: boolean; tipoPlazoId: number | null; fechaNotificacion: string;
    diasPlazo: number | null; tipoComputo: string; nivelRiesgo: string;
    revisorId: number | null; requiereRevisionProfesional: boolean; motivoRevision: string;
    actuacionRequerida: string;
  };
  evento: {
    generar: boolean; tipo: string; titulo: string; descripcion: string;
    fechaInicio: string; fechaFin: string; modalidad: string; ubicacion: string;
    enlaceVirtual: string; recordatorioMinutos: number | null;
  };
  alerta: { generar: boolean; fechaProgramada: string; mensaje: string; severidad: string; };
  documentoPendiente: { generar: boolean; nombre: string; categoria: string; descripcion: string; confidencial: boolean; };
}

export interface ActuacionCrearResponse {
  uuid: string; tareaUuid: string | null; plazoUuid: string | null; eventoUuid: string | null;
  documentoPendienteUuid: string | null; alertasGeneradas: number;
}
