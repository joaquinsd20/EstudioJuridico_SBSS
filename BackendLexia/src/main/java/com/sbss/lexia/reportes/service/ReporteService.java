package com.sbss.lexia.reportes.service;

import com.sbss.lexia.reportes.dto.ReporteDtos.*;
import com.sbss.lexia.reportes.repository.ReporteRepositoryJdbc;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private final ReporteRepositoryJdbc repo;

    @Transactional(readOnly = true)
    public ResumenReporte obtenerResumenReportes(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId, String preset) {
        KpiReporte kpis = repo.obtenerKpis(fechaInicio, fechaFin, materia, abogadoId);
        List<Etiqueta> porEstado = repo.expedientesPorEstado(fechaInicio, fechaFin, materia, abogadoId);
        List<Etiqueta> porMateria = repo.expedientesPorMateria(fechaInicio, fechaFin, materia, abogadoId);
        List<Barra> porAbogado = repo.expedientesPorAbogado(fechaInicio, fechaFin, materia, abogadoId);
        List<Barra> carga = repo.cargaPorUsuario(fechaInicio, fechaFin, materia, abogadoId);
        List<Barra> tareas = repo.tareasPorUsuario(fechaInicio, fechaFin, materia, abogadoId);
        List<Barra> ingresos = repo.ingresosPorMes(fechaInicio, fechaFin, materia, abogadoId);
        List<ItemReporteDetalle> detalle = repo.tablaDetalle(fechaInicio, fechaFin, materia, abogadoId);

        FiltrosReporte filtros = new FiltrosReporte(fechaInicio, fechaFin, materia, abogadoId, preset);

        return new ResumenReporte(filtros, kpis, porEstado, porMateria, porAbogado, carga, tareas, ingresos, detalle);
    }

    @Transactional(readOnly = true)
    public byte[] exportarExcel(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        List<ItemReporteDetalle> lista = repo.tablaDetalle(fechaInicio, fechaFin, materia, abogadoId);

        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM
        sb.append("\uFEFF");
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
        sb.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n");
        sb.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n");
        sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n");
        sb.append("<Styles>\n");
        sb.append("<Style ss:ID=\"Header\"><Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/><Interior ss:Color=\"#1E293B\" ss:Pattern=\"Solid\"/></Style>\n");
        sb.append("</Styles>\n");
        sb.append("<Worksheet ss:Name=\"Reporte Lexia\">\n");
        sb.append("<Table>\n");

        // Headers
        sb.append("<Row ss:StyleID=\"Header\">\n");
        sb.append("<Cell><Data ss:Type=\"String\">ID</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Código / N° Expediente</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Carátula / Título</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Cliente</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Contraparte</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Materia</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Abogado Responsable</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Estado</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Riesgo</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Fecha Inicio</Data></Cell>\n");
        sb.append("<Cell><Data ss:Type=\"String\">Cuantía (S/)</Data></Cell>\n");
        sb.append("</Row>\n");

        // Data Rows
        for (ItemReporteDetalle item : lista) {
            sb.append("<Row>\n");
            sb.append("<Cell><Data ss:Type=\"Number\">").append(item.id()).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.codigo())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.titulo())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.cliente())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.contraparte())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.materia())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.abogado())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.estado())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.nivelRiesgo())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"String\">").append(escapeXml(item.fechaInicio())).append("</Data></Cell>\n");
            sb.append("<Cell><Data ss:Type=\"Number\">").append(item.cuantia()).append("</Data></Cell>\n");
            sb.append("</Row>\n");
        }

        sb.append("</Table>\n");
        sb.append("</Worksheet>\n");
        sb.append("</Workbook>");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportarPdf(LocalDate fechaInicio, LocalDate fechaFin, String materia, Long abogadoId) {
        ResumenReporte datos = obtenerResumenReportes(fechaInicio, fechaFin, materia, abogadoId, "custom");
        return generarPdfEstructurado(datos);
    }

    private byte[] generarPdfEstructurado(ResumenReporte datos) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            String fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String rIni = datos.filtros().fechaInicio() != null ? datos.filtros().fechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Inicio";
            String rFin = datos.filtros().fechaFin() != null ? datos.filtros().fechaFin().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Hoy";
            String matSel = datos.filtros().materia() != null ? datos.filtros().materia() : "Todas las materias";

            StringBuilder pdfLines = new StringBuilder();
            
            // TITULO Y CABECERA
            pdfLines.append("BT\n/F1 15 Tf\n40 750 Td\n(ESTUDIO JURIDICO LEXIA & ASOCIADOS - INFORME ANALITICO DETALLADO) Tj\nET\n");
            pdfLines.append("BT\n/F1 9 Tf\n40 735 Td\n(Fecha de Emision: ").append(fechaHoy).append("   |   Periodo Analizado: ").append(rIni).append(" al ").append(rFin).append("   |   Materia: ").append(escapePdf(matSel)).append(") Tj\nET\n");

            // SECCION 1: RESUMEN DE KPIS
            pdfLines.append("BT\n/F1 11 Tf\n40 705 Td\n(1. INDICADORES CLAVE DE RENDIMIENTO (KPIS)) Tj\nET\n");
            
            String lineKpi1 = String.format("- Expedientes Totales: %d   |   En Tramite: %d   |   Concluidos: %d   |   Con Alerta/Riesgo: %d",
                    datos.kpis().totalExpedientes(), datos.kpis().expedientesEnTramite(), datos.kpis().expedientesConcluidos(), datos.kpis().expedientesVencidos());
            String lineKpi2 = String.format("- Ingresos Totales: S/ %,.2f   |   Cobrado: S/ %,.2f   |   Saldo Pendiente: S/ %,.2f",
                    datos.kpis().ingresosTotales(), datos.kpis().cobradoTotal(), datos.kpis().saldoPendiente());
            String lineKpi3 = String.format("- Tareas Pendientes en Sesion: %d   |   Tiempo Promedio Procesal: %.1f Meses",
                    datos.kpis().tareasPendientes(), datos.kpis().tiempoPromedioMeses());

            pdfLines.append("BT\n/F1 9 Tf\n50 688 Td\n(").append(escapePdf(cleanPdfText(lineKpi1))).append(") Tj\nET\n");
            pdfLines.append("BT\n/F1 9 Tf\n50 673 Td\n(").append(escapePdf(cleanPdfText(lineKpi2))).append(") Tj\nET\n");
            pdfLines.append("BT\n/F1 9 Tf\n50 658 Td\n(").append(escapePdf(cleanPdfText(lineKpi3))).append(") Tj\nET\n");

            // SECCION 2: DESGROSE POR MATERIA Y ABOGADO
            pdfLines.append("BT\n/F1 11 Tf\n40 630 Td\n(2. DESGLOSE DE DISTRIBUCION POR MATERIA) Tj\nET\n");
            StringBuilder matText = new StringBuilder("- Materias: ");
            for (Etiqueta et : datos.expedientesPorMateria()) {
                matText.append(et.nombre()).append(" (").append(et.total()).append("), ");
            }
            if (datos.expedientesPorMateria().isEmpty()) matText.append("Sin datos registrados");
            pdfLines.append("BT\n/F1 9 Tf\n50 615 Td\n(").append(escapePdf(cleanPdfText(matText.toString()))).append(") Tj\nET\n");

            // SECCION 3: TABLA DETALLADA DE EXPEDIENTES
            pdfLines.append("BT\n/F1 11 Tf\n40 585 Td\n(3. REGISTRO DETALLADO DE EXPEDIENTES EN EL PERIODO) Tj\nET\n");
            pdfLines.append("BT\n/F1 8 Tf\n40 570 Td\n(CODIGO | CLIENTE | CONTRAPARTE | MATERIA | ABOGADO | ESTADO | CUANTIA S/) Tj\nET\n");

            int y = 553;
            int limit = 0;
            for (ItemReporteDetalle item : datos.tablaDetalle()) {
                if (++limit > 22) break;
                String rowStr = String.format("%s | %s | %s | %s | %s | %s | S/ %,.2f",
                        trunc(item.codigo(), 14),
                        trunc(item.cliente(), 18),
                        trunc(item.contraparte(), 15),
                        trunc(item.materia(), 12),
                        trunc(item.abogado(), 16),
                        trunc(item.estado(), 12),
                        item.cuantia() != null ? item.cuantia() : java.math.BigDecimal.ZERO
                );
                pdfLines.append(String.format("BT\n/F1 8 Tf\n40 %d Td\n(%s) Tj\nET\n", y, escapePdf(cleanPdfText(rowStr))));
                y -= 15;
            }

            // PIE LEGAL DE PAGINA Y CONFIDENCIALIDAD
            pdfLines.append("BT\n/F1 8 Tf\n40 40 Td\n(Documento Confidencial generado por el Sistema Juridico LEXIA - Pagina 1 de 1) Tj\nET\n");
            pdfLines.append("BT\n/F1 8 Tf\n40 28 Td\n(Lexia & Asociados estudio juridico. Todos los derechos reservados.) Tj\nET\n");

            byte[] contentBytes = pdfLines.toString().getBytes(StandardCharsets.ISO_8859_1);
            int contentLen = contentBytes.length;

            String pdfHeader = "%PDF-1.4\n";
            String obj1 = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n";
            String obj2 = "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n";
            String obj3 = "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n";
            String obj4 = "4 0 obj\n<< /Length " + contentLen + " >>\nstream\n";
            String obj4End = "\nendstream\nendobj\n";
            String obj5 = "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n";

            out.write(pdfHeader.getBytes(StandardCharsets.ISO_8859_1));
            int off1 = out.size();
            out.write(obj1.getBytes(StandardCharsets.ISO_8859_1));
            int off2 = out.size();
            out.write(obj2.getBytes(StandardCharsets.ISO_8859_1));
            int off3 = out.size();
            out.write(obj3.getBytes(StandardCharsets.ISO_8859_1));
            int off4 = out.size();
            out.write(obj4.getBytes(StandardCharsets.ISO_8859_1));
            out.write(contentBytes);
            out.write(obj4End.getBytes(StandardCharsets.ISO_8859_1));
            int off5 = out.size();
            out.write(obj5.getBytes(StandardCharsets.ISO_8859_1));

            int xrefOff = out.size();
            String xref = "xref\n"
                    + "0 6\n"
                    + "0000000000 65535 f \n"
                    + String.format("%010d 00000 n \n", off1)
                    + String.format("%010d 00000 n \n", off2)
                    + String.format("%010d 00000 n \n", off3)
                    + String.format("%010d 00000 n \n", off4)
                    + String.format("%010d 00000 n \n", off5)
                    + "trailer\n"
                    + "<< /Size 6 /Root 1 0 R >>\n"
                    + "startxref\n"
                    + xrefOff + "\n"
                    + "%%EOF\n";

            out.write(xref.getBytes(StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            return "ERROR AL GENERAR PDF".getBytes(StandardCharsets.UTF_8);
        }
        return out.toByteArray();
    }

    private String trunc(String str, int max) {
        if (str == null) return "-";
        if (str.length() <= max) return str;
        return str.substring(0, max - 2) + "..";
    }

    private String escapeXml(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String escapePdf(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String cleanPdfText(String text) {
        if (text == null) return "-";
        return text.replaceAll("[^\\x00-\\x7F]", "");
    }
}
