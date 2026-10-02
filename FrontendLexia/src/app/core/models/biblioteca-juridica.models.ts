export interface RecursoJuridico {
  id: number;
  titulo: string;
  subtituloDescripcion: string;
  tipo: string;
  categoria: string;
  anio: number;
  expedienteCodigo: string;
  nombreArchivo: string;
  extension?: string;
  estado: 'ACTIVO' | 'ARCHIVADO';
  fechaCreacion: string;
}

/** Formatos que ya admite el listado del componente. */
export type RecursosJuridicosResponse = RecursoJuridico[] | { content: RecursoJuridico[] };
