export type TipoEventoAgenda =
  | 'AUDIENCIA'
  | 'REUNION_CLIENTE'
  | 'VENCIMIENTO_PLAZO'
  | 'LLAMADA'
  | 'DILIGENCIA'
  | 'PRESENTACION'
  | 'VIDEOCONFERENCIA'
  | 'REVISION'
  | 'INTERNO';

export type ModalidadEvento = 'PRESENCIAL' | 'VIRTUAL' | 'HIBRIDA';
export type EstadoEvento = 'PROGRAMADO' | 'CONFIRMADO' | 'REPROGRAMADO' | 'REALIZADO' | 'CANCELADO';
export type OrigenInformacion = 'MANUAL' | 'DESCARGADA' | 'CORREO' | 'CLIENTE' | 'IA';

export interface EventoAgenda {
  uuid: string;
  tipo: TipoEventoAgenda;
  titulo: string;
  descripcion: string | null;
  fechaInicio: string;
  fechaFin: string;
  todoElDia: boolean;
  modalidad: ModalidadEvento;
  ubicacion: string | null;
  enlaceVirtual: string | null;
  estado: EstadoEvento;
  resultado: string | null;
  recordatorioMinutos: number | null;
  colorHex: string | null;
  origenInformacion: OrigenInformacion;
  requiereVerificacion: boolean;
  expedienteId: number | null;
  expedienteCodigo: string | null;
  numeroExpediente: string | null;
  clienteNombre: string | null;
  creadorId: number | null;
  creador: string | null;
  participantes: number;
}

export interface ParticipanteAgenda {
  id: number;
  nombre: string;
  cargo: string | null;
  tipoParticipacion: 'ORGANIZADOR' | 'ASISTENTE' | 'OPCIONAL';
  confirmado: boolean;
}

export interface ReprogramacionAgenda {
  id: number;
  fechaInicioAnterior: string;
  fechaFinAnterior: string;
  fechaInicioNueva: string;
  fechaFinNueva: string;
  motivo: string;
  usuario: string | null;
  fechaReprogramacion: string;
}

export interface EventoAgendaDetalle {
  evento: EventoAgenda;
  participantes: ParticipanteAgenda[];
  reprogramaciones: ReprogramacionAgenda[];
}

export interface AgendaResumen {
  eventosHoy: number;
  proximosSieteDias: number;
  audienciasMes: number;
  pendientesVerificacion: number;
  totalMes: number;
}

export interface AgendaExpedienteOpcion {
  id: number;
  codigo: string;
  numeroExpediente: string | null;
  clienteNombre: string;
}

export interface AgendaUsuarioOpcion {
  id: number;
  nombre: string;
  cargo: string | null;
}

export interface AgendaCatalogos {
  expedientes: AgendaExpedienteOpcion[];
  usuarios: AgendaUsuarioOpcion[];
}

export interface EventoAgendaGuardar {
  expedienteId: number | null;
  tipo: TipoEventoAgenda;
  titulo: string;
  descripcion: string;
  fechaInicio: string;
  fechaFin: string;
  todoElDia: boolean;
  modalidad: ModalidadEvento;
  ubicacion: string;
  enlaceVirtual: string;
  recordatorioMinutos: number | null;
  colorHex: string;
  origenInformacion: OrigenInformacion;
  requiereVerificacion: boolean;
  participanteIds: number[];
}

export interface EventoAgendaActualizar extends EventoAgendaGuardar {
  estado: EstadoEvento;
  resultado: string;
}

export interface AgendaFiltro {
  desde: string;
  hasta: string;
  texto?: string;
  tipo?: string;
  estado?: string;
  expedienteId?: number | null;
  participanteId?: number | null;
}
