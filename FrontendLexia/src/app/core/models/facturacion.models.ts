export interface HonorarioLista {

  uuid: string;

  clienteId: number;
  expedienteId: number;

  clienteNombre: string;
  clienteDocumento: string;

  expedienteNumero: string;

  concepto: string;

  modalidadCobro: string;

  montoTotal: number;
  montoPagado: number;
  montoPendiente: number;

  moneda: string;

  estadoCodigo: string;
  estadoNombre: string;
  estadoColor: string;

  fechaEmision: string | null;
  fechaVencimiento: string | null;
}

export interface ResumenBackend {

  totalFacturado: number;
  totalCobrado: number;
  totalPendiente: number;
  facturasPendientes: number;
  totalVencido: number;
  cuentasVencidas: number;
}

export interface ResumenFacturacion {

  totalPactado: number;
  totalCobrado: number;
  saldosPendientes: number;
  totalVencido: number;
  cuentasVencidas: number;
}

export interface Opcion {

  id: number;
  nombre: string;
  detalle?: string;

  clienteId?: number | null;
}

export interface CatalogosFacturacion {

  clientes: Opcion[];
  expedientes: Opcion[];
  modalidadesCobro: Opcion[];
  estados: Opcion[];
}

export interface RegistroMovimientoDTO {

  clienteId: number | null;

  expedienteId: number | null;

  tipoOperacion: string;

  modalidad: string;

  monto: number | null;

  fecha: string;

  fechaVencimiento: string | null;

  etapaProcesal: string;

  concepto: string;

  observaciones: string;

  honorarioUuid: string | null;
}

export interface MovimientoResponse {

  uuid: string;
}

export interface ActualizarHonorarioDTO {

  clienteId: number;

  expedienteId: number;

  modalidad: string;

  montoPactado: number;

  fecha: string;

  fechaVencimiento: string;

  etapaProcesal: string;

  concepto: string;

  observaciones: string;
}

export interface PagoHonorarioDetalle {

  id: number;

  fecha: string | null;

  monto: number;

  concepto: string;

  observaciones: string;
}

export interface DetalleHonorario {

  uuid: string;

  clienteId: number;

  expedienteId: number;

  clienteNombre: string;

  clienteDocumento: string;

  expedienteNumero: string;

  concepto: string;

  modalidadCobro: string;

  etapaProcesal: string;

  observaciones: string;

  montoPactado: number;

  montoPagado: number;

  saldoPendiente: number;

  moneda: string;

  estadoCodigo: string;

  estadoNombre: string;

  fechaEmision: string | null;

  fechaVencimiento: string | null;

  vencido: boolean;

  puedeEmitirComprobante: boolean;

  pagos: PagoHonorarioDetalle[];
}
