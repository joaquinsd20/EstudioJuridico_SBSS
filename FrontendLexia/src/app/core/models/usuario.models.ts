/** Contrato de /api/v1/usuarios. Refleja UsuarioDtos.java. */

export interface UsuarioLista {
  uuidPublico: string;
  codigoUsuario: string | null;
  nombreCompleto: string;
  email: string;
  cargo: string | null;
  estado: 'ACTIVO' | 'INACTIVO' | 'BLOQUEADO';
  activo: boolean;
  debeCambiarPassword: boolean;
  ultimoAcceso: string | null;
  roles: string[];
}

/**
 * De que es responsable la persona. Las tres primeras cifras bloquean la baja
 * en el backend: son compromisos vivos que quedarian sin nadie que los mire.
 */
export interface CargaTrabajo {
  expedientes: number;
  plazosAbiertos: number;
  tareasAbiertas: number;
  clientes: number;
  equiposApoyo: number;
  eventosAgenda: number;
  versionesDocumento: number;
}

// ---- Reasignacion de trabajo ----------------------------------------------

export interface CandidatoReasignacion {
  uuidPublico: string;
  nombreCompleto: string;
  cargo: string | null;
  /** Puede llevar expedientes y clientes. */
  esAbogado: boolean;
}

export interface CargaDetallada {
  expedientes: { uuidPublico: string; codigo: string; cliente: string; confidencial: boolean }[];
  plazos: { descripcion: string; vencimiento: string; estado: string; expedienteCodigo: string }[];
  tareas: { titulo: string; estado: string; fechaLimite: string | null; expedienteCodigo: string }[];
  clientes: { uuidPublico: string; nombre: string }[];
  equipos: { expedienteCodigo: string; rol: string }[];
  candidatos: CandidatoReasignacion[];
}

/** Un destino por categoria. Los plazos siguen al expediente; el apoyo se retira. */
export interface ReasignarRequest {
  expedientesA: string | null;
  tareasA: string | null;
  clientesA: string | null;
}

export interface ResultadoReasignacion {
  expedientes: number;
  plazos: number;
  alertas: number;
  tareas: number;
  clientes: number;
  equiposRetirados: number;
}

export interface UsuarioDetalle {
  uuidPublico: string;
  codigoUsuario: string | null;
  nombres: string;
  apellidos: string;
  email: string;
  cargo: string | null;
  telefono: string | null;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  numeroColegiatura: string | null;
  estado: 'ACTIVO' | 'INACTIVO' | 'BLOQUEADO';
  activo: boolean;
  debeCambiarPassword: boolean;
  ultimoAcceso: string | null;
  fechaCreacion: string | null;
  roles: string[];
  cargaTrabajo: CargaTrabajo;
}

export interface RolOpcion {
  codigo: string;
  nombre: string;
  nivelAcceso: number;
  esPortalCliente: boolean;
}

export interface GuardarUsuario {
  nombres: string;
  apellidos: string;
  email: string;
  cargo: string;
  telefono: string;
  tipoDocumento: string;
  numeroDocumento: string;
  numeroColegiatura: string;
  roles: string[];
}

/** Se recibe UNA vez, al crear o restablecer. No se guarda en ninguna parte. */
export interface CredencialTemporal {
  uuidPublico: string;
  email: string;
  passwordTemporal: string;
  mensaje: string;
}

export type FiltroEstadoUsuario = '' | 'ACTIVOS' | 'INACTIVOS' | 'BLOQUEADOS';
