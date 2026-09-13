export type CategoriaDocumento =
  | 'DEMANDA'
  | 'CONTESTACION'
  | 'RECURSO'
  | 'ESCRITO'
  | 'RESOLUCION'
  | 'NOTIFICACION'
  | 'CARGO'
  | 'CONTRATO'
  | 'PODER'
  | 'PRUEBA'
  | 'INFORME'
  | 'MULTIMEDIA'
  | 'CORREO'
  | 'CLIENTE'
  | 'OTRO';

export type EstadoDocumento =
  | 'BORRADOR'
  | 'EN_REVISION'
  | 'OBSERVADO'
  | 'APROBADO'
  | 'FIRMADO'
  | 'PRESENTADO'
  | 'CERRADO';

export interface Documento {
  uuid: string;
  nombre: string;
  descripcion: string | null;
  categoria: CategoriaDocumento;
  estado: EstadoDocumento;
  confidencial: boolean;
  enPapelera: boolean;
  fechaPapelera: string | null;
  fechaCreacion: string;
  fechaModificacion: string | null;
  expedienteId: number;
  actuacionId: number | null;
  tareaId: number | null;
  expedienteCodigo: string;
  numeroExpediente: string | null;
  clienteId: number;
  clienteNombre: string;
  creadoPor: string | null;
  responsable: string | null;
  nombreArchivo: string | null;
  extension: string | null;
  mimeType: string | null;
  tamanoBytes: number | null;
  numeroVersion: number | null;
}

export interface DocumentoVersion {
  id: number;
  numeroVersion: number;
  nombreArchivo: string;
  extension: string;
  mimeType: string | null;
  tamanoBytes: number;
  hashSha256: string;
  cargadoPor: string;
  fechaCarga: string;
  observaciones: string | null;
  vigente: boolean;
}

export interface DocumentoEstadisticas {
  total: number;
  recientes: number;
  confidenciales: number;
  papelera: number;
}

export interface DocumentoContenido {
  uuid: string;
  nombre: string;
  extensionOrigen: string;
  html: string;
  editable: boolean;
}

export interface ExpedienteOpcion {
  id: number;
  codigo: string;
  numeroExpediente: string | null;
  clienteId: number;
  clienteNombre: string;
}

export interface UsuarioOpcion {
  id: number;
  nombre: string;
}

export interface RelacionOpcion {
  id: number;
  texto: string;
}

export interface DocumentoCrear {
  expedienteId: number | null;
  actuacionId: number | null;
  tareaId: number | null;
  nombre: string;
  descripcion: string;
  categoria: CategoriaDocumento;
  estado: EstadoDocumento;
  esConfidencial: boolean;
  observaciones: string;
}

export interface DocumentoActualizar {
  expedienteId: number;
  actuacionId: number | null;
  tareaId: number | null;
  nombre: string;
  descripcion: string;
  categoria: CategoriaDocumento;
  estado: EstadoDocumento;
  esConfidencial: boolean;
}

export interface DocumentoFiltro {
  texto?: string;
  categoria?: string;
  estado?: string;
  expedienteId?: number | null;
  clienteId?: number | null;
  creadorId?: number | null;
  extension?: string;
  confidencial?: boolean | null;
  fechaDesde?: string;
  fechaHasta?: string;
}
