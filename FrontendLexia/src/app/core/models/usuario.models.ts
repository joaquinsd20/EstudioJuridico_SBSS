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
  eventosAgenda: number;
  versionesDocumento: number;
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
