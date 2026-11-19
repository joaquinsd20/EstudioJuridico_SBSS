package com.sbss.lexia.escritosia.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

/** Genera escritos judiciales y documentos legales en Word/PDF con formato sobrio. */
@Service
@Slf4j
public class EscritosIaDocumentoService {

    public static final String WORD_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String PDF_MIME = "application/pdf";

    private static final Pattern NUMERACION = Pattern.compile("^(?:\\d+(?:-[a-zA-Z])?(?:\\.\\d+)*[.)]?|[a-zA-Z][.)])\\s+.*");
    private static final Pattern SECCION = Pattern.compile("^(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII)(?:\\.|\\))\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("(?i)^(EXPEDIENTE|ESPECIALISTA(?: LEGAL)?|SECRETARIO(?:/A)?|ESCRITO(?: N[.°º]*)?|CUADERNO|SUMILLA|MATERIA|DEMANDANTE|DEMANDADO)\\s*:");

    public record ArchivoGenerado(String nombreArchivo, String mimeType, byte[] bytes) { }

    private enum TipoBloque { META, DESTINATARIO, TITULO, ENCABEZADO, LISTA, TEXTO, FECHA, FIRMA }

    private record Bloque(String texto, TipoBloque tipo) { }

    private record CursorPdf(PDPageContentStream stream, float y) { }

    private record Renglon(String texto, boolean negrita) { }

    public ArchivoGenerado generarWord(String titulo, String contenido) {
        validar(titulo, contenido);
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getProperties().getCoreProperties().setTitle(titulo.trim());
            doc.getProperties().getCoreProperties().setDescription("Borrador generado en LEXIA para revisión profesional.");
            configurarPaginaA4(doc);

            List<Bloque> bloques = analizarContenido(contenido);
            for (int i = 0; i < bloques.size();) {
                Bloque bloque = bloques.get(i);
                if (bloque.tipo() == TipoBloque.FIRMA) {
                    List<String> firmas = new ArrayList<>();
                    while (i < bloques.size() && bloques.get(i).tipo() == TipoBloque.FIRMA) {
                        firmas.add(bloques.get(i).texto());
                        i++;
                    }
                    agregarBloqueFirmasWord(doc, firmas);
                    continue;
                }
                XWPFParagraph p = doc.createParagraph();
                aplicarEstiloWord(p, bloque);
                agregarTextoFormateado(p, bloque.texto(), bloque.tipo() == TipoBloque.META ? 9.5 : 12);
                i++;
            }

            agregarPieWord(doc);
            doc.write(out);
            return new ArchivoGenerado(nombreSeguro(titulo, "docx"), WORD_MIME, out.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.error("No se pudo generar el Word del escrito ({}).", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el documento Word. Inténtalo nuevamente.");
        }
    }

    public ArchivoGenerado generarPdf(String titulo, String contenido) {
        validar(titulo, contenido);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            PDType1Font cursiva = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

            final float margenIzquierdo = 85f;  // 3 cm, conforme a la referencia de forma del CPC.
            final float margenDerecho = 57f;    // 2 cm.
            final float margenSuperior = 68f;
            final float margenInferior = 62f;
            final float anchoDisponible = PDRectangle.A4.getWidth() - margenIzquierdo - margenDerecho;
            final float fuenteCuerpo = 12f;
            final float interlineadoCuerpo = 22.5f;

            List<PDPage> paginas = new ArrayList<>();
            PDPageContentStream stream = null;
            float y = 0f;

            List<Bloque> bloques = analizarContenido(contenido);
            for (int indiceBloque = 0; indiceBloque < bloques.size();) {
                Bloque bloque = bloques.get(indiceBloque);
                if (bloque.tipo() == TipoBloque.FIRMA) {
                    List<String> firmas = new ArrayList<>();
                    while (indiceBloque < bloques.size() && bloques.get(indiceBloque).tipo() == TipoBloque.FIRMA) {
                        firmas.add(bloques.get(indiceBloque).texto());
                        indiceBloque++;
                    }
                    CursorPdf cursor = dibujarFirmasPdf(doc, paginas, stream, firmas, y,
                            normal, negrita, margenIzquierdo, margenInferior, anchoDisponible);
                    stream = cursor.stream();
                    y = cursor.y();
                    continue;
                }
                indiceBloque++;
                PDType1Font font = (bloque.tipo() == TipoBloque.ENCABEZADO
                        || bloque.tipo() == TipoBloque.DESTINATARIO
                        || bloque.tipo() == TipoBloque.TITULO) ? negrita : normal;
                float size = bloque.tipo() == TipoBloque.META ? 9.5f : fuenteCuerpo;
                float before = switch (bloque.tipo()) {
                    case ENCABEZADO -> 9f;
                    case DESTINATARIO -> 7f;
                    case TITULO -> 12f;
                    default -> 0f;
                };
                float after = switch (bloque.tipo()) {
                    case META -> 2f;
                    case ENCABEZADO -> 3f;
                    case DESTINATARIO -> 8f;
                    case TITULO -> 8f;
                    default -> 1f;
                };
                float leading = bloque.tipo() == TipoBloque.META ? 13f
                        : (bloque.tipo() == TipoBloque.ENCABEZADO || bloque.tipo() == TipoBloque.DESTINATARIO
                        || bloque.tipo() == TipoBloque.TITULO ? 16f : interlineadoCuerpo);
                float indent = bloque.tipo() == TipoBloque.LISTA ? 20f
                        : (bloque.tipo() == TipoBloque.TEXTO ? 24f : 0f);

                if (stream == null) {
                    PDPage page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    paginas.add(page);
                    stream = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - margenSuperior;
                }
                y -= before;

                List<String> lineas = envolverPorAncho(bloque.texto(), font, size, anchoDisponible - indent);
                for (int i = 0; i < lineas.size(); i++) {
                    if (y < margenInferior + leading) {
                        stream.close();
                        PDPage page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        paginas.add(page);
                        stream = new PDPageContentStream(doc, page);
                        y = page.getMediaBox().getHeight() - margenSuperior;
                    }

                    String texto = textoCompatible(font, lineas.get(i));
                    float textoAncho = font.getStringWidth(texto) / 1000f * size;
                    float x = margenIzquierdo + (bloque.tipo() == TipoBloque.TEXTO && i == 0 ? indent : 0f);
                    if (bloque.tipo() == TipoBloque.LISTA) {
                        x = margenIzquierdo + 20f + (i > 0 ? 10f : 0f);
                    }
                    if (bloque.tipo() == TipoBloque.META || bloque.tipo() == TipoBloque.FECHA) {
                        x = PDRectangle.A4.getWidth() - margenDerecho - textoAncho;
                    } else if (bloque.tipo() == TipoBloque.TITULO || bloque.tipo() == TipoBloque.FIRMA) {
                        x = Math.max(margenIzquierdo, (PDRectangle.A4.getWidth() - textoAncho) / 2f);
                    }
                    stream.beginText();
                    stream.setFont(font, size);
                    stream.newLineAtOffset(x, y);
                    stream.showText(texto);
                    stream.endText();
                    y -= leading;
                }
                y -= after;
            }

            if (stream != null) stream.close();
            if (paginas.isEmpty()) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                paginas.add(page);
            }

            // Pie uniforme con marca de borrador y numeración real de páginas.
            for (int i = 0; i < paginas.size(); i++) {
                try (PDPageContentStream pie = new PDPageContentStream(
                        doc, paginas.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    pie.beginText();
                    pie.setFont(cursiva, 8f);
                    pie.newLineAtOffset(margenIzquierdo, 27f);
                    pie.showText(textoCompatible(cursiva, "BORRADOR PARA REVISION PROFESIONAL - LEXIA"));
                    pie.endText();

                    String numeracion = "Pagina " + (i + 1) + " de " + paginas.size();
                    float ancho = normal.getStringWidth(textoCompatible(normal, numeracion)) / 1000f * 8f;
                    pie.beginText();
                    pie.setFont(normal, 8f);
                    pie.newLineAtOffset(PDRectangle.A4.getWidth() - margenDerecho - ancho, 27f);
                    pie.showText(textoCompatible(normal, numeracion));
                    pie.endText();
                }
            }

            doc.save(out);
            return new ArchivoGenerado(nombreSeguro(titulo, "pdf"), PDF_MIME, out.toByteArray());
        } catch (IOException | RuntimeException e) {
            log.error("No se pudo generar el PDF del escrito ({}).", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el documento PDF. Inténtalo nuevamente.");
        }
    }

    private List<Bloque> analizarContenido(String contenido) {
        List<Bloque> bloques = new ArrayList<>();
        String[] lineas = normalizarSaltos(contenido).split("\\n");
        // La zona de firmas (todo lo que sigue a la línea de lugar y fecha) se interpreta completa y no
        // línea por línea; así "BANCO DEL PERÚ S.A.A." no se confunde con un encabezado de sección.
        int inicioFirmas = FirmasEscrito.inicioZona(lineas);
        int limite = inicioFirmas >= 0 ? inicioFirmas : lineas.length;
        StringBuilder textoActual = new StringBuilder();
        TipoBloque tipoActual = null;
        boolean zonaMeta = true;
        boolean primeraLineaUtil = true;

        for (int indiceLinea = 0; indiceLinea < limite; indiceLinea++) {
            String raw = lineas[indiceLinea];
            String linea = limpiarMarkdown(raw);
            if (linea.isBlank() || linea.matches("[-_=]{3,}")) {
                agregarBloque(bloques, textoActual, tipoActual);
                tipoActual = null;
                if (!linea.isBlank()) continue;
                continue;
            }
            String upper = linea.toUpperCase(Locale.ROOT);
            if (upper.contains("BORRADOR PARA REVISION PROFESIONAL") || upper.matches("PAGINA\\s+\\d+\\s+DE\\s+\\d+")) {
                continue;
            }

            if (zonaMeta && META.matcher(linea).find()) {
                agregarBloque(bloques, textoActual, tipoActual);
                tipoActual = null;
                bloques.add(new Bloque(linea, TipoBloque.META));
                continue;
            }
            zonaMeta = false;

            TipoBloque tipo = clasificar(linea, primeraLineaUtil);
            primeraLineaUtil = false;
            if ((tipo == TipoBloque.TEXTO && tipoActual == TipoBloque.TEXTO
                    || tipo == TipoBloque.TEXTO && tipoActual == TipoBloque.FIRMA)
                    && textoActual.length() > 0) {
                textoActual.append(" ").append(linea);
                continue;
            }
            agregarBloque(bloques, textoActual, tipoActual);
            textoActual.setLength(0);
            textoActual.append(linea);
            tipoActual = tipo;
        }
        agregarBloque(bloques, textoActual, tipoActual);
        if (inicioFirmas >= 0) {
            for (String firma : FirmasEscrito.bloques(lineas, inicioFirmas)) {
                bloques.add(new Bloque(firma, TipoBloque.FIRMA));
            }
        }
        return bloques;
    }

    private void agregarBloque(List<Bloque> bloques, StringBuilder texto, TipoBloque tipo) {
        if (tipo != null && texto.length() > 0) {
            bloques.add(new Bloque(texto.toString().trim(), tipo));
            texto.setLength(0);
        }
    }

    private TipoBloque clasificar(String linea, boolean primeraLineaUtil) {
        String limpia = linea.trim();
        String upper = limpia.toUpperCase(Locale.ROOT);
        if (upper.startsWith("SEÑOR JUEZ") || upper.startsWith("SEÑORA JUEZA")
                || upper.startsWith("SEÑOR RESPONSABLE") || upper.startsWith("SEÑORA RESPONSABLE")) {
            return TipoBloque.DESTINATARIO;
        }
        if (primeraLineaUtil && limpia.length() <= 100 && esMayuscula(limpia)) {
            return TipoBloque.TITULO;
        }
        if (upper.startsWith("POR TANTO") || upper.startsWith("OTROSÍ DIGO") || upper.startsWith("OTROSI DIGO")
                || SECCION.matcher(limpia).matches() || (limpia.length() < 100 && esMayuscula(limpia))) {
            return TipoBloque.ENCABEZADO;
        }
        if (NUMERACION.matcher(limpia).matches() || limpia.startsWith("• ") || limpia.startsWith("- ")) {
            return TipoBloque.LISTA;
        }
        if (upper.startsWith("TRUJILLO,") || upper.startsWith("LIMA,") || upper.startsWith("[LUGAR Y FECHA")) {
            return TipoBloque.FECHA;
        }
        if (limpia.matches("^[_—-]\\s*.+") || upper.startsWith("FIRMA DEL ")
                || upper.startsWith("REPRESENTANTE LEGAL:") || upper.startsWith("ABOGADO PATROCINANTE:")) {
            return TipoBloque.FIRMA;
        }
        return TipoBloque.TEXTO;
    }

    private boolean esMayuscula(String texto) {
        boolean tieneLetras = texto.matches(".*[A-ZÁÉÍÓÚÜÑ].*");
        return tieneLetras && texto.equals(texto.toUpperCase(Locale.ROOT))
                && !texto.startsWith("[") && !texto.matches(".*\\[[^]]+\\].*");
    }

    private String limpiarMarkdown(String texto) {
        if (texto == null) return "";
        String limpio = texto.strip();
        limpio = limpio.replaceFirst("^(#{1,6})\\s*", "");
        limpio = limpio.replace("**", "").replace("__", "");
        limpio = limpio.replaceAll("^\\s*[-*_]{3,}\\s*$", "");
        return limpio;
    }

    /** Renderiza las firmas en columnas iguales para evitar que nombres y cargos se desalineen. */
    private void agregarBloqueFirmasWord(XWPFDocument doc, List<String> firmas) {
        if (firmas == null || firmas.isEmpty()) return;
        int columnas = firmas.size() == 1 ? 1 : 2;
        int filas = (firmas.size() + columnas - 1) / columnas;
        XWPFTable tabla = doc.createTable(filas, columnas);
        tabla.setWidth("100%");
        tabla.setCellMargins(0, 120, 0, 120);

        CTTblBorders bordes = tabla.getCTTbl().getTblPr().isSetTblBorders()
                ? tabla.getCTTbl().getTblPr().getTblBorders()
                : tabla.getCTTbl().getTblPr().addNewTblBorders();
        bordes.addNewTop().setVal(STBorder.NIL);
        bordes.addNewLeft().setVal(STBorder.NIL);
        bordes.addNewBottom().setVal(STBorder.NIL);
        bordes.addNewRight().setVal(STBorder.NIL);
        bordes.addNewInsideH().setVal(STBorder.NIL);
        bordes.addNewInsideV().setVal(STBorder.NIL);

        for (int i = 0; i < firmas.size(); i++) {
            int fila = i / columnas;
            int columna = i % columnas;
            XWPFTableCell celda = tabla.getRow(fila).getCell(columna);
            celda.setWidth((100 / columnas) + "%");
            celda.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);

            XWPFParagraph linea = celda.getParagraphs().get(0);
            linea.setAlignment(ParagraphAlignment.CENTER);
            linea.setSpacingBefore(260);
            linea.setSpacingAfter(55);
            linea.setSpacingBetween(1.0);
            linea.setKeepNext(true);
            XWPFRun runLinea = linea.createRun();
            configurarRun(runLinea, 10, false);
            runLinea.setText("________________________________");

            String textoFirma = normalizarTextoFirma(firmas.get(i));
            String[] renglones = textoFirma.split("\\n");
            for (int j = 0; j < renglones.length; j++) {
                if (renglones[j].isBlank()) continue;
                XWPFParagraph etiqueta = celda.addParagraph();
                etiqueta.setAlignment(ParagraphAlignment.CENTER);
                etiqueta.setSpacingBefore(0);
                etiqueta.setSpacingAfter(0);
                etiqueta.setSpacingBetween(1.0);
                XWPFRun run = etiqueta.createRun();
                configurarRun(run, 9, j == 0);
                run.setText(renglones[j]);
            }
        }

        XWPFParagraph separador = doc.createParagraph();
        separador.setSpacingBefore(0);
        separador.setSpacingAfter(0);
        separador.setSpacingBetween(1.0);
    }

    /** Dibuja cada firma en una columna centrada, con líneas de igual longitud y texto ajustado. */
    private CursorPdf dibujarFirmasPdf(PDDocument doc, List<PDPage> paginas,
            PDPageContentStream stream, List<String> firmas, float y,
            PDType1Font fuente, PDType1Font negrita, float margenIzquierdo, float margenInferior,
            float anchoDisponible) throws IOException {
        int columnas = firmas.size() == 1 ? 1 : 2;
        float anchoColumna = anchoDisponible / columnas;
        for (int inicio = 0; inicio < firmas.size(); inicio += columnas) {
            List<String> fila = firmas.subList(inicio, Math.min(inicio + columnas, firmas.size()));
            List<List<Renglon>> renglones = new ArrayList<>();
            int maxRenglones = 1;
            for (String firma : fila) {
                // Cada renglón de la firma se respeta; el primero (título) va en negrita, como en Word.
                List<Renglon> partes = new ArrayList<>();
                String[] filasFirma = normalizarTextoFirma(firma).split("\\n");
                for (int k = 0; k < filasFirma.length; k++) {
                    if (filasFirma[k].isBlank()) continue;
                    PDType1Font fuenteFila = k == 0 ? negrita : fuente;
                    for (String parte : envolverPorAncho(filasFirma[k], fuenteFila, 9.5f, anchoColumna - 28f)) {
                        partes.add(new Renglon(parte, k == 0));
                    }
                }
                if (partes.isEmpty()) partes.add(new Renglon(" ", false));
                renglones.add(partes);
                maxRenglones = Math.max(maxRenglones, partes.size());
            }
            float altoNecesario = 24f + 15f + maxRenglones * 12f + 14f;
            if (y - altoNecesario < margenInferior + 8f) {
                if (stream != null) stream.close();
                PDPage nueva = new PDPage(PDRectangle.A4);
                doc.addPage(nueva);
                paginas.add(nueva);
                stream = new PDPageContentStream(doc, nueva);
                y = nueva.getMediaBox().getHeight() - 68f;
            }

            y -= 24f;
            float lineaY = y;
            for (int columna = 0; columna < fila.size(); columna++) {
                float inicioX = margenIzquierdo + columna * anchoColumna;
                stream.moveTo(inicioX + 14f, lineaY);
                stream.lineTo(inicioX + anchoColumna - 14f, lineaY);
                stream.stroke();
                float textoY = lineaY - 15f;
                for (Renglon renglon : renglones.get(columna)) {
                    PDType1Font fuenteRenglon = renglon.negrita() ? negrita : fuente;
                    String seguro = textoCompatible(fuenteRenglon, renglon.texto());
                    float anchoTexto = fuenteRenglon.getStringWidth(seguro) / 1000f * 9.5f;
                    float x = inicioX + (anchoColumna - anchoTexto) / 2f;
                    stream.beginText();
                    stream.setFont(fuenteRenglon, 9.5f);
                    stream.newLineAtOffset(x, textoY);
                    stream.showText(seguro);
                    stream.endText();
                    textoY -= 12f;
                }
            }
            y = lineaY - 15f - maxRenglones * 12f - 14f;
        }
        return new CursorPdf(stream, y);
    }

    private String normalizarTextoFirma(String texto) {
        return FirmasEscrito.normalizar(texto);
    }

    private void aplicarEstiloWord(XWPFParagraph p, Bloque bloque) {
        p.setSpacingAfter(0);
        p.setSpacingBefore(0);
        switch (bloque.tipo()) {
            case META -> {
                p.setAlignment(ParagraphAlignment.RIGHT);
                p.setSpacingBetween(1.0);
                p.setSpacingAfter(20);
                p.setKeepNext(true);
            }
            case DESTINATARIO -> {
                p.setAlignment(ParagraphAlignment.LEFT);
                p.setSpacingBetween(1.0);
                p.setSpacingBefore(100);
                p.setSpacingAfter(120);
                p.setKeepNext(true);
            }
            case TITULO -> {
                p.setAlignment(ParagraphAlignment.CENTER);
                p.setSpacingBetween(1.0);
                p.setSpacingBefore(120);
                p.setSpacingAfter(160);
                p.setKeepNext(true);
            }
            case ENCABEZADO -> {
                p.setAlignment(ParagraphAlignment.LEFT);
                p.setSpacingBetween(1.0);
                p.setSpacingBefore(180);
                p.setSpacingAfter(60);
                p.setKeepNext(true);
            }
            case LISTA -> {
                // El artículo 130 CPC exige doble espacio y márgenes; la alineación izquierda
                // evita espacios excesivos entre palabras en las líneas cortas de las listas.
                p.setAlignment(ParagraphAlignment.LEFT);
                p.setSpacingBetween(2.0);
                p.setIndentationLeft(600);
                p.setIndentationHanging(300);
            }
            case FECHA -> {
                p.setAlignment(ParagraphAlignment.RIGHT);
                p.setKeepNext(true);
                p.setSpacingBetween(1.0);
                p.setSpacingBefore(120);
                p.setSpacingAfter(180);
            }
            case FIRMA -> {
                p.setAlignment(ParagraphAlignment.CENTER);
                p.setSpacingBetween(1.0);
                p.setSpacingAfter(20);
            }
            case TEXTO -> {
                // Mantener alineación izquierda: Word y PDF deben presentar el mismo espaciado.
                p.setAlignment(ParagraphAlignment.LEFT);
                p.setSpacingBetween(2.0);
                p.setIndentationFirstLine(360);
            }
        }
    }

    private void agregarTextoFormateado(XWPFParagraph p, String texto, double size) {
        String limpio = texto.replace('“', '\"').replace('”', '\"').replace('’', '\'');
        if (p.getAlignment() == ParagraphAlignment.RIGHT && META.matcher(limpio).find()) {
            int separador = limpio.indexOf(':');
            if (separador > 0) {
                XWPFRun etiqueta = p.createRun();
                configurarRun(etiqueta, size, true);
                etiqueta.setText(limpio.substring(0, separador + 1) + " ");
                XWPFRun valor = p.createRun();
                configurarRun(valor, size, limpio.substring(0, separador).trim().equalsIgnoreCase("SUMILLA"));
                valor.setText(limpio.substring(separador + 1).trim());
                return;
            }
        }
        XWPFRun run = p.createRun();
        boolean bold = p.getAlignment() == ParagraphAlignment.CENTER && esMayuscula(texto)
                || p.getAlignment() == ParagraphAlignment.LEFT
                && (texto.toUpperCase(Locale.ROOT).startsWith("SEÑOR") || esEncabezado(texto));
        configurarRun(run, size, bold);
        run.setText(limpio);
    }

    private void configurarRun(XWPFRun run, double size, boolean bold) {
        run.setFontFamily("Times New Roman");
        run.setFontSize((int) Math.round(size));
        run.setBold(bold);
        run.setColor("000000");
    }

    private void configurarPaginaA4(XWPFDocument doc) {
        CTSectPr section = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(java.math.BigInteger.valueOf(11906));
        size.setH(java.math.BigInteger.valueOf(16838));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(java.math.BigInteger.valueOf(1417));
        margins.setBottom(java.math.BigInteger.valueOf(1417));
        margins.setLeft(java.math.BigInteger.valueOf(1701)); // 3 cm
        margins.setRight(java.math.BigInteger.valueOf(1134)); // 2 cm
    }

    private void agregarPieWord(XWPFDocument doc) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph p = footer.getParagraphs().isEmpty() ? footer.createParagraph() : footer.getParagraphs().get(0);
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBetween(1.0);
        XWPFRun run = p.createRun();
        run.setFontFamily("Times New Roman");
        run.setFontSize(8);
        run.setItalic(true);
        run.setColor("777777");
        run.setText("BORRADOR PARA REVISIÓN PROFESIONAL - LEXIA | PÁGINA ");
        CTSimpleField page = p.getCTP().addNewFldSimple();
        page.setInstr("PAGE");
    }

