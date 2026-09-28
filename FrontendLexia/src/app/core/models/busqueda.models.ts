/**
 * Contrato con `/api/v1/busqueda` (`com.sbss.lexia.busqueda.dto.BusquedaDtos`).
 *
 * El backend devuelve SIETE grupos, no ocho. La octava categoria que pidio el
 * usuario, el DNI/RUC, no es un tipo de resultado propio: es una de las columnas
 * por las que se busca dentro de clientes, contrapartes y abogados. Por eso
 * buscar un RUC devuelve fichas de esos tres tipos y no una cuarta seccion.
 */

/** Tipos que se pueden pedir de uno en uno con `/busqueda/tipo`. */
export type TipoBusqueda =
  | 'EXPEDIENTE'
  | 'CLIENTE'
  | 'CONTRAPARTE'
  | 'RESOLUCION'
  | 'ACTUACION'
  | 'DOCUMENTO'
  | 'ABOGADO';

export const TIPOS_BUSQUEDA: { codigo: TipoBusqueda; etiqueta: string }[] = [
  { codigo: 'EXPEDIENTE', etiqueta: 'Expediente' },
  { codigo: 'CLIENTE', etiqueta: 'Cliente' },
  { codigo: 'CONTRAPARTE', etiqueta: 'Contraparte' },
  { codigo: 'RESOLUCION', etiqueta: 'Resolución' },
  { codigo: 'ACTUACION', etiqueta: 'Actuación' },
  { codigo: 'DOCUMENTO', etiqueta: 'Documento' },
  { codigo: 'ABOGADO', etiqueta: 'Abogado' }
];

/** Cabecera de cada grupo: el tipo y cuantos resultados trae. */
export interface GrupoBusqueda {
  tipo: TipoBusqueda | string;
  etiqueta: string;
  total: number;
  items: ResultadoBusqueda[];
  resultados?: ResultadoBusqueda[];
}

/**
 * Un resultado, con los mismos campos para todos los tipos.
 */
export interface ResultadoBusqueda {
  uuid?: string;
  titulo: string;
  subtitulo?: string | null;
  detalle?: string | null;
  tipo: TipoBusqueda | string;
  etiqueta?: string;
  etiquetaTipo?: string;
  ruta: string | null;
  restringido?: boolean;
  expedienteId?: number;
  expedienteCodigo?: string;
  coincidencia?: string;
  fecha?: string;
}

export interface BusquedaRespuesta {
  texto: string;
  grupos: GrupoBusqueda[];
  total: number;
  parcialPorPermisos?: boolean;
}
