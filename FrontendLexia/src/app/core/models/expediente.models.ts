export interface ExpedienteResumen {
  total: number;
  activos: number;
  enTramite: number;
  criticos: number;
}

export interface ExpedienteLista {
  uuid: string;
  codigoInterno: string;
  numeroExpediente: string | null;
  anio: number;
  clienteNombre: string;
  clienteDocumento: string;
  especialidad: string;
  materia: string;
  organo: string;
  distritoJudicial: string | null;
  responsable: string;
  estadoCodigo: string;
  estadoNombre: string;
  estadoColor: string;
  nivelRiesgo: 'BAJO' | 'MEDIO' | 'ALTO' | 'CRITICO';
  cuantia: number | null;
  moneda: string;
  fechaInicio: string;
  fechaUltimaActuacion: string | null;
  confidencial: boolean;
}

export interface PersonaRelacionada {
  id: number;
  nombre: string;
  detalle: string | null;
}

export interface ExpedienteEquipo {
  usuarioId: number;
  nombre: string;
  rol: string;
  puedeEditar: boolean;
}

export interface ExpedienteDetalle {
  uuid: string;
  codigoInterno: string;
  numeroExpediente: string | null;
  numeroCorrelativo: string | null;
  anio: number;
  clienteId: number;
  clienteNombre: string;
  clienteDocumento: string;
  especialidadId: number;
  especialidad: string;
  materiaId: number | null;
  materia: string | null;
  organoId: number | null;
  organo: string | null;
  distritoJudicial: string | null;
  etapaProcesal: string | null;
  instancia: string | null;
  estadoId: number;
  estadoCodigo: string;
  estadoNombre: string;
  estadoColor: string;
  abogadoResponsableId: number;
  abogadoResponsable: string;
  socioSupervisorId: number | null;
  socioSupervisor: string | null;
  nivelRiesgo: 'BAJO' | 'MEDIO' | 'ALTO' | 'CRITICO';
  cuantia: number | null;
  moneda: string;
  fechaInicio: string;
  fechaIngresoEstudio: string;
  fechaUltimaActuacion: string | null;
  fechaCierre: string | null;
  resultado: string | null;
  confidencial: boolean;
  resumen: string | null;
  contrapartes: PersonaRelacionada[];
  equipo: ExpedienteEquipo[];
}

export interface EventoLineaTiempo {
  clave: string;
  tipo:
    | 'HECHO'
    | 'DOCUMENTO'
    | 'RESOLUCION'
    | 'NOTIFICACION'
    | 'AUDIENCIA'
    | 'TAREA'
    | 'PLAZO'
    | 'LLAMADA'
    | 'REUNION'
    | 'CAMBIO_RESPONSABLE'
    | 'CAMBIO_EQUIPO';
  fechaHora: string;
  usuario: string | null;
  titulo: string;
  descripcion: string | null;
  documentoUuid: string | null;
  plazoUuid: string | null;
  tareaUuid: string | null;
  origen: string;
}

export interface OpcionExpediente {
  id: number;
  nombre: string;
  detalle: string | null;
}

export interface MateriaOpcionExpediente {
  id: number;
  especialidadId: number;
  nombre: string;
}

export interface EstadoOpcionExpediente {
  id: number;
  codigo: string;
  nombre: string;
  color: string;
}

export interface CatalogosExpediente {
  clientes: OpcionExpediente[];
  especialidades: OpcionExpediente[];
  materias: MateriaOpcionExpediente[];
  organos: OpcionExpediente[];
  estados: EstadoOpcionExpediente[];
  usuarios: OpcionExpediente[];
  contrapartes: OpcionExpediente[];
}

export interface ExpedienteGuardar {
  numeroExpediente: string | null;
  numeroCorrelativo: string | null;
  anio: number;
  clienteId: number | null;
  especialidadId: number | null;
  materiaId: number | null;
  organoId: number | null;
  estadoId: number | null;
  etapaProcesal: string | null;
  instancia: string | null;
  abogadoResponsableId: number | null;
  socioSupervisorId: number | null;
  nivelRiesgo: 'BAJO' | 'MEDIO' | 'ALTO' | 'CRITICO';
  cuantia: number | null;
  moneda: string;
  fechaInicio: string;
  fechaIngresoEstudio: string | null;
  fechaCierre: string | null;
  resultado: string | null;
  confidencial: boolean;
  resumen: string | null;
  equipoIds: number[];
  contraparteIds: number[];
}
