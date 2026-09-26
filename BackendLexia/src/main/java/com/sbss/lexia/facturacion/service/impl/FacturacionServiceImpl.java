package com.sbss.lexia.facturacion.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sbss.lexia.facturacion.dto.FacturacionDtos.ActualizarHonorarioRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.CatalogosFacturacion;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.DetalleHonorario;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.GuardarHonorarioRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.HonorarioLista;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.PagoHonorarioDetalle;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.RegistrarMovimientoRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.RegistrarPagoRequest;
import com.sbss.lexia.facturacion.dto.FacturacionDtos.ResumenFacturacion;
import com.sbss.lexia.facturacion.repository.FacturacionRepositoryJdbc;
import com.sbss.lexia.facturacion.service.FacturacionService;
import com.sbss.lexia.shared.security.SesionActual;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacturacionServiceImpl implements FacturacionService {

    private final FacturacionRepositoryJdbc repository;

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Locale LOCALE_PERU =
            Locale.forLanguageTag("es-PE");


    // =========================================================
    // DATOS DE LA SESIÓN ACTUAL
    // =========================================================

    private Integer getEstudioIdActual() {
        return SesionActual.estudioId();
    }

    private Long getUsuarioIdActual() {
        // Devolvia 1L fijo, asi que todo honorario, movimiento y pago quedaba
        // registrado a nombre del usuario 1 hiciera quien lo hiciera. En un
        // modulo de dinero la trazabilidad es el punto.
        return SesionActual.usuarioId();
    }


    // =========================================================
    // LISTAR HONORARIOS
    // =========================================================

    @Override
    public List<HonorarioLista> listar(
            String cliente,
            String expediente,
            Long modalidadId,
            String estado,
            int limite,
            int desplazamiento) {

        return repository.listar(
                getEstudioIdActual(),
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
    // El resumen incluye:
    //
    // - total facturado
    // - total cobrado
    // - total pendiente
    // - cantidad de facturas pendientes
    // - total vencido
    // - cantidad de cuentas vencidas
    //
    // IMPORTANTE:
    //
    // VENCIDO no es un estado de pago.
    //
    // Los estados continúan siendo:
    //
    // - PENDIENTE
    // - PARCIAL
    // - PAGADO
    //
    // =========================================================

    @Override
    public ResumenFacturacion resumen() {

        return repository.resumen(getEstudioIdActual());
    }


    // =========================================================
    // CATÁLOGOS
    // =========================================================

    @Override
    public CatalogosFacturacion catalogos() {

        return repository.catalogos(getEstudioIdActual());
    }


    // =========================================================
    // CREAR HONORARIO
    // =========================================================

    @Override
    @Transactional
    public String crearHonorario(
            GuardarHonorarioRequest request) {

        return repository.crearHonorario(
                getEstudioIdActual(),
                getUsuarioIdActual(),
                request
        );
    }


    // =========================================================
    // ACTUALIZAR HONORARIO
    // =========================================================
    //
    // Este método será utilizado por el botón "Editar"
    // del frontend.
    //
    // El Repository se encargará de:
    //
    // - validar cliente y expediente
    // - comprobar que el honorario exista
    // - proteger datos si ya existen pagos
    // - actualizar honorarios
    // - actualizar fecha de vencimiento
    // - actualizar el movimiento financiero original
    //
    // =========================================================

    @Override
    @Transactional
    public void actualizarHonorario(
            String honorarioUuid,
            ActualizarHonorarioRequest request) {

        repository.actualizarHonorario(
                getEstudioIdActual(),
                getUsuarioIdActual(),
                honorarioUuid,
                request
        );
    }


    // =========================================================
    // OBTENER DETALLE DEL HONORARIO
    // =========================================================
    //
    // Utilizado posteriormente por:
    //
    // GET /api/v1/facturacion/honorarios/{uuid}/detalle
    //
    // El Repository aplica el filtro por estudioId.
    //
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public DetalleHonorario obtenerDetalleHonorario(
            String honorarioUuid) {

        validarUuidHonorario(honorarioUuid);

        return repository.obtenerDetalleHonorario(
                getEstudioIdActual(),
                honorarioUuid
        );
    }


    // =========================================================
    // GENERAR COMPROBANTE PDF
    // =========================================================
    //
    // REGLA:
    //
    // El comprobante final solamente se puede generar cuando:
    //
    // - estadoCodigo = PAGADO
    // - saldoPendiente <= 0
    //
    // La validación se realiza nuevamente en el backend.
    // Por tanto, no depende de que Angular oculte el botón.
    //
    // Este documento es una constancia interna de pago
    // generada por LEXIA.
    //
    // No representa por sí mismo un comprobante tributario
    // electrónico emitido por SUNAT.
    //
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public byte[] generarComprobante(
            String honorarioUuid) {

        validarUuidHonorario(honorarioUuid);

        DetalleHonorario detalle =
                repository.obtenerDetalleHonorario(
                        getEstudioIdActual(),
                        honorarioUuid
                );

        validarEmisionComprobante(detalle);

        try {

            return construirComprobantePdf(detalle);

        } catch (IOException e) {

            log.error(
                    "Error al generar el comprobante PDF del honorario {}: {}",
                    honorarioUuid,
                    e.getMessage(),
                    e
            );

            throw new IllegalStateException(
                    "No se pudo generar el comprobante de pago.",
                    e
            );
        }
    }


    // =========================================================
    // VALIDAR UUID DEL HONORARIO
    // =========================================================

    private void validarUuidHonorario(
            String honorarioUuid) {

        if (honorarioUuid == null
                || honorarioUuid.isBlank()) {

            throw new IllegalArgumentException(
                    "Debe indicar el honorario."
            );
        }
    }


    // =========================================================
    // VALIDAR EMISIÓN DEL COMPROBANTE
    // =========================================================

    private void validarEmisionComprobante(
            DetalleHonorario detalle) {

        if (detalle == null) {

            throw new IllegalArgumentException(
                    "El honorario seleccionado no existe."
            );
        }

        boolean estadoPagado =
                "PAGADO".equalsIgnoreCase(
                        detalle.estadoCodigo()
                );

        BigDecimal saldo =
                detalle.saldoPendiente() != null
                        ? detalle.saldoPendiente()
                        : BigDecimal.ZERO;

        boolean saldoCancelado =
                saldo.compareTo(BigDecimal.ZERO) <= 0;

        if (!estadoPagado || !saldoCancelado) {

            throw new IllegalStateException(
                    "El comprobante solo puede emitirse cuando el honorario se encuentra completamente pagado."
            );
        }
    }


    // =========================================================
    // CONSTRUIR COMPROBANTE PDF
    // =========================================================

    private byte[] construirComprobantePdf(
            DetalleHonorario detalle) throws IOException {

        try (
                PDDocument documento = new PDDocument();
                ByteArrayOutputStream salida =
                        new ByteArrayOutputStream()
        ) {

            PDType1Font fuenteNormal =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA
                    );

            PDType1Font fuenteNegrita =
                    new PDType1Font(
                            Standard14Fonts.FontName.HELVETICA_BOLD
                    );

            PdfContexto contexto =
                    nuevaPagina(
                            documento,
                            fuenteNormal,
                            fuenteNegrita
                    );

            try {

                // =================================================
                // CABECERA
                // =================================================

                escribirTexto(
                        contexto.contenido(),
                        fuenteNegrita,
                        20,
                        50,
                        contexto.y(),
                        "LEXIA"
                );

                contexto.y(contexto.y() - 28);

                escribirTexto(
                        contexto.contenido(),
                        fuenteNegrita,
                        16,
                        50,
                        contexto.y(),
                        "COMPROBANTE DE PAGO"
                );

                contexto.y(contexto.y() - 18);

                escribirTexto(
                        contexto.contenido(),
                        fuenteNormal,
                        9,
                        50,
                        contexto.y(),
                        "Constancia interna generada por el sistema LEXIA"
                );

                contexto.y(contexto.y() - 16);

                escribirTexto(
                        contexto.contenido(),
                        fuenteNormal,
                        9,
                        50,
                        contexto.y(),
                        "Referencia: " + textoSeguro(detalle.uuid())
                );

                contexto.y(contexto.y() - 14);

                escribirTexto(
                        contexto.contenido(),
                        fuenteNormal,
                        9,
                        50,
                        contexto.y(),
                        "Fecha de emisión del comprobante: "
                                + formatearFecha(LocalDate.now())
                );

                contexto.y(contexto.y() - 28);


                // =================================================
                // DATOS DEL CLIENTE
                // =================================================

                contexto = asegurarEspacio(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        130
                );

                escribirTexto(
                        contexto.contenido(),
                        fuenteNegrita,
                        12,
                        50,
                        contexto.y(),
                        "DATOS DEL CLIENTE"
                );

                contexto.y(contexto.y() - 20);

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Cliente",
                        detalle.clienteNombre()
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Documento",
                        detalle.clienteDocumento()
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Expediente",
                        detalle.expedienteNumero()
                );

                contexto.y(contexto.y() - 10);


                // =================================================
                // DATOS DEL HONORARIO
                // =================================================

                contexto = asegurarEspacio(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        220
                );

                escribirTexto(
                        contexto.contenido(),
                        fuenteNegrita,
                        12,
                        50,
                        contexto.y(),
                        "DATOS DEL HONORARIO"
                );

                contexto.y(contexto.y() - 20);

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Concepto",
                        detalle.concepto()
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Modalidad",
                        detalle.modalidadCobro()
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Etapa procesal",
                        detalle.etapaProcesal()
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Fecha de emisión",
                        formatearFecha(
                                detalle.fechaEmision()
                        )
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Fecha de vencimiento",
                        formatearFecha(
                                detalle.fechaVencimiento()
                        )
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Monto pactado",
                        formatearMoneda(
                                detalle.montoPactado(),
                                detalle.moneda()
                        )
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Total pagado",
                        formatearMoneda(
                                detalle.montoPagado(),
                                detalle.moneda()
                        )
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Saldo pendiente",
                        formatearMoneda(
                                detalle.saldoPendiente(),
                                detalle.moneda()
                        )
                );

                contexto = escribirCampo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Estado",
                        detalle.estadoNombre()
                );

                if (detalle.observaciones() != null
                        && !detalle.observaciones().isBlank()) {

                    contexto = escribirCampo(
                            documento,
                            contexto,
                            fuenteNormal,
                            fuenteNegrita,
                            "Observaciones",
                            detalle.observaciones()
                    );
                }

                contexto.y(contexto.y() - 10);


                // =================================================
                // HISTORIAL DE PAGOS
                // =================================================

                contexto = asegurarEspacio(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        100
                );

                escribirTexto(
                        contexto.contenido(),
                        fuenteNegrita,
                        12,
                        50,
                        contexto.y(),
                        "HISTORIAL DE PAGOS"
                );

                contexto.y(contexto.y() - 22);

                List<PagoHonorarioDetalle> pagos =
                        detalle.pagos() != null
                                ? detalle.pagos()
                                : List.of();

                if (pagos.isEmpty()) {

                    escribirTexto(
                            contexto.contenido(),
                            fuenteNormal,
                            9,
                            50,
                            contexto.y(),
                            "No existen pagos registrados."
                    );

                    contexto.y(contexto.y() - 18);

                } else {

                    int numeroPago = 1;

                    for (PagoHonorarioDetalle pago : pagos) {

                        contexto = asegurarEspacio(
                                documento,
                                contexto,
                                fuenteNormal,
                                fuenteNegrita,
                                70
                        );

                        escribirTexto(
                                contexto.contenido(),
                                fuenteNegrita,
                                9,
                                50,
                                contexto.y(),
                                "Pago " + numeroPago
                        );

                        contexto.y(contexto.y() - 15);

                        contexto = escribirCampo(
                                documento,
                                contexto,
                                fuenteNormal,
                                fuenteNegrita,
                                "Fecha",
                                formatearFecha(
                                        pago.fecha()
                                )
                        );

                        contexto = escribirCampo(
                                documento,
                                contexto,
                                fuenteNormal,
                                fuenteNegrita,
                                "Monto",
                                formatearMoneda(
                                        pago.monto(),
                                        detalle.moneda()
                                )
                        );

                        if (pago.concepto() != null
                                && !pago.concepto().isBlank()) {

                            contexto = escribirCampo(
                                    documento,
                                    contexto,
                                    fuenteNormal,
                                    fuenteNegrita,
                                    "Concepto",
                                    pago.concepto()
                            );
                        }

                        if (pago.observaciones() != null
                                && !pago.observaciones().isBlank()) {

                            contexto = escribirCampo(
                                    documento,
                                    contexto,
                                    fuenteNormal,
                                    fuenteNegrita,
                                    "Observaciones",
                                    pago.observaciones()
                            );
                        }

                        contexto.y(contexto.y() - 8);

                        numeroPago++;
                    }
                }


                // =================================================
                // PIE DEL DOCUMENTO
                // =================================================

                contexto = asegurarEspacio(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        90
                );

                contexto.y(contexto.y() - 10);

                escribirTexto(
                        contexto.contenido(),
                        fuenteNegrita,
                        10,
                        50,
                        contexto.y(),
                        "Honorario completamente pagado."
                );

                contexto.y(contexto.y() - 18);

                contexto = escribirParrafo(
                        documento,
                        contexto,
                        fuenteNormal,
                        fuenteNegrita,
                        "Este documento constituye una constancia interna de pago generada por LEXIA. "
                                + "No constituye por sí mismo una factura o boleta electrónica emitida ante SUNAT.",
                        9
                );

            } finally {

                contexto.contenido().close();
            }

            documento.save(salida);

            return salida.toByteArray();
        }
    }


    // =========================================================
    // NUEVA PÁGINA DEL PDF
    // =========================================================

    private PdfContexto nuevaPagina(
            PDDocument documento,
            PDType1Font fuenteNormal,
            PDType1Font fuenteNegrita) throws IOException {

        PDPage pagina =
                new PDPage(PDRectangle.A4);

        documento.addPage(pagina);

        PDPageContentStream contenido =
                new PDPageContentStream(
                        documento,
                        pagina
                );

        return new PdfContexto(
                contenido,
                792f
        );
    }


    // =========================================================
    // ASEGURAR ESPACIO EN LA PÁGINA
    // =========================================================

    private PdfContexto asegurarEspacio(
            PDDocument documento,
            PdfContexto contexto,
            PDType1Font fuenteNormal,
            PDType1Font fuenteNegrita,
            float espacioNecesario) throws IOException {

        if (contexto.y() - espacioNecesario >= 55f) {
            return contexto;
        }

        contexto.contenido().close();

        return nuevaPagina(
                documento,
                fuenteNormal,
                fuenteNegrita
        );
    }


    // =========================================================
    // ESCRIBIR CAMPO
    // =========================================================

    private PdfContexto escribirCampo(
            PDDocument documento,
            PdfContexto contexto,
            PDType1Font fuenteNormal,
            PDType1Font fuenteNegrita,
            String etiqueta,
            String valor) throws IOException {

        contexto = asegurarEspacio(
                documento,
                contexto,
                fuenteNormal,
                fuenteNegrita,
                45
        );

        escribirTexto(
                contexto.contenido(),
                fuenteNegrita,
                9,
                50,
                contexto.y(),
                etiqueta + ":"
        );

        contexto.y(contexto.y() - 14);

        contexto = escribirParrafo(
                documento,
                contexto,
                fuenteNormal,
                fuenteNegrita,
                textoSeguro(valor),
                9
        );

        contexto.y(contexto.y() - 5);

        return contexto;
    }


    // =========================================================
    // ESCRIBIR PÁRRAFO CON SALTO DE LÍNEA
    // =========================================================

    private PdfContexto escribirParrafo(
            PDDocument documento,
            PdfContexto contexto,
            PDType1Font fuenteNormal,
            PDType1Font fuenteNegrita,
            String texto,
            float tamanoFuente) throws IOException {

        List<String> lineas =
                dividirTexto(
                        textoSeguro(texto),
                        85
                );

        for (String linea : lineas) {

            contexto = asegurarEspacio(
                    documento,
                    contexto,
                    fuenteNormal,
                    fuenteNegrita,
                    20
            );

            escribirTexto(
                    contexto.contenido(),
                    fuenteNormal,
                    tamanoFuente,
                    50,
                    contexto.y(),
                    linea
            );

            contexto.y(contexto.y() - 14);
        }

        return contexto;
    }


    // =========================================================
    // ESCRIBIR TEXTO EN PDF
    // =========================================================

    private void escribirTexto(
            PDPageContentStream contenido,
            PDType1Font fuente,
            float tamano,
            float x,
            float y,
            String texto) throws IOException {

        contenido.beginText();

        contenido.setFont(
                fuente,
                tamano
        );

        contenido.newLineAtOffset(
                x,
                y
        );

        contenido.showText(
                limpiarTextoPdf(
                        textoSeguro(texto)
                )
        );

        contenido.endText();
    }


    // =========================================================
    // DIVIDIR TEXTO
    // =========================================================

    private List<String> dividirTexto(
            String texto,
            int maximoCaracteres) {

        if (texto == null || texto.isBlank()) {
            return List.of("-");
        }

        String limpio =
                texto.replaceAll("\\s+", " ")
                        .trim();

        java.util.ArrayList<String> lineas =
                new java.util.ArrayList<>();

        StringBuilder lineaActual =
                new StringBuilder();

        for (String palabra : limpio.split(" ")) {

            if (lineaActual.length() == 0) {

                lineaActual.append(palabra);

            } else if (lineaActual.length()
                    + 1
                    + palabra.length()
                    <= maximoCaracteres) {

                lineaActual
                        .append(" ")
                        .append(palabra);

            } else {

                lineas.add(
                        lineaActual.toString()
                );

                lineaActual =
                        new StringBuilder(palabra);
            }
        }

        if (lineaActual.length() > 0) {
            lineas.add(
                    lineaActual.toString()
            );
        }

        return lineas;
    }


    // =========================================================
    // TEXTO SEGURO
    // =========================================================

    private String textoSeguro(
            String valor) {

        if (valor == null || valor.isBlank()) {
            return "-";
        }

        return valor.trim();
    }


    // =========================================================
    // LIMPIAR TEXTO PARA FUENTES ESTÁNDAR PDF
    // =========================================================
    //
    // Helvetica estándar utiliza WinAnsi.
    //
    // Se reemplazan algunos caracteres tipográficos que pueden
    // aparecer en textos pegados desde Word o páginas web.
    //
    // =========================================================

    private String limpiarTextoPdf(
            String texto) {

        if (texto == null) {
            return "-";
        }

        return texto
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2022', '-')
                .replace('\u00A0', ' ');
    }


    // =========================================================
    // FORMATEAR FECHA
    // =========================================================

    private String formatearFecha(
            LocalDate fecha) {

        if (fecha == null) {
            return "-";
        }

        return fecha.format(
                FORMATO_FECHA
        );
    }


    // =========================================================
    // FORMATEAR MONEDA
    // =========================================================

    private String formatearMoneda(
            BigDecimal monto,
            String moneda) {

        BigDecimal valor =
                monto != null
                        ? monto
                        : BigDecimal.ZERO;

        NumberFormat formato =
                NumberFormat.getNumberInstance(
                        LOCALE_PERU
                );

        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);

        String codigo =
                moneda != null
                        && !moneda.isBlank()
                                ? moneda.trim().toUpperCase()
                                : "PEN";

        String simbolo =
                "PEN".equals(codigo)
                        ? "S/"
                        : codigo;

        return simbolo
                + " "
                + formato.format(valor);
    }


    // =========================================================
    // CONTEXTO DE ESCRITURA DEL PDF
    // =========================================================
    //
    // Guarda:
    //
    // - el stream de la página actual
    // - la posición vertical actual
    //
    // Esto permite crear páginas adicionales cuando el
    // historial de pagos sea demasiado grande.
    //
    // =========================================================

    private static final class PdfContexto {

        private final PDPageContentStream contenido;
        private float y;

        private PdfContexto(
                PDPageContentStream contenido,
                float y) {

            this.contenido = contenido;
            this.y = y;
        }

        private PDPageContentStream contenido() {
            return contenido;
        }

        private float y() {
            return y;
        }

        private void y(
                float nuevoY) {

            this.y = nuevoY;
        }
    }


    // =========================================================
    // REGISTRAR MOVIMIENTO FINANCIERO
    // =========================================================
    //
    // Cuando el movimiento sea "Honorario Pactado",
    // el Repository también almacenará la fecha de vencimiento.
    //
    // =========================================================

    @Override
    @Transactional
    public String registrarMovimiento(
            RegistrarMovimientoRequest request) {

        return repository.registrarMovimiento(
                getEstudioIdActual(),
                getUsuarioIdActual(),
                request
        );
    }


    // =========================================================
    // REGISTRAR PAGO
    // =========================================================

    @Override
    @Transactional
    public String registrarPago(
            String honorarioUuid,
            RegistrarPagoRequest request) {

        return repository.registrarPago(
                getEstudioIdActual(),
                getUsuarioIdActual(),
                honorarioUuid,
                request
        );
    }
}