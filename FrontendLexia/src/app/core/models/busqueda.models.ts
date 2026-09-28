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
  tipo: TipoBusqueda;
  etiqueta: string;
  total: number;
  resultados: ResultadoBusqueda[];
}

/**
 * Un resultado, con los mismos campos para todos los tipos.
 *
 * Se opta por un unico tipo y no por siete porque el buscador solo muestra
 * cuatro cosas de cada fila: titulo, un subtitulo, una etiqueta y a donde
 * lleva. Modelar cada tipo por separado obligaria a un `@switch` en la
 * plantilla por cada uno, y cada uno anadiria una excepcion.
 */
export interface ResultadoBusqueda {
  /** Identificador publico, el que acepta el modulo de destino. */
  uuid: string;
  /** Linea principal. */
  titulo: string;
  /** Linea secundaria, con el contexto que hace distinguir dos homonimos. */
  subtitulo: string | null;
  /** Etiqueta corta del tipo, para el chip de color. */
  tipo: TipoBusqueda;
  etiquetaTipo: string;
  /** Destino dentro de la SPA. Viene del backend como `urlDestino`. */
  ruta: string | null;
  /** Marca los resultados que el usuario no tiene permiso a abrir. */
  restringido: boolean;
}

export interface BusquedaRespuesta {
  texto: string;
  grupos: GrupoBusqueda[];
  /** Suma de resultados de todos los grupos. */
  total: number;
  /** true si algun grupo se quedo sin resultados visibles por permisos. */
  parcialPorPermisos: boolean;
}
