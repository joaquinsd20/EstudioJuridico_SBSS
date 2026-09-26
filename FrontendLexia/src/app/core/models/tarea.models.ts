export type EstadoTarea = 'PENDIENTE'|'EN_PROCESO'|'EN_REVISION'|'OBSERVADA'|'APROBADA'|'PRESENTADA'|'COMPLETADA'|'CANCELADA'|'VENCIDA';
export type PrioridadTarea = 'BAJA'|'MEDIA'|'ALTA'|'URGENTE';
export type EtapaTarea = 'ELABORAR'|'REVISAR'|'CORREGIR'|'APROBAR'|'FIRMAR'|'PRESENTAR'|'CARGAR_CARGO'|'CERRAR';

export interface Tarea {
  id:number; uuid:string; titulo:string; expedienteId:number; expedienteCodigo:string;
  numeroExpediente:string|null; cliente:string; responsableId:number; responsable:string; supervisorId:number|null;
  supervisor:string|null; prioridad:PrioridadTarea; estado:EstadoTarea; etapaFlujo:EtapaTarea; fechaLimite:string;
  porcentajeAvance:number; subtareasTotal:number; subtareasCompletadas:number; adjuntos:number; comentarios:number;
}
export interface TareaInfo extends Tarea {
  descripcion:string|null; fechaAsignacion:string; fechaInicioReal:string|null; fechaCompletado:string|null;
  tiempoEstimadoMinutos:number|null; tiempoRealMinutos:number|null;
}
export interface TareaExcelFila {
  titulo:string; expedienteCodigo:string; responsable:string; supervisor:string|null; prioridad:PrioridadTarea;
  estado:EstadoTarea; etapaFlujo:EtapaTarea; fechaLimite:string;
}
export interface TareaResumen { pendientes:number; proceso:number; revision:number; vencidas:number; completadas:number; total:number; }
export interface TareaPagina {
  contenido:Tarea[];
  pagina:number;
  tamano:number;
  totalElementos:number;
  totalPaginas:number;
  primera:boolean;
  ultima:boolean;
}

export interface ExpedienteTareaOpcion { id:number; codigo:string; numeroExpediente:string|null; cliente:string; }
export interface UsuarioTareaOpcion { id:number; nombre:string; cargo:string|null; }
export interface TareaCatalogos { expedientes:ExpedienteTareaOpcion[]; usuarios:UsuarioTareaOpcion[]; }
export interface Subtarea { id:number; titulo:string; completada:boolean; orden:number; responsableId:number|null; responsable:string|null; fechaCompletado:string|null; }
export interface ComentarioTarea { id:number; usuario:string; texto:string; fechaCreacion:string; }
export interface AdjuntoTarea { uuid:string; nombre:string; categoria:string; estado:string; fechaCreacion:string; }
export interface HistorialTarea { id:number; usuario:string; estadoAnterior:string|null; estadoNuevo:string; etapaAnterior:string|null; etapaNueva:string; observacion:string|null; fechaCambio:string; }
export interface TareaDetalle { tarea:TareaInfo; subtareas:Subtarea[]; comentarios:ComentarioTarea[]; adjuntos:AdjuntoTarea[]; historial:HistorialTarea[]; }
export interface TareaGuardar { expedienteId:number|null; titulo:string; descripcion:string; responsableId:number|null; supervisorId:number|null; prioridad:PrioridadTarea; fechaLimite:string; tiempoEstimadoMinutos:number|null; subtareas:{titulo:string; responsableId:number|null}[]; }
export interface TareaFiltro { texto?:string; estado?:string; prioridad?:string; responsableId?:number|null; expedienteId?:number|null; desde?:string; hasta?:string; }
