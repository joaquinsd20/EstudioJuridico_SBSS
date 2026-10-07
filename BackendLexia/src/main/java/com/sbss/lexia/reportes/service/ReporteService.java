package com.sbss.lexia.reportes.service;

import com.sbss.lexia.reportes.dto.ReporteDtos.*;
import com.sbss.lexia.reportes.repository.ReporteRepositoryJdbc;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private static final int MAX_FILAS_EXPORTACION = 20_000;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color INDIGO = new Color(99, 102, 241);
    private static final Color INDIGO_DARK = new Color(67, 56, 202);
    private static final Color SLATE_50 = new Color(248, 250, 252);
    private static final Color INDIGO_50 = new Color(238, 242, 255);
    private static final Color SLATE_200 = new Color(226, 232, 240);
    private static final Color SLATE_500 = new Color(100, 116, 139);
    private static final Color SLATE_700 = new Color(51, 65, 85);
    private static final Color WHITE = Color.WHITE;

    private final ReporteRepositoryJdbc repo;

    @Transactional(readOnly = true)
    public ResumenReporte obtenerResumenReportes(LocalDate fechaInicio, LocalDate fechaFin,
                                                  String materia, Long abogadoId, String preset) {
        validarRango(fechaInicio, fechaFin);

        KpiReporte kpis = repo.obtenerKpis(fechaInicio, fechaFin, materia, abogadoId);
        List<Etiqueta> porEstado = repo.expedientesPorEstado(fechaInicio, fechaFin, materia, abogadoId);
        List<Etiqueta> porMateria = repo.expedientesPorMateria(fechaInicio, fechaFin, materia, abogadoId);
        List<Barra> carga = repo.cargaPorUsuario(fechaInicio, fechaFin, materia, abogadoId);
        List<Barra> ingresos = repo.ingresosPorMes(fechaInicio, fechaFin, materia, abogadoId);

        FiltrosReporte filtros = new FiltrosReporte(fechaInicio, fechaFin, materia, abogadoId, preset);

        return new ResumenReporte(
                filtros,
                kpis,
                porEstado,
                porMateria,
                List.of(),
                carga,
                List.of(),
                ingresos,
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public PaginaDto<ItemReporteDetalle> obtenerDetallePagina(LocalDate fechaInicio, LocalDate fechaFin,
                                                               String materia, Long abogadoId, String texto,
                                                               int page, int size) {
        validarRango(fechaInicio, fechaFin);
        return repo.detallePagina(fechaInicio, fechaFin, materia, abogadoId, texto, page, size);
    }

    @Transactional(readOnly = true)
    public CatalogosReporte catalogos() {
        return repo.catalogos();
    }

    /**
     * Excel con el mismo lenguaje visual del reporte de Tareas:
     * título azul oscuro, encabezados índigo, filas alternadas, bordes,
     * cuadrícula oculta y panel superior congelado.
     *
     * Se usa XSSFWorkbook para producir un OOXML .xlsx estándar y evitar los
     * avisos de reparación que Excel mostraba con el archivo anterior.
     */
    @Transactional(readOnly = true)
    public byte[] exportarExcel(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        validarRango(fechaInicio, fechaFin);
        List<ItemReporteDetalle> lista = detalleParaExportar(fechaInicio, fechaFin, materia, abogadoId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Reporte Lexia");
            sheet.setDisplayGridlines(false);
            sheet.createFreezePane(0, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, 2, 0, 10));

            PrintSetup printSetup = sheet.getPrintSetup();
            printSetup.setLandscape(true);
            printSetup.setFitWidth((short) 1);
            printSetup.setFitHeight((short) 0);
            sheet.setFitToPage(true);

            CellStyle titulo = estiloTitulo(workbook);
            CellStyle meta = estiloMeta(workbook);
            CellStyle encabezado = estiloEncabezado(workbook);
            CellStyle datoPar = estiloDato(workbook, "EEF2FF", HorizontalAlignment.LEFT);
            CellStyle datoImpar = estiloDato(workbook, "F8FAFC", HorizontalAlignment.LEFT);
            CellStyle centroPar = estiloDato(workbook, "EEF2FF", HorizontalAlignment.CENTER);
            CellStyle centroImpar = estiloDato(workbook, "F8FAFC", HorizontalAlignment.CENTER);
            CellStyle monedaPar = estiloMoneda(workbook, "EEF2FF");
            CellStyle monedaImpar = estiloMoneda(workbook, "F8FAFC");

            Row filaTitulo = sheet.createRow(0);
            filaTitulo.setHeightInPoints(32);
            Cell tituloCell = filaTitulo.createCell(0);
            tituloCell.setCellValue("LEXIA - REPORTE DEL ESTUDIO - " + LocalDateTime.now().format(FECHA_HORA));
            tituloCell.setCellStyle(titulo);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));
            aplicarEstiloRegion(sheet, 0, 0, 0, 10, titulo);

            Row filaMeta = sheet.createRow(1);
            filaMeta.setHeightInPoints(25);
            Cell metaCell = filaMeta.createCell(0);
            metaCell.setCellValue(
                    "Periodo: " + formatoFecha(fechaInicio, "Inicio") + " - " + formatoFecha(fechaFin, "Hoy")
                            + "   |   Materia: " + materiaMostrar(materia)
                            + "   |   Registros: " + lista.size());
            metaCell.setCellStyle(meta);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 10));
            aplicarEstiloRegion(sheet, 1, 1, 0, 10, meta);

            String[] titulos = {
                    "ID", "Código / N° Expediente", "Carátula / Título", "Cliente",
                    "Contraparte", "Materia", "Abogado Responsable", "Estado",
                    "Riesgo", "Fecha Inicio", "Cuantía (S/)"
            };

            Row header = sheet.createRow(2);
            header.setHeightInPoints(28);
            for (int i = 0; i < titulos.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(titulos[i]);
                c.setCellStyle(encabezado);
            }

            int fila = 3;
            for (int index = 0; index < lista.size(); index++) {
                ItemReporteDetalle item = lista.get(index);
                boolean par = index % 2 == 0;
                CellStyle dato = par ? datoPar : datoImpar;
                CellStyle centro = par ? centroPar : centroImpar;
                CellStyle moneda = par ? monedaPar : monedaImpar;

                Row row = sheet.createRow(fila++);
                row.setHeightInPoints(26);

                celda(row, 0, item.id() != null ? item.id() : 0L, centro);
                celda(row, 1, nvl(item.codigo()), centro);
                celda(row, 2, nvl(item.titulo()), dato);
                celda(row, 3, nvl(item.cliente()), dato);
                celda(row, 4, nvl(item.contraparte()), dato);
                celda(row, 5, nvl(item.materia()), dato);
                celda(row, 6, nvl(item.abogado()), dato);
                celda(row, 7, textoHumano(item.estado()), centro);
                celda(row, 8, textoHumano(item.nivelRiesgo()), centro);
                celda(row, 9, nvl(item.fechaInicio()), centro);

                Cell cuantia = row.createCell(10);
                cuantia.setCellValue(item.cuantia() != null ? item.cuantia().doubleValue() : 0.0);
                cuantia.setCellStyle(moneda);
            }

            if (lista.isEmpty()) {
                Row vacia = sheet.createRow(3);
                vacia.setHeightInPoints(28);
                Cell c = vacia.createCell(0);
                c.setCellValue("No existen expedientes para los filtros seleccionados.");
                c.setCellStyle(datoImpar);
                sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 10));
                aplicarEstiloRegion(sheet, 3, 3, 0, 10, datoImpar);
            }

            int[] anchos = {10, 22, 38, 28, 28, 24, 28, 18, 14, 16, 18};
            for (int i = 0; i < anchos.length; i++) {
                sheet.setColumnWidth(i, anchos[i] * 256);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo Excel del reporte.", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<ItemReporteDetalle> obtenerDatosExportacion(LocalDate fechaInicio, LocalDate fechaFin,
                                                             String materia, Long abogadoId) {
        validarRango(fechaInicio, fechaFin);
        return detalleParaExportar(fechaInicio, fechaFin, materia, abogadoId);
    }

    @Transactional(readOnly = true)
    public byte[] exportarPdf(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        validarRango(fechaInicio, fechaFin);
        KpiReporte kpis = repo.obtenerKpis(fechaInicio, fechaFin, materia, abogadoId);
        List<ItemReporteDetalle> detalle = detalleParaExportar(fechaInicio, fechaFin, materia, abogadoId);
        return generarPdf(kpis, detalle, fechaInicio, fechaFin, materia);
    }

    private List<ItemReporteDetalle> detalleParaExportar(LocalDate fechaInicio, LocalDate fechaFin,
                                                          String materia, Long abogadoId) {
        List<ItemReporteDetalle> lista = repo.detalleExportacion(
                fechaInicio, fechaFin, materia, abogadoId, MAX_FILAS_EXPORTACION + 1);
        if (lista.size() > MAX_FILAS_EXPORTACION) {
            throw new ParametroInvalidoException(
                    "El reporte supera " + MAX_FILAS_EXPORTACION
                            + " expedientes. Acota el rango o aplica filtros antes de exportar.");
        }
        return lista;
    }

    // =====================================================================
    // EXCEL - estilos alineados al reporte de Tareas
    // =====================================================================

    private CellStyle estiloTitulo(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color("0F172A"));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(color("FFFFFF"));
        style.setFont(font);
        return style;
    }

    private CellStyle estiloMeta(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color("E2E8F0"));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(color("334155"));
        style.setFont(font);
        bordes(style, color("CBD5E1"));
        return style;
    }

    private CellStyle estiloEncabezado(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color("6366F1"));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(color("FFFFFF"));
        style.setFont(font);
        bordes(style, color("4338CA"));
        return style;
    }

    private CellStyle estiloDato(XSSFWorkbook workbook, String fondo, HorizontalAlignment alineacion) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color(fondo));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(alineacion);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(color("1E293B"));
        style.setFont(font);
        bordes(style, color("CBD5E1"));
        return style;
    }

    private CellStyle estiloMoneda(XSSFWorkbook workbook, String fondo) {
        CellStyle style = estiloDato(workbook, fondo, HorizontalAlignment.RIGHT);
        style.setDataFormat(workbook.createDataFormat().getFormat("S/ #,##0.00"));
        return style;
    }

    private XSSFColor color(String hex) {
        return new XSSFColor(
                new Color(Integer.parseInt(hex.substring(0, 2), 16),
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16)),
                new DefaultIndexedColorMap());
    }

    private void bordes(XSSFCellStyle style, XSSFColor color) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setTopBorderColor(color);
        style.setRightBorderColor(color);
        style.setBottomBorderColor(color);
        style.setLeftBorderColor(color);
    }

    private void aplicarEstiloRegion(Sheet sheet, int filaIni, int filaFin, int colIni, int colFin, CellStyle style) {
        for (int f = filaIni; f <= filaFin; f++) {
            Row row = sheet.getRow(f);
            if (row == null) row = sheet.createRow(f);
            for (int c = colIni; c <= colFin; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) cell = row.createCell(c);
                cell.setCellStyle(style);
            }
        }
    }

    private void celda(Row row, int columna, Object valor, CellStyle style) {
        Cell cell = row.createCell(columna);
        if (valor instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(valor != null ? valor.toString() : "");
        }
        cell.setCellStyle(style);
    }

    // =====================================================================
    // PDF - diseño visual mejorado
    // =====================================================================

    private byte[] generarPdf(KpiReporte kpis, List<ItemReporteDetalle> detalle,
                              LocalDate fechaInicio, LocalDate fechaFin, String materia) {
        PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDRectangle paginaA4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int indice = 0;
            int numeroPagina = 1;

            do {
                boolean primeraPagina = numeroPagina == 1;
                PDPage page = new PDPage(paginaA4);
                document.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    dibujarCabeceraPdf(cs, bold, normal, paginaA4, fechaInicio, fechaFin, materia, numeroPagina);

                    float y;
                    if (primeraPagina) {
                        dibujarKpisPdf(cs, bold, normal, kpis, paginaA4);
                        // Deja una separación real entre las tarjetas KPI y la sección de detalle.
                        // Antes el título comenzaba prácticamente sobre el borde inferior de las tarjetas.
                        y = paginaA4.getHeight() - 220;
                    } else {
                        // En páginas siguientes no hay KPIs, pero se mantiene aire debajo de la cabecera.
                        y = paginaA4.getHeight() - 105;
                    }

                    dibujarTituloTablaPdf(cs, bold, paginaA4, y, detalle.size());
                    y -= 29;
                    dibujarCabeceraTablaPdf(cs, bold, y);
                    y -= 31;

                    while (indice < detalle.size() && y >= 54) {
                        ItemReporteDetalle item = detalle.get(indice);
                        float alto = altoFilaPdf(item);
                        if (y - alto < 43) break;
                        dibujarFilaTablaPdf(cs, normal, bold, item, y, alto, indice);
                        y -= alto;
                        indice++;
                    }

                    if (detalle.isEmpty() && primeraPagina) {
                        setFill(cs, SLATE_50);
                        cs.addRect(30, y - 34, paginaA4.getWidth() - 60, 34);
                        cs.fill();
                        escribir(cs, normal, 9, 44, y - 21,
                                "No existen expedientes para los filtros seleccionados.", SLATE_500);
                    }

                    dibujarPiePdf(cs, normal, paginaA4, numeroPagina);
                }

                numeroPagina++;
            } while (indice < detalle.size() || document.getNumberOfPages() == 0);

            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte.", ex);
        }
    }

    private void dibujarCabeceraPdf(PDPageContentStream cs, PDType1Font bold, PDType1Font normal,
                                    PDRectangle page, LocalDate inicio, LocalDate fin,
                                    String materia, int pagina) throws IOException {
        float width = page.getWidth();
        float top = page.getHeight();

        setFill(cs, NAVY);
        cs.addRect(0, top - 74, width, 74);
        cs.fill();

        setFill(cs, INDIGO);
        cs.addRect(0, top - 78, width, 4);
        cs.fill();

        escribir(cs, bold, 17, 30, top - 31, "LEXIA - Reporte del Estudio", WHITE);
        escribir(cs, normal, 8.5f, 30, top - 49,
                "Analítica, rendimiento y detalle de expedientes", new Color(203, 213, 225));
        escribir(cs, normal, 8.2f, 30, top - 64,
                "Periodo: " + formatoFecha(inicio, "Inicio") + " al " + formatoFecha(fin, "Hoy")
                        + "   |   Materia: " + materiaMostrar(materia)
                        + "   |   Emitido: " + LocalDateTime.now().format(FECHA_HORA),
                new Color(148, 163, 184));
        escribirDerecha(cs, bold, 8.5f, width - 30, top - 31, "PÁGINA " + pagina, WHITE);
    }

    private void dibujarKpisPdf(PDPageContentStream cs, PDType1Font bold, PDType1Font normal,
                                KpiReporte kpis, PDRectangle page) throws IOException {
        float x = 30;
        float y = page.getHeight() - 112;
        float gap = 12;
        float cardW = (page.getWidth() - 60 - gap * 3) / 4f;
        float cardH = 76;

        dibujarKpi(cs, bold, normal, x, y, cardW, cardH,
                "Ingresos facturados", "S/ " + dinero(kpis.ingresosTotales()),
                "Cobrado S/ " + dinero(kpis.cobradoTotal()) + " · Pendiente S/ " + dinero(kpis.saldoPendiente()));
        x += cardW + gap;
        dibujarKpi(cs, bold, normal, x, y, cardW, cardH,
                "Expedientes", String.valueOf(kpis.totalExpedientes()),
                kpis.expedientesEnTramite() + " en trámite · " + kpis.expedientesConcluidos() + " concluidos");
        x += cardW + gap;
        dibujarKpi(cs, bold, normal, x, y, cardW, cardH,
                "Horas facturables", String.format("%.1f h", kpis.horasFacturables()),
                "Horas de actuaciones del periodo");
        x += cardW + gap;
        dibujarKpi(cs, bold, normal, x, y, cardW, cardH,
                "Tiempo promedio", String.format("%.1f meses", kpis.tiempoPromedioMeses()),
                kpis.tareasPendientes() + " tareas pendientes");
    }

    private void dibujarKpi(PDPageContentStream cs, PDType1Font bold, PDType1Font normal,
                            float x, float y, float w, float h,
                            String label, String value, String detail) throws IOException {
        setFill(cs, SLATE_50);
        cs.addRect(x, y - h, w, h);
        cs.fill();
        setStroke(cs, SLATE_200);
        cs.addRect(x, y - h, w, h);
        cs.stroke();

        setFill(cs, INDIGO);
        cs.addRect(x, y - h, 4, h);
        cs.fill();

        escribir(cs, bold, 8, x + 13, y - 19, label, SLATE_500);
        escribir(cs, bold, 15, x + 13, y - 42, trunc(value, 24), NAVY);
        escribir(cs, normal, 7.1f, x + 13, y - 62, trunc(detail, 42), SLATE_500);
    }

    private void dibujarTituloTablaPdf(PDPageContentStream cs, PDType1Font bold, PDRectangle page,
                                       float y, int total) throws IOException {
        // Título a la izquierda y total a la derecha: evita que ambos textos queden amontonados.
        escribir(cs, bold, 11f, 30, y, "EXPEDIENTES DEL REPORTE", NAVY);
        escribirDerecha(cs, bold, 8f, page.getWidth() - 30, y,
                total + (total == 1 ? " REGISTRO" : " REGISTROS"), INDIGO_DARK);

        // Separador visual para dar aire entre el bloque de KPIs y la tabla.
        setStroke(cs, SLATE_200);
        cs.moveTo(30, y - 11);
        cs.lineTo(page.getWidth() - 30, y - 11);
        cs.stroke();
    }

    private void dibujarCabeceraTablaPdf(PDPageContentStream cs, PDType1Font bold, float y) throws IOException {
        float x = 30;
        float h = 24;
        float[] widths = {118, 145, 105, 135, 83, 70, 86};
        String[] headers = {"EXPEDIENTE", "CLIENTE", "MATERIA", "ABOGADO", "ESTADO", "RIESGO", "CUANTÍA"};

        setFill(cs, INDIGO);
        cs.addRect(x, y - h, suma(widths), h);
        cs.fill();

        for (int i = 0; i < headers.length; i++) {
            escribir(cs, bold, 7.2f, x + 6, y - 15, headers[i], WHITE);
            x += widths[i];
        }
    }

    private float altoFilaPdf(ItemReporteDetalle item) {
        int lineas = Math.max(
                lineasNecesarias(nvl(item.titulo()), 28),
                Math.max(lineasNecesarias(nvl(item.cliente()), 31), lineasNecesarias(nvl(item.abogado()), 27))
        );
        return Math.max(30, 16 + Math.min(lineas, 2) * 9);
    }

    private void dibujarFilaTablaPdf(PDPageContentStream cs, PDType1Font normal, PDType1Font bold,
                                     ItemReporteDetalle d, float y, float h, int indice) throws IOException {
        float x = 30;
        float[] widths = {118, 145, 105, 135, 83, 70, 86};
        Color fondo = indice % 2 == 0 ? INDIGO_50 : WHITE;

        setFill(cs, fondo);
        cs.addRect(x, y - h, suma(widths), h);
        cs.fill();
        setStroke(cs, SLATE_200);
        cs.addRect(x, y - h, suma(widths), h);
        cs.stroke();

        float textY = y - 12;
        escribir(cs, bold, 7.2f, x + 6, textY, trunc(nvl(d.codigo()), 22), INDIGO_DARK);
        escribirLineas(cs, normal, 6.5f, x + 6, textY - 10, widths[0] - 12,
                nvl(d.titulo()), SLATE_500, 1);
        x += widths[0];

        escribirLineas(cs, normal, 7.1f, x + 6, textY, widths[1] - 12,
                nvl(d.cliente()), SLATE_700, 2);
        x += widths[1];

        escribirLineas(cs, normal, 7.1f, x + 6, textY, widths[2] - 12,
                nvl(d.materia()), SLATE_700, 2);
        x += widths[2];

        escribirLineas(cs, normal, 7.1f, x + 6, textY, widths[3] - 12,
                nvl(d.abogado()), SLATE_700, 2);
        x += widths[3];

        escribir(cs, normal, 7.1f, x + 6, textY, trunc(textoHumano(d.estado()), 16), SLATE_700);
        x += widths[4];

        Color riesgoColor = colorRiesgo(d.nivelRiesgo());
        escribir(cs, bold, 6.8f, x + 6, textY, trunc(textoHumano(d.nivelRiesgo()), 11), riesgoColor);
        x += widths[5];

        BigDecimal cuantia = d.cuantia() != null ? d.cuantia() : BigDecimal.ZERO;
        escribirDerecha(cs, bold, 7.1f, x + widths[6] - 6, textY,
                "S/ " + dinero(cuantia), NAVY);
    }

    private void dibujarPiePdf(PDPageContentStream cs, PDType1Font normal,
                               PDRectangle page, int pagina) throws IOException {
        setStroke(cs, SLATE_200);
        cs.moveTo(30, 34);
        cs.lineTo(page.getWidth() - 30, 34);
        cs.stroke();
        escribir(cs, normal, 6.8f, 30, 21,
                "Documento confidencial generado por LEXIA. Su contenido está sujeto a permisos y trazabilidad del sistema.",
                SLATE_500);
        escribirDerecha(cs, normal, 6.8f, page.getWidth() - 30, 21,
                "Página " + pagina, SLATE_500);
    }

    private void escribirLineas(PDPageContentStream cs, PDType1Font font, float size,
                                float x, float y, float maxWidth, String texto,
                                Color color, int maxLineas) throws IOException {
        List<String> lineas = envolverTexto(font, size, texto, maxWidth, maxLineas);
        for (int i = 0; i < lineas.size(); i++) {
            escribir(cs, font, size, x, y - i * 9, lineas.get(i), color);
        }
    }

    private List<String> envolverTexto(PDType1Font font, float size, String texto,
                                       float maxWidth, int maxLineas) throws IOException {
        String limpio = pdfSeguro(texto);
        if (limpio.isBlank()) return List.of("—");

        String[] palabras = limpio.split("\\s+");
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();

        for (String palabra : palabras) {
            String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
            float ancho = font.getStringWidth(candidato) / 1000f * size;
            if (ancho <= maxWidth || actual.isEmpty()) {
                actual.setLength(0);
                actual.append(candidato);
            } else {
                lineas.add(actual.toString());
                actual.setLength(0);
                actual.append(palabra);
                if (lineas.size() >= maxLineas) break;
            }
        }

        if (!actual.isEmpty() && lineas.size() < maxLineas) lineas.add(actual.toString());
        if (lineas.size() > maxLineas) lineas = new ArrayList<>(lineas.subList(0, maxLineas));

        if (lineas.size() == maxLineas) {
            String ultima = lineas.get(maxLineas - 1);
            while (font.getStringWidth(ultima + "...") / 1000f * size > maxWidth && ultima.length() > 3) {
                ultima = ultima.substring(0, ultima.length() - 1);
            }
            if (!ultima.equals(limpio)) lineas.set(maxLineas - 1, ultima + "...");
        }
        return lineas;
    }

    private int lineasNecesarias(String texto, int charsPorLinea) {
        if (texto == null || texto.isBlank()) return 1;
        return Math.max(1, (int) Math.ceil(texto.length() / (double) charsPorLinea));
    }

    private void escribir(PDPageContentStream cs, PDType1Font font, float size,
                          float x, float y, String texto, Color color) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(pdfSeguro(texto));
        cs.endText();
    }

    private void escribirDerecha(PDPageContentStream cs, PDType1Font font, float size,
                                 float rightX, float y, String texto, Color color) throws IOException {
        String seguro = pdfSeguro(texto);
        float width = font.getStringWidth(seguro) / 1000f * size;
        escribir(cs, font, size, rightX - width, y, seguro, color);
    }

    private void setFill(PDPageContentStream cs, Color color) throws IOException {
        cs.setNonStrokingColor(color);
    }

    private void setStroke(PDPageContentStream cs, Color color) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(0.5f);
    }

    private float suma(float[] valores) {
        float total = 0;
        for (float v : valores) total += v;
        return total;
    }

    private Color colorRiesgo(String riesgo) {
        String r = riesgo == null ? "" : riesgo.toUpperCase();
        return switch (r) {
            case "CRITICO", "CRÍTICO" -> new Color(185, 28, 28);
            case "ALTO" -> new Color(180, 83, 9);
            case "MEDIO" -> new Color(161, 98, 7);
            case "BAJO" -> new Color(21, 128, 61);
            default -> SLATE_500;
        };
    }

    private String dinero(BigDecimal value) {
        BigDecimal seguro = value != null ? value : BigDecimal.ZERO;
        return String.format("%,.2f", seguro);
    }

    private String dinero(double value) {
        return String.format("%,.2f", value);
    }

    private String pdfSeguro(String texto) {
        if (texto == null) return "";
        String limpio = texto
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replace('–', '-')
                .replace('—', '-')
                .replace('“', '"')
                .replace('”', '"')
                .replace('’', '\'')
                .replace('\u00A0', ' ');

        String normalizado = Normalizer.normalize(limpio, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(normalizado.length());
        for (char c : normalizado.toCharArray()) {
            if (c >= 32 && c <= 255) out.append(c);
            else out.append(' ');
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private String formatoFecha(LocalDate fecha, String fallback) {
        return fecha != null ? fecha.format(FECHA) : fallback;
    }

    private String materiaMostrar(String materia) {
        return materia == null || materia.isBlank() || materia.equalsIgnoreCase("todas")
                ? "Todas las materias" : materia;
    }

    private String textoHumano(String valor) {
        if (valor == null || valor.isBlank()) return "—";
        String t = valor.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private String trunc(String str, int max) {
        if (str == null || str.isBlank()) return "—";
        if (str.length() <= max) return str;
        return str.substring(0, Math.max(1, max - 3)) + "...";
    }

    private String nvl(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void validarRango(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new ParametroInvalidoException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }
}
