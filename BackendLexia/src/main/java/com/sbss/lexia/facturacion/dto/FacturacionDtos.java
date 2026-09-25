package com.sbss.lexia.facturacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class FacturacionDtos {

    private FacturacionDtos() {
    }


    // =========================================================
    // RESUMEN DE FACTURACIÓN
    // =========================================================
    //
    // Utilizado por:
    //
    // GET /api/v1/facturacion/resumen
    //
    // IMPORTANTE:
    //
    // "Vencido" NO es un estado de pago.
    //
    // Una cuenta se considera vencida cuando:
    //
    // - Tiene saldo pendiente.
    // - Tiene fecha de vencimiento.
    // - La fecha de vencimiento es anterior a la fecha actual.
    //
    // De esta manera se conservan los estados:
    //
    // - PENDIENTE
    // - PARCIAL
    // - PAGADO
    //
    // Y adicionalmente una cuenta puede estar vencida.
    //
    // Ejemplos:
    //
    // PENDIENTE + VENCIDO
    // PARCIAL + VENCIDO
    //
    // =========================================================

    public record ResumenFacturacion(

            BigDecimal totalFacturado,

            BigDecimal totalCobrado,

            BigDecimal totalPendiente,

            long facturasPendientes,

            BigDecimal totalVencido,

            long cuentasVencidas

    ) {
    }


    // =========================================================
    // LISTADO DE HONORARIOS
    // =========================================================
    //
    // Utilizado por:
    //
    // GET /api/v1/facturacion
    //
    // Representa cada honorario mostrado en la tabla
    // principal del módulo de Facturación.
    //
    // clienteId y expedienteId también son necesarios para:
    //
    // - Registrar pagos.
    // - Editar honorarios.
    // - Cargar correctamente los datos en el formulario.
    //
    // fechaVencimiento permitirá determinar desde el frontend
    // si una cuenta se encuentra vencida.
    //
    // IMPORTANTE:
    //
    // No se agrega un estado "VENCIDO".
    //
    // El estado de pago continúa siendo:
    //
    // - PENDIENTE
    // - PARCIAL
    // - PAGADO
    //
    // =========================================================

    public record HonorarioLista(

            String uuid,

            Long clienteId,

            Long expedienteId,

            String clienteNombre,

            String clienteDocumento,

            String expedienteNumero,

            String concepto,

            String modalidadCobro,

            BigDecimal montoTotal,

            BigDecimal montoPagado,

            BigDecimal montoPendiente,

            String moneda,

            String estadoCodigo,

            String estadoNombre,

            String estadoColor,

            LocalDate fechaEmision,

            LocalDate fechaVencimiento

    ) {
    }


    // =========================================================
    // PAGO DEL DETALLE DE UN HONORARIO
    // =========================================================
    //
    // Representa cada abono registrado en:
    //
    // movimientos_financieros
    //
    // para un honorario determinado.
    //
    // Se utilizará dentro de DetalleHonorario para mostrar
    // el historial de pagos.
    //
    // El id corresponde al identificador interno del
    // movimiento financiero.
    //
    // =========================================================

    public record PagoHonorarioDetalle(

            Long id,

            LocalDate fecha,

            BigDecimal monto,

            String concepto,

            String observaciones

    ) {
    }


    // =========================================================
    // DETALLE DE HONORARIO
    // =========================================================
    //
    // Utilizado por:
    //
    // GET /api/v1/facturacion/honorarios/{uuid}/detalle
    //
    // Contiene la información necesaria para el modal:
    //
    // "Ver detalle"
    //
    // También contiene el historial de pagos asociados al
    // honorario.
    //
    // IMPORTANTE:
    //
    // puedeEmitirComprobante solamente será verdadero cuando
    // el honorario se encuentre completamente PAGADO.
    //
    // vencido es una condición independiente del estado de
    // pago y no reemplaza a:
    //
    // - PENDIENTE
    // - PARCIAL
    // - PAGADO
    //
    // =========================================================

    public record DetalleHonorario(

            String uuid,

            Long clienteId,

            Long expedienteId,

            String clienteNombre,

            String clienteDocumento,

            String expedienteNumero,

            String concepto,

            String modalidadCobro,

            String etapaProcesal,

            String observaciones,

            BigDecimal montoPactado,

            BigDecimal montoPagado,

            BigDecimal saldoPendiente,

            String moneda,

            String estadoCodigo,

            String estadoNombre,

            LocalDate fechaEmision,

            LocalDate fechaVencimiento,

            boolean vencido,

            boolean puedeEmitirComprobante,

            List<PagoHonorarioDetalle> pagos

    ) {
    }


    // =========================================================
    // CREAR HONORARIO
    // =========================================================
    //
    // Utilizado por:
    //
    // POST /api/v1/facturacion/honorarios
    //
    // Se mantiene para conservar compatibilidad con el
    // endpoint que ya existe en el proyecto.
    //
    // La fecha de vencimiento será obligatoria cuando se
    // registre directamente un honorario mediante este
    // endpoint.
    //
    // =========================================================

    public record GuardarHonorarioRequest(

            @NotNull
            Long clienteId,

            @NotNull
            Long expedienteId,

            @NotNull
            Long modalidadCobroId,

            @NotNull
            @Size(max = 255)
            String concepto,

            @NotNull
            @DecimalMin("0.01")
            BigDecimal montoTotal,

            @Size(min = 3, max = 3)
            String moneda,

            @NotNull
            LocalDate fechaEmision,

            @NotNull
            LocalDate fechaVencimiento

    ) {
    }


    // =========================================================
    // ACTUALIZAR HONORARIO
    // =========================================================
    //
    // Utilizado por:
    //
    // PUT /api/v1/facturacion/honorarios/{uuid}
    //
    // Permite implementar el botón "Editar" del frontend.
    //
    // El UUID NO se recibe aquí porque llega mediante
    // @PathVariable en el Controller.
    //
    // También recibe fechaVencimiento para permitir modificar
    // la fecha límite de pago de un honorario.
    //
    // =========================================================

    public record ActualizarHonorarioRequest(

            @NotNull
            Long clienteId,

            @NotNull
            Long expedienteId,

            @NotBlank
            String modalidad,

            @NotNull
            @DecimalMin("0.01")
            BigDecimal montoPactado,

            @NotNull
            LocalDate fecha,

            @NotNull
            LocalDate fechaVencimiento,

            String etapaProcesal,

            @Size(max = 255)
            String concepto,

            String observaciones

    ) {
    }


    // =========================================================
    // REGISTRAR MOVIMIENTO FINANCIERO
    // =========================================================
    //
    // Utilizado por:
    //
    // POST /api/v1/facturacion/movimientos
    //
    // Puede representar:
    //
    // - Honorario Pactado
    // - Abono / Pago de Cuota
    // - Gasto Reembolsable
    //
    // honorarioUuid:
    //
    // - null cuando se crea un honorario nuevo.
    // - contiene UUID cuando el movimiento pertenece a un
    //   honorario existente.
    //
    // fechaVencimiento:
    //
    // - Se utiliza cuando tipoOperacion es "Honorario Pactado".
    // - No es necesaria para registrar un pago.
    //
    // Por ese motivo NO lleva @NotNull aquí.
    //
    // =========================================================

    public record RegistrarMovimientoRequest(

            @NotNull
            Long clienteId,

            @NotNull
            Long expedienteId,

            @NotBlank
            String tipoOperacion,

            @NotBlank
            String modalidad,

            @NotNull
            @DecimalMin("0.01")
            BigDecimal monto,

            @NotNull
            LocalDate fecha,

            LocalDate fechaVencimiento,

            String etapaProcesal,

            @Size(max = 255)
            String concepto,

            String observaciones,

            String honorarioUuid

    ) {
    }


    // =========================================================
    // REGISTRAR PAGO
    // =========================================================
    //
    // Utilizado por:
    //
    // POST /api/v1/facturacion/honorarios/{uuid}/pagos
    //
    // Este DTO se conserva para mantener compatible el
    // endpoint de pagos que ya existe.
    //
    // =========================================================

    public record RegistrarPagoRequest(

            @NotNull
            @DecimalMin("0.01")
            BigDecimal monto,

            @NotBlank
            String metodoPago,

            String numeroOperacion,

            @NotNull
            LocalDateTime fechaPago,

            String observacion

    ) {
    }


    // =========================================================
    // OPCIÓN GENERAL DE CATÁLOGO
    // =========================================================
    //
    // Utilizada para:
    //
    // - Clientes
    // - Modalidades de cobro
    // - Estados
    //
    // No se utilizará para expedientes porque necesitamos
    // conocer también el cliente propietario del expediente.
    //
    // =========================================================

    public record Opcion(

            Long id,

            String nombre,

            String detalle

    ) {
    }


    // =========================================================
    // OPCIÓN DE EXPEDIENTE
    // =========================================================
    //
    // clienteId permite que Angular pueda mostrar únicamente
    // los expedientes correspondientes al cliente seleccionado.
    //
    // Ejemplo:
    //
    // Cliente A
    //   -> Expediente 001
    //   -> Expediente 002
    //
    // Cliente B
    //   -> Expediente 003
    //
    // =========================================================

    public record ExpedienteOpcion(

            Long id,

            String nombre,

            String detalle,

            Long clienteId

    ) {
    }


    // =========================================================
    // CATÁLOGOS DE FACTURACIÓN
    // =========================================================
    //
    // Utilizado por:
    //
    // GET /api/v1/facturacion/catalogos
    //
    // expedientes utiliza ExpedienteOpcion para incluir
    // clienteId.
    //
    // Los estados continúan siendo únicamente estados de pago:
    //
    // - PENDIENTE
    // - PARCIAL
    // - PAGADO
    //
    // VENCIDO no se agrega al catálogo de estados porque se
    // calcula utilizando fechaVencimiento y saldo pendiente.
    //
    // =========================================================

    public record CatalogosFacturacion(

            List<Opcion> clientes,

            List<ExpedienteOpcion> expedientes,

            List<Opcion> modalidadesCobro,

            List<Opcion> estados

    ) {
    }
}