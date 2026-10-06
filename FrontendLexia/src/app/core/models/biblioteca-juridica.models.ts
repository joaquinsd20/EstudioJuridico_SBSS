export type EstadoRecursoJuridico = 'ACTIVO' | 'ARCHIVADO';

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  esUltima: boolean;
}

export interface RecursoJuridico {
  uuid: string;
  titulo: string;
  subtituloDescripcion: string | null;
  tipo: string;
  categoria: string | null;
  anio: number | null;
  expedienteCodigo: string | null;
  nombreArchivo: string | null;
  extension: string | null;
  tamanioBytes?: number | null;
  estado: EstadoRecursoJuridico;
  fechaCreacion: string;
  fechaModificacion?: string | null;
}

export interface BibliotecaEstadisticas {
  recursosTotales: number;
  recursosActivos: number;
  conArchivo: number;
  totalCategorias: number;
}

export interface FiltrosBiblioteca {
  busqueda: string;
  tipo: string;
  categoria: string;
  estado: string;
  conArchivo: '' | 'true' | 'false';
  anio?: number | null;
}
