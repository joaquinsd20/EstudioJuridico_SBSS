export type TipoPersona = 'NATURAL' | 'JURIDICA' | 'ENTIDAD_PUBLICA';
export type EstadoCliente = 'PROSPECTO' | 'ACTIVO' | 'INACTIVO' | 'ANTIGUO';
export type SituacionFinanciera = 'SOLIDA' | 'ESTABLE' | 'REGULAR' | 'RIESGO' | 'MOROSA';

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  esUltima: boolean;
}

export interface ClienteResumen {
  uuid: string;
  tipoDocumento: string;
  numeroDocumento: string;
  nombre: string;
  tipoPersona: TipoPersona;
  estado: EstadoCliente;
  email: string | null;
  telefonoPrincipal: string | null;
  abogadoResponsable: string | null;
  totalExpedientes: number;
}

export interface ClientesResumen {
  total: number;
  activos: number;
  naturales: number;
  juridicas: number;
  entidadesPublicas: number;
  inactivos: number;
  nuevosEsteMes: number;
  conExpedientes: number;
}

export interface ClienteDetalle {
  uuid: string;
  tipoPersona: TipoPersona;
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string | null;
  apellidoPaterno: string | null;
  apellidoMaterno: string | null;
  razonSocial: string | null;
  nombreComercial: string | null;
  nombre: string;
  email: string | null;
  telefonoPrincipal: string | null;
  telefonoSecundario: string | null;
  direccion: string | null;
  distrito: string | null;
  provincia: string | null;
  departamento: string | null;
  estado: EstadoCliente;
  origenCaptacion: string | null;
  situacionFinanciera: SituacionFinanciera | null;
  abogadoResponsable: string | null;
  abogadoResponsableUuid: string | null;
  fechaUltimoContacto: string | null;
  observaciones: string | null;
  fechaCreacion: string;
  totalExpedientes: number;
}

export interface ContactoCliente {
  nombreCompleto: string;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  cargo: string | null;
  email: string | null;
  telefono: string | null;
  representanteLegal: boolean;
  puedeRecibirDocumentos: boolean;
  numeroPoder: string | null;
}

export interface VinculoCliente {
  tipoPersona: TipoPersona;
  nombre: string;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  relacion: string | null;
  observaciones: string | null;
}

export interface ExpedienteCliente {
  uuid: string;
  codigoInterno: string;
  numeroExpediente: string | null;
  materia: string;
  estado: string;
  estadoColor: string | null;
  responsable: string;
  fechaInicio: string;
  ultimaActuacion: string | null;
}

export interface ComunicacionCliente {
  uuid: string;
  tipo: string;
  asunto: string;
  detalle: string | null;
  canal: string | null;
  fecha: string;
  registradoPor: string;
}

export interface FichaCliente {
  cliente: ClienteDetalle;
  contactos: ContactoCliente[];
  vinculados: VinculoCliente[];
  expedientes: ExpedienteCliente[];
  comunicaciones: ComunicacionCliente[];
}

export interface AbogadoOpcion {
  uuid: string;
  nombre: string;
  cargo: string | null;
}

export interface CatalogosCliente { abogados: AbogadoOpcion[]; }

export interface ClienteGuardar {
  tipoPersona: TipoPersona;
  tipoDocumento: string;
  numeroDocumento: string;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  razonSocial: string;
  nombreComercial: string;
  email: string;
  telefonoPrincipal: string;
  telefonoSecundario: string;
  direccion: string;
  distrito: string;
  provincia: string;
  departamento: string;
  estado: EstadoCliente;
  origenCaptacion: string;
  situacionFinanciera: SituacionFinanciera | '';
  abogadoResponsableUuid: string;
  observaciones: string;
  contactos: ContactoCliente[];
  vinculados: VinculoCliente[];
}

export interface ConflictoRelacion { nombre: string; numeroDocumento: string; }
export interface VerificarConflictoRequest {
  nombre: string;
  numeroDocumento: string;
  relacionados: ConflictoRelacion[];
  excluirClienteUuid?: string | null;
}
export interface CoincidenciaConflicto {
  origen: string;
  referenciaUuid: string | null;
  nombre: string;
  documento: string | null;
  relacion: string | null;
  tipoCoincidencia: string;
  terminoBuscado: string;
}
export interface VerificarConflictoResponse {
  posibleConflicto: boolean;
  totalCoincidencias: number;
  coincidencias: CoincidenciaConflicto[];
}

export interface RegistrarComunicacion {
  tipo: 'LLAMADA' | 'EMAIL' | 'WHATSAPP' | 'REUNION' | 'OTRO';
  asunto: string;
  detalle: string;
  canal: string;
  fecha: string | null;
}

/** Datos de un DNI (RENIEC) o RUC (SUNAT) para autocompletar la ficha. */
export interface DatosDocumento {
  tipoDocumento: 'DNI' | 'RUC';
  numeroDocumento: string;
  nombres: string | null;
  apellidoPaterno: string | null;
  apellidoMaterno: string | null;
  razonSocial: string | null;
  direccion: string | null;
  distrito: string | null;
  provincia: string | null;
  departamento: string | null;
  estadoContribuyente: string | null;
  condicionContribuyente: string | null;
}
