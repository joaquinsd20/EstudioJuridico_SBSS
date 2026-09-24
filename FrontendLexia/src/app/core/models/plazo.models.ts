export type EstadoPlazo =
  | 'PENDIENTE_CALCULO'
  | 'CALCULADO'
  | 'EN_PREPARACION'
  | 'EN_REVISION'
  | 'LISTO_PRESENTAR'
  | 'PRESENTADO'
  | 'CUMPLIDO'
  | 'VENCIDO'
  | 'SUSPENDIDO'
  | 'CANCELADO';

export type RiesgoPlazo = 'BAJO' | 'MEDIO' | 'ALTO' | 'CRITICO';
export type TipoComputoPlazo = 'HABILES' | 'CALENDARIO';
export type OrigenPlazo = 'MANUAL' | 'RESOLUCION' | 'NOTIFICACION' | 'ACTUACION' | 'MONITOR_JUDICIAL';

export interface Plazo {
  uuid: string;
  origen: OrigenPlazo;
  expedienteId: number;
  expedienteCodigo: string;
  numeroExpediente: string | null;
  clienteNombre: string;
  materia: string | null;
  organo: string | null;
  distritoJudicial: string | null;
  tipoPlazoId: number;
  tipoCodigo: string;
  tipoNombre: string;
  descripcion: string;
  fechaNotificacion: string;
  fechaInicioComputo: string;
  diasPlazo: number;
  tipoComputo: TipoComputoPlazo;
  fechaVencimiento: string;
  fechaRevisionInterna: string | null;
  diasRestantes: number;
  responsableId: number;
  responsable: string;
  revisorId: number | null;
  revisor: string | null;
  estado: EstadoPlazo;
  nivelRiesgo: RiesgoPlazo;
  requiereRevisionProfesional: boolean;
  motivoRevision: string | null;
  actuacionRequerida: string | null;
  resolucionId: number | null;
  resolucionNumero: string | null;
  actuacionOrigenId: number | null;
  actuacionAsunto: string | null;
  documentoId: number | null;
  documentoNombre: string | null;
  observaciones: string | null;
  alertas: number;
}

export interface AlertaPlazo {
  id: number;
  tipo: string;
  diasAntes: number | null;
  mensaje: string;
  severidad: string;
  fechaProgramada: string;
  estado: string;
}

export interface PlazoDetalle {
  plazo: Plazo;
  alertas: AlertaPlazo[];
}

export interface PlazoResumen {
  totalActivos: number;
  vencidos: number;
  venceHoy: number;
  proximosSieteDias: number;
  criticos: number;
  revisionProfesional: number;
}

export interface PlazoExpedienteOpcion {
  id: number;
  codigo: string;
  numeroExpediente: string | null;
  clienteNombre: string;
  responsableId: number;
  responsable: string;
  distritoJudicial: string | null;
}

export interface PlazoUsuarioOpcion {
  id: number;
  nombre: string;
  cargo: string | null;
}

export interface TipoPlazoOpcion {
  id: number;
  codigo: string;
  nombre: string;
  diasPorDefecto: number | null;
  tipoComputoDefecto: TipoComputoPlazo;
  nivelRiesgoDefecto: RiesgoPlazo;
}

export interface PlazoCatalogos {
  expedientes: PlazoExpedienteOpcion[];
  usuarios: PlazoUsuarioOpcion[];
  tiposPlazo: TipoPlazoOpcion[];
}

export interface ResolucionPlazoOpcion {
  id: number;
  numero: string;
  tipo: string | null;
  fechaResolucion: string | null;
  fechaNotificacion: string;
  sumilla: string | null;
  origen: string;
  estadoVerificacion: string;
}

export interface ActuacionPlazoOpcion {
  id: number;
  fechaHora: string;
  tipo: string;
  asunto: string;
}

export interface DocumentoPlazoOpcion {
  id: number;
  uuid: string;
  nombre: string;
  categoria: string;
  estado: string;
}

export interface PlazoRelaciones {
  resoluciones: ResolucionPlazoOpcion[];
  actuaciones: ActuacionPlazoOpcion[];
  documentos: DocumentoPlazoOpcion[];
}

export interface CalculoPlazo {
  fechaInicioComputo: string;
  fechaVencimiento: string;
  fechaRevisionInternaSugerida: string;
  distritoJudicial: string | null;
  requiereRevisionSugerida: boolean;
  advertencias: string[];
}

export interface PlazoGuardar {
  expedienteId: number | null;
  actuacionOrigenId: number | null;
  resolucionId: number | null;
  tipoPlazoId: number | null;
  origen: OrigenPlazo;
  descripcion: string;
  fechaNotificacion: string;
  diasPlazo: number | null;
  tipoComputo: TipoComputoPlazo;
  fechaRevisionInterna: string | null;
  responsableId: number | null;
  revisorId: number | null;
  estado: EstadoPlazo;
  nivelRiesgo: RiesgoPlazo;
  requiereRevisionProfesional: boolean;
  motivoRevision: string;
  actuacionRequerida: string;
  documentoId: number | null;
  observaciones: string;
  alertasDias: number[];
}

export interface PlazoFiltro {
  texto?: string;
  expedienteId?: number | null;
  responsableId?: number | null;
  tipoPlazoId?: number | null;
  estado?: string;
  riesgo?: string;
  computo?: string;
  requiereRevision?: boolean | null;
  desde?: string;
  hasta?: string;
}
