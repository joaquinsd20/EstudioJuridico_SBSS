package com.sbss.lexia.facturacion.service;

import java.util.List;

import com.sbss.lexia.facturacion.dto.FacturacionDtos.ActualizarHonorarioRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.CatalogosFacturacion;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.DetalleHonorario;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.GuardarHonorarioRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.HonorarioLista;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.RegistrarMovimientoRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.RegistrarPagoRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.ResumenFacturacion;

public interface FacturacionService {

    // =========================================================
    // LISTAR HONORARIOS
    // =========================================================

    List<HonorarioLista> listar(
            String cliente,
            String expediente,
            Long modalidadId,
            String estado
    );


    // =========================================================
    // RESUMEN DE FACTURACIÓN
    // =========================================================
    //
    // Devuelve:
    //
    // - Total facturado.
    // - Total cobrado.
    // - Total pendiente.
    // - Cantidad de facturas pendientes.
    // - Total vencido.
    // - Cantidad de cuentas vencidas.
    //
    // La información específica del resumen se encuentra
    // definida en ResumenFacturacion.
    //
    // =========================================================

    ResumenFacturacion resumen();


    // =========================================================
    // CATÁLOGOS
    // =========================================================

    CatalogosFacturacion catalogos();


    // =========================================================
    // CREAR HONORARIO
    // =========================================================

    String crearHonorario(
            GuardarHonorarioRequest request
    );


    // =========================================================
    // ACTUALIZAR HONORARIO
    // =========================================================
    //
    // Utilizado por el botón "Editar" del frontend.
    //
    // El UUID identifica el honorario que se modificará.
    //
    // ActualizarHonorarioRequest también contiene la fecha
    // de vencimiento del honorario.
    //
    // =========================================================

    void actualizarHonorario(
            String honorarioUuid,
            ActualizarHonorarioRequest request
    );


    // =========================================================
    // OBTENER DETALLE DE HONORARIO
    // =========================================================
    //
    // Utilizado por:
    //
    // GET /api/v1/facturacion/honorarios/{uuid}/detalle
    //
    // Devuelve toda la información necesaria para el modal
    // "Ver detalle" del frontend.
    //
    // Incluye:
    //
    // - Cliente.
    // - Documento.
    // - Expediente.
    // - Concepto.
    // - Modalidad.
    // - Etapa procesal.
    // - Observaciones.
    // - Fecha de emisión.
    // - Fecha de vencimiento.
    // - Monto pactado.
    // - Monto pagado.
    // - Saldo pendiente.
    // - Estado de pago.
    // - Condición de vencido.
    // - Posibilidad de emitir comprobante.
    // - Historial de pagos.
    //
    // =========================================================

    DetalleHonorario obtenerDetalleHonorario(
            String honorarioUuid
    );


    // =========================================================
    // GENERAR COMPROBANTE PDF
    // =========================================================
    //
    // Utilizado por:
    //
    // GET /api/v1/facturacion/honorarios/{uuid}/comprobante
    //
    // Genera una constancia/comprobante de pago en formato PDF.
    //
    // IMPORTANTE:
    //
    // La implementación del Service debe volver a validar
    // el estado real del honorario.
    //
    // El comprobante únicamente podrá generarse cuando:
    //
    // - estado_pago sea PAGADO.
    // - saldo pendiente sea cero.
    //
    // Por lo tanto, la seguridad no dependerá únicamente de
    // que Angular muestre u oculte el botón.
    //
    // El byte[] retornado será enviado por el Controller con
    // Content-Type application/pdf.
    //
    // =========================================================

    byte[] generarComprobante(
            String honorarioUuid
    );


    // =========================================================
    // REGISTRAR MOVIMIENTO FINANCIERO
    // =========================================================
    //
    // Cuando se registra un "Honorario Pactado",
    // RegistrarMovimientoRequest puede incluir su fecha
    // de vencimiento.
    //
    // =========================================================

    String registrarMovimiento(
            RegistrarMovimientoRequest request
    );


    // =========================================================
    // REGISTRAR PAGO
    // =========================================================

    String registrarPago(
            String honorarioUuid,
            RegistrarPagoRequest request
    );
}