    private List<String> envolverPorAncho(String texto, PDType1Font fuente, float size, float maxWidth)
            throws IOException {
        List<String> resultado = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split("\\s+")) {
            String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (fuente.getStringWidth(textoCompatible(fuente, candidato)) / 1000f * size <= maxWidth) {
                actual.setLength(0);
                actual.append(candidato);
            } else {
                if (!actual.isEmpty()) resultado.add(actual.toString());
                actual.setLength(0);
                if (fuente.getStringWidth(textoCompatible(fuente, palabra)) / 1000f * size <= maxWidth) {
                    actual.append(palabra);
                } else {
                    StringBuilder fragmento = new StringBuilder();
                    for (char c : palabra.toCharArray()) {
                        String prueba = fragmento.toString() + c;
                        if (fuente.getStringWidth(textoCompatible(fuente, prueba)) / 1000f * size > maxWidth
                                && !fragmento.isEmpty()) {
                            resultado.add(fragmento.toString());
                            fragmento.setLength(0);
                        }
                        fragmento.append(c);
                    }
                    actual.append(fragmento);
                }
            }
        }
        if (!actual.isEmpty()) resultado.add(actual.toString());
        if (resultado.isEmpty()) resultado.add(" ");
        return resultado;
    }

    private String textoCompatible(PDType1Font fuente, String texto) throws IOException {
        String normalizado = texto
                .replace('“', '"').replace('”', '"')
                .replace('‘', '\'').replace('’', '\'')
                .replace('–', '-').replace('—', '-')
                .replace('•', '*').replace('…', '.')
                .replace('º', '°');
        StringBuilder seguro = new StringBuilder(normalizado.length());
        for (int i = 0; i < normalizado.length(); i++) {
            String caracter = String.valueOf(normalizado.charAt(i));
            try {
                fuente.encode(caracter);
                seguro.append(caracter);
            } catch (IllegalArgumentException e) {
                seguro.append('?');
            }
        }
        return seguro.toString();
    }

    private boolean esEncabezado(String linea) {
        if (linea == null || linea.isBlank() || linea.length() > 110) return false;
        String limpia = limpiarMarkdown(linea.trim());
        return SECCION.matcher(limpia).matches()
                || limpia.toUpperCase(Locale.ROOT).startsWith("POR TANTO")
                || limpia.toUpperCase(Locale.ROOT).startsWith("OTROSÍ DIGO")
                || limpia.toUpperCase(Locale.ROOT).startsWith("OTROSI DIGO");
    }

    private String normalizarSaltos(String texto) {
        return texto == null ? "" : texto.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void validar(String titulo, String contenido) {
        if (titulo == null || titulo.isBlank() || contenido == null || contenido.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El título y el contenido del escrito son obligatorios.");
        }
        if (contenido.length() > 100_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El escrito supera el tamaño máximo permitido para exportación.");
        }
    }

    private String nombreSeguro(String titulo, String extension) {
        String base = Normalizer.normalize(titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (base.isBlank()) base = "escrito-lexia";
        if (base.length() > 90) base = base.substring(0, 90);
        return base + "." + extension;
    }
}
