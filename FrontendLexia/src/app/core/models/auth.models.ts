/**
 * Contrato con el backend. Estas interfaces reflejan exactamente los records de
 * `com.sbss.lexia.seguridad.dto`: si el backend cambia un campo, aqui tiene que
 * cambiar tambien.
 */

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UsuarioAutenticado {
  /** Identificador publico. El backend nunca expone el id numerico. */
  uuid: string;
  nombreCompleto: string;
  email: string;
  cargo: string | null;
  fotoUrl: string | null;
  debeCambiarPassword: boolean;
  /** Codigos inmutables de rol. Se decide con estos, no con la etiqueta visible. */
  roles: string[];
  /** Etiqueta del rol de mayor nivel. Es lo que muestra el sidebar. */
  rolPrincipal: string | null;
  nivelAcceso: number;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  expiraEnSegundos: number;
  usuario: UsuarioAutenticado;
}

/** Formato unico de error de la API. */
export interface ErrorRespuesta {
  timestamp: string;
  estado: number;
  codigo: string;
  mensaje: string;
  detalles: string[];
}
