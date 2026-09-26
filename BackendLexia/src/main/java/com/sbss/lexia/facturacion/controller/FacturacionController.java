package com.sbss.lexia.facturacion.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sbss.lexia.facturacion.dto.FacturacionDtos.ActualizarHonorarioRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.CatalogosFacturacion;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.DetalleHonorario;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.GuardarHonorarioRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.HonorarioLista;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.RegistrarMovimientoRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.RegistrarPagoRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.ResumenFacturacion;
import com.sbss.lexia.facturacion.service.FacturacionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1/facturacion")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class FacturacionController {

    private final FacturacionService service;


    // =========================================================
    // LISTAR HONORARIOS
    // =========================================================
    //
    // GET /api/v1/facturacion
    //
    // Permite obtener todos los honorarios registrados.
    //
    // También permite aplicar filtros por:
    //
    // - Cliente
    // - Expediente
    // - Modalidad
    // - Estado de pago
    //
    // Los estados de pago continúan siendo:
    //
    // - PENDIENTE
    // - PARCIAL
    // - PAGADO
    //
    // IMPORTANTE:
    //
    // VENCIDO no se maneja como un estado de pago.
    // Se determina utilizando:
    //
    // - fechaVencimiento
    // - montoPendiente
    //
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAuthority('FACTURACION_LEER')")
    public List<HonorarioLista> listar(
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String expediente,
            @RequestParam(required = false) Long modalidadId,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "250") int limite,
            @RequestParam(defaultValue = "0") int desplazamiento) {

        return service.listar(
                cliente,
                expediente,
                modalidadId,
                estado,
                limite,
                desplazamiento
        );
    }


    // =========================================================
    // RESUMEN DE FACTURACIÓN
    // =========================================================
    //
    // GET /api/v1/facturacion/resumen
    //
    // Devuelve:
    //
    // - Total facturado
    // - Total cobrado
    // - Total pendiente
    // - Cantidad de facturas pendientes
    // - Total vencido
    // - Cantidad de cuentas vencidas
    //
    // =========================================================

    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('FACTURACION_LEER')")
    public ResumenFacturacion resumen() {

        return service.resumen();
    }


    // =========================================================
    // CATÁLOGOS
    // =========================================================
    //
    // GET /api/v1/facturacion/catalogos
    //
    // Devuelve:
    //
    // - Clientes
    // - Expedientes
    // - Modalidades de cobro
    // - Estados de pago
    //
    // =========================================================

    @GetMapping("/catalogos")
    @PreAuthorize("hasAuthority('FACTURACION_LEER')")
    public CatalogosFacturacion catalogos() {

        return service.catalogos();
    }


    // =========================================================
    // CREAR HONORARIO
    // =========================================================
    //
    // POST /api/v1/facturacion/honorarios
    //
    // =========================================================

    @PostMapping("/honorarios")
    @PreAuthorize("hasAuthority('FACTURACION_REGISTRAR')")
    public ResponseEntity<Map<String, String>> crearHonorario(
            @Valid @RequestBody GuardarHonorarioRequest request) {

        String uuid = service.crearHonorario(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/v1/facturacion/honorarios/" + uuid
                        )
                )
                .body(
                        Map.of(
                                "uuid",
                                uuid
                        )
                );
    }


    // =========================================================
    // ACTUALIZAR HONORARIO
    // =========================================================
    //
    // PUT /api/v1/facturacion/honorarios/{uuid}
    //
    // =========================================================

    @PutMapping("/honorarios/{uuid}")
    @PreAuthorize("hasAuthority('FACTURACION_EDITAR')")
    public ResponseEntity<Map<String, String>> actualizarHonorario(
            @PathVariable String uuid,
            @Valid @RequestBody ActualizarHonorarioRequest request) {

        service.actualizarHonorario(
                uuid,
                request
        );

        return ResponseEntity.ok(
                Map.of(
                        "uuid",
                        uuid,
                        "mensaje",
                        "Honorario actualizado correctamente."
                )
        );
    }


    // =========================================================
    // VER DETALLE DEL HONORARIO
    // =========================================================
    //
    // GET /api/v1/facturacion/honorarios/{uuid}/detalle
    //
    // Devuelve la información completa del honorario:
    //
    // - Cliente
    // - Documento
    // - Expediente
    // - Concepto
    // - Modalidad
    // - Etapa procesal
    // - Observaciones
    // - Monto pactado
    // - Monto pagado
    // - Saldo pendiente
    // - Estado
    // - Fecha de emisión
    // - Fecha de vencimiento
    // - Condición de vencido
    // - Historial de pagos
    // - Permiso para emitir comprobante
    //
    // =========================================================

    @GetMapping("/honorarios/{uuid}/detalle")
    @PreAuthorize("hasAuthority('FACTURACION_LEER')")
    public ResponseEntity<DetalleHonorario> obtenerDetalleHonorario(
            @PathVariable String uuid) {

        DetalleHonorario detalle =
                service.obtenerDetalleHonorario(uuid);

        return ResponseEntity.ok(detalle);
    }


    // =========================================================
    // EMITIR COMPROBANTE DE PAGO
    // =========================================================
    //
    // GET /api/v1/facturacion/honorarios/{uuid}/comprobante
    //
    // El Service valida que el honorario se encuentre
    // completamente PAGADO antes de generar el PDF.
    //
    // Si está pendiente o parcialmente pagado,
    // el comprobante no debe generarse.
    //
    // =========================================================

    @GetMapping(
            value = "/honorarios/{uuid}/comprobante",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    @PreAuthorize("hasAuthority('FACTURACION_EMITIR')")
    public ResponseEntity<byte[]> emitirComprobante(
            @PathVariable String uuid) {

        byte[] pdf =
                service.generarComprobante(uuid);

        String nombreArchivo =
                "comprobante-pago-"
                        + limpiarUuidParaArchivo(uuid)
                        + ".pdf";

        ContentDisposition contentDisposition =
                ContentDisposition
                        .attachment()
                        .filename(nombreArchivo)
                        .build();

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .body(pdf);
    }


    // =========================================================
    // REGISTRAR MOVIMIENTO FINANCIERO
    // =========================================================
    //
    // POST /api/v1/facturacion/movimientos
    //
    // =========================================================

    @PostMapping("/movimientos")
    @PreAuthorize("hasAuthority('FACTURACION_REGISTRAR')")
    public ResponseEntity<Map<String, String>> crearMovimiento(
            @Valid @RequestBody RegistrarMovimientoRequest request) {

        String uuid = service.registrarMovimiento(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/v1/facturacion/movimientos/" + uuid
                        )
                )
                .body(
                        Map.of(
                                "uuid",
                                uuid
                        )
                );
    }


    // =========================================================
    // REGISTRAR PAGO
    // =========================================================
    //
    // POST /api/v1/facturacion/honorarios/{uuid}/pagos
    //
    // =========================================================

    @PostMapping("/honorarios/{uuid}/pagos")
    @PreAuthorize("hasAuthority('FACTURACION_REGISTRAR')")
    public ResponseEntity<Map<String, String>> registrarPago(
            @PathVariable String uuid,
            @Valid @RequestBody RegistrarPagoRequest request) {

        String pagoUuid = service.registrarPago(
                uuid,
                request
        );

        return ResponseEntity.ok(
                Map.of(
                        "uuid",
                        pagoUuid
                )
        );
    }


    // =========================================================
    // UTILIDAD PARA NOMBRE DEL ARCHIVO PDF
    // =========================================================

    private String limpiarUuidParaArchivo(
            String uuid) {

        if (uuid == null || uuid.isBlank()) {

            return "honorario";
        }

        String limpio =
                uuid.replaceAll(
                        "[^a-zA-Z0-9\\-]",
                        ""
                );

        if (limpio.isBlank()) {

            return "honorario";
        }

        return limpio;
    }
}