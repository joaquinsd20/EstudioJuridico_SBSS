export type OrigenMonitor = 'MANUAL' | 'DESCARGADA' | 'CORREO' | 'CLIENTE' | 'IA';
export type EstadoVerificacionMonitor = 'PENDIENTE' | 'VERIFICADA' | 'RECHAZADA';

export interface MonitorResumen {
  expedientesMonitoreados: number;
  movimientosHoy: number;
  nuevasResolucionesHoy: number;
  pendientesVerificacion: number;
}

export interface PaginaMonitor<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  esUltima: boolean;
}

export interface ExpedienteMonitor {
  uuid: string;
  expedienteId: number;
  codigoInterno: string;
  numeroExpediente: string | null;
  cliente: string;
  organo: string | null;
  estadoProcesal: string | null;
  abogadoResponsable: string | null;
  fuentePlataforma: string;
  estadoMonitor: string;
  fechaCreacion: string;
  ultimaConsulta: string | null;
  totalConsultas: number;
  movimientos: number;
  pendientesVerificacion: number;
  ultimoMovimiento: string | null;
  fechaUltimoMovimiento: string | null;
}

export interface ConsultaMonitor {
  uuid: string;
  expedienteId: number;
  codigoInterno: string;
  numeroExpediente: string | null;
  cliente: string;
  fechaConsulta: string;
  usuarioId: number;
  usuario: string;
  fuentePlataforma: string;
  huboMovimiento: boolean;
  tipoMovimiento: string | null;
  nuevaResolucion: boolean;
  numeroResolucion: string | null;
  tipoResolucion: string | null;
  fechaResolucion: string | null;
  fechaNotificacion: string | null;
  documentoDescargado: string | null;
  notificacion: string | null;
  plazo: string | null;
  observaciones: string | null;
  origenInformacion: OrigenMonitor;
  estadoVerificacion: EstadoVerificacionMonitor;
  verificadoPor: string | null;
  fechaVerificacion: string | null;
}

export interface MonitorExpedienteOpcion {
  id: number;
  codigoInterno: string;
  numeroExpediente: string | null;
  cliente: string;
  organo: string | null;
  abogadoResponsable: string | null;
}

export interface MonitorCatalogos {
  expedientes: MonitorExpedienteOpcion[];
}

export interface MonitorFiltro {
  expediente?: string;
  cliente?: string;
  abogado?: string;
  organo?: string;
  estadoProcesal?: string;
  desde?: string;
  hasta?: string;
}

export interface ConsultaMonitorFiltro {
  expedienteId?: number;
  tipoMovimiento?: string;
  estadoVerificacion?: EstadoVerificacionMonitor | '';
  desde?: string;
  hasta?: string;
}

export interface AgregarMonitorRequest {
  expedienteId: number;
  fuentePlataforma: string;
}

export interface RegistrarConsultaMonitorRequest {
  expedienteId: number;
  fuentePlataforma: string;
  huboMovimiento: boolean;
  tipoMovimiento: string;
  nuevaResolucion: boolean;
  numeroResolucion: string;
  tipoResolucion: string;
  fechaResolucion: string | null;
  fechaNotificacion: string | null;
  documentoDescargado: string;
  notificacion: string;
  plazo: string;
  observaciones: string;
  origenInformacion: OrigenMonitor;
  estadoVerificacion: EstadoVerificacionMonitor;
}
