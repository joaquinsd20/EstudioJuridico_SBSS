package com.sbss.lexia.documental.service;

import com.sbss.lexia.documental.entity.DocumentoVersion;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Editor de documentos de LEXIA.
 *
 * V8:
 * - DOCX -> HTML conserva mucho mejor fuentes, tamaños, color, negrita,
 *   cursiva, subrayado, alineación, sangrías, espaciado, tablas e imágenes.
 * - Se reconocen saltos de página y se muestran como hojas A4 independientes.
 * - HTML -> DOCX conserva los mismos estilos y las imágenes incrustadas.
 * - PDF se mantiene como respaldo de texto; DocumentoService usa la conversión
 *   PDF -> DOCX avanzada antes de abrirlo en el editor.
 */
@Service
public class EditorDocumentoService {

    private static final Set<String> EDITABLES = Set.of("docx", "doc", "pdf", "txt");
    private static final long A4_WIDTH_TWIPS = 11906L;
    private static final long A4_HEIGHT_TWIPS = 16838L;
    private static final long MARGIN_TOP_BOTTOM = 1417L; // 2.5 cm
    private static final long MARGIN_LEFT_RIGHT = 1701L; // 3.0 cm

    private final AlmacenamientoDocumentoService almacenamiento;

    public EditorDocumentoService(AlmacenamientoDocumentoService almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    public boolean esEditable(String extension) {
        return extension != null && EDITABLES.contains(extension.toLowerCase(Locale.ROOT));
    }

    public String extraerHtml(DocumentoVersion version) {
        Path archivo = almacenamiento.resolver(version.getRutaAlmacenamiento());
        String ext = version.getExtension() == null ? "" : version.getExtension().toLowerCase(Locale.ROOT);
        try {
            return switch (ext) {
                case "docx" -> docxAHtml(archivo);
                case "doc" -> textoAHtml(extraerDoc(archivo));
                case "pdf" -> pdfAHtmlEditable(archivo);
                case "txt" -> textoAHtml(Files.readString(archivo, StandardCharsets.UTF_8));
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Este formato no se puede editar desde la pagina");
            };
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer el contenido del documento", e);
        }
    }

    /** Permite editar el DOCX temporal creado por el conversor PDF -> Word. */
    public String extraerHtmlDocx(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             XWPFDocument doc = new XWPFDocument(in)) {
            return docxAHtml(doc);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo preparar el documento para editar", e);
        }
    }

    public Path crearDocxDesdeHtml(String html) {
        try {
            Path temporal = Files.createTempFile("lexia-edicion-", ".docx");
            try (XWPFDocument doc = new XWPFDocument();
                 OutputStream out = Files.newOutputStream(temporal)) {

                configurarPaginaA4(doc);
                Element body = Jsoup.parseBodyFragment(html == null ? "" : html).body();
                WriteContext ctx = new WriteContext();

                for (Node node : body.childNodes()) {
                    if (node instanceof Element el && el.hasClass("lexia-page")) {
                        if (ctx.yaEscribioPagina) {
                            ctx.saltoPaginaPendiente = true;
                        }
                        for (Node child : el.childNodes()) {
                            escribirBloque(doc, child, ctx);
                        }
                        ctx.yaEscribioPagina = true;
                    } else {
                        escribirBloque(doc, node, ctx);
                    }
                }

                if (doc.getParagraphs().isEmpty() && doc.getTables().isEmpty()) {
                    doc.createParagraph();
                }
                doc.write(out);
            }
            return temporal;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Word editado", e);
        }
    }

    // =====================================================================
    // DOCX -> HTML AVANZADO
    // =====================================================================

    private String docxAHtml(Path archivo) throws IOException {
        try (InputStream in = Files.newInputStream(archivo);
             XWPFDocument doc = new XWPFDocument(in)) {
            return docxAHtml(doc);
        }
    }

    private String docxAHtml(XWPFDocument doc) {
        StringBuilder html = new StringBuilder(8192);
        int pagina = 1;
        boolean paginaTieneContenido = false;
        html.append("<div class=\"lexia-page\" data-page=\"").append(pagina).append("\">");

        for (IBodyElement elemento : doc.getBodyElements()) {
            if (elemento instanceof XWPFParagraph p) {
                if (saltoPaginaAntes(p) && paginaTieneContenido) {
                    html.append("</div><div class=\"lexia-page\" data-page=\"")
                            .append(++pagina).append("\">");
                    paginaTieneContenido = false;
                }
                html.append(parrafoAHtml(p));
                paginaTieneContenido = true;
            } else if (elemento instanceof XWPFTable tabla) {
                html.append(tablaAHtml(tabla));
                paginaTieneContenido = true;
            }
        }

        html.append("</div>");
        return html.toString();
    }

    private boolean saltoPaginaAntes(XWPFParagraph p) {
        // setPageBreak(true) de POI termina serializado como pageBreakBefore.
        // Buscarlo en el XML evita depender de una API concreta de xmlbeans.
        String xml = p.getCTP().xmlText();
        return xml.contains("pageBreakBefore") || xml.contains("w:type=\"page\"");
    }

    private String parrafoAHtml(XWPFParagraph p) {
        String tag = etiquetaParrafo(p);
        StringBuilder style = new StringBuilder();

        switch (p.getAlignment()) {
            case CENTER -> css(style, "text-align", "center");
            case RIGHT -> css(style, "text-align", "right");
            case BOTH, DISTRIBUTE -> css(style, "text-align", "justify");
            default -> { }
        }

        if (p.getIndentationLeft() > 0) {
            css(style, "margin-left", twipsToPt(p.getIndentationLeft()) + "pt");
        }
        if (p.getIndentationRight() > 0) {
            css(style, "margin-right", twipsToPt(p.getIndentationRight()) + "pt");
        }
        if (p.getIndentationFirstLine() != 0) {
            css(style, "text-indent", twipsToPt(p.getIndentationFirstLine()) + "pt");
        }
        if (p.getSpacingBefore() > 0) {
            css(style, "margin-top", twipsToPt(p.getSpacingBefore()) + "pt");
        }
        if (p.getSpacingAfter() > 0) {
            css(style, "margin-bottom", twipsToPt(p.getSpacingAfter()) + "pt");
        }
        if (p.getSpacingBetween() > 0) {
            css(style, "line-height", String.format(Locale.US, "%.2f", p.getSpacingBetween()));
        }

        StringBuilder h = new StringBuilder("<").append(tag);
        if (!style.isEmpty()) {
            h.append(" style=\"").append(style).append("\"");
        }
        h.append(">");

        if (p.getRuns().isEmpty()) {
            h.append(escapar(p.getText()));
        } else {
            for (XWPFRun r : p.getRuns()) {
                h.append(runAHtml(r));
            }
        }

        if (p.getText().isBlank() && p.getRuns().stream().allMatch(r -> r.getEmbeddedPictures().isEmpty())) {
            h.append("<br>");
        }

        return h.append("</").append(tag).append(">").toString();
    }

    private String etiquetaParrafo(XWPFParagraph p) {
        String style = p.getStyle() == null ? "" : p.getStyle().toLowerCase(Locale.ROOT).replace(" ", "");
        if (style.contains("title") || style.contains("titulo")) return "h1";
        if (style.contains("heading1") || style.contains("ttulo1")) return "h1";
        if (style.contains("heading2") || style.contains("ttulo2")) return "h2";
        if (style.contains("heading3") || style.contains("ttulo3")) return "h3";
        return "p";
    }

    private String runAHtml(XWPFRun r) {
        StringBuilder html = new StringBuilder();
        String texto = r.text();

        if (texto != null && !texto.isEmpty()) {
            StringBuilder style = new StringBuilder();
            String familia = r.getFontFamily();
            if (familia != null && !familia.isBlank()) {
                css(style, "font-family", "'" + escaparAtributo(familia) + "'");
            }
            Double size = r.getFontSizeAsDouble();
            if (size != null && size > 0) {
                css(style, "font-size", cleanNumber(size) + "pt");
            }
            String color = r.getColor();
            if (color != null && color.matches("(?i)[0-9a-f]{6}")) {
                css(style, "color", "#" + color);
            }

            html.append("<span");
            if (!style.isEmpty()) html.append(" style=\"").append(style).append("\"");
            html.append(">");

            String valor = escapar(texto).replace("\t", "&emsp;").replace("\n", "<br>");
            if (r.isBold()) valor = "<strong>" + valor + "</strong>";
            if (r.isItalic()) valor = "<em>" + valor + "</em>";
            if (r.getUnderline() != UnderlinePatterns.NONE) valor = "<u>" + valor + "</u>";
            html.append(valor).append("</span>");
        }

        for (XWPFPicture picture : r.getEmbeddedPictures()) {
            XWPFPictureData data = picture.getPictureData();
            if (data == null || data.getData() == null) continue;
            byte[] bytes = data.getData();
            String ext = extensionImagen(data.getFileName());
            String mime = mimeImagen(ext);
            ImageSize size = obtenerTamanoImagen(bytes);
            double widthPt = picture.getWidth() > 0
                    ? picture.getWidth()
                    : (size.width > 0 ? Math.min(425d, size.width * 0.75d) : 360d);
            double heightPt = picture.getDepth() > 0
                    ? picture.getDepth()
                    : (size.width > 0 && size.height > 0
                        ? widthPt * ((double) size.height / size.width)
                        : 250d);
            html.append("<img class=\"lexia-document-image\" src=\"data:")
                    .append(mime).append(";base64,")
                    .append(Base64.getEncoder().encodeToString(bytes))
                    .append("\" style=\"width:").append(cleanNumber(widthPt)).append("pt;height:auto\"")
                    .append(" data-width-pt=\"").append(cleanNumber(widthPt)).append("\"")
                    .append(" data-height-pt=\"").append(cleanNumber(heightPt)).append("\"")
                    .append(" alt=\"").append(escaparAtributo(data.getFileName())).append("\">");
        }
        return html.toString();
    }

    private String tablaAHtml(XWPFTable tabla) {
        StringBuilder h = new StringBuilder("<table class=\"lexia-doc-table\"><tbody>");
        tabla.getRows().forEach(fila -> {
            h.append("<tr>");
            fila.getTableCells().forEach(celda -> {
                h.append("<td>");
                for (XWPFParagraph p : celda.getParagraphs()) {
                    h.append(parrafoAHtml(p));
                }
                h.append("</td>");
            });
            h.append("</tr>");
        });
        return h.append("</tbody></table>").toString();
    }

    // =====================================================================
    // HTML -> DOCX AVANZADO
    // =====================================================================

    private void escribirBloque(XWPFDocument doc, Node node, WriteContext ctx) {
        if (node instanceof TextNode texto) {
            if (!texto.text().isBlank()) {
                XWPFParagraph p = nuevoParrafo(doc, ctx);
                p.createRun().setText(texto.text());
            }
            return;
        }
        if (!(node instanceof Element el)) return;

        String tag = el.tagName().toLowerCase(Locale.ROOT);
        if ("table".equals(tag)) {
            if (ctx.saltoPaginaPendiente) {
                XWPFParagraph salto = doc.createParagraph();
                salto.setPageBreak(true);
                ctx.saltoPaginaPendiente = false;
            }
            escribirTabla(doc, el);
            return;
        }
        if ("img".equals(tag)) {
            XWPFParagraph p = nuevoParrafo(doc, ctx);
            p.setAlignment(ParagraphAlignment.CENTER);
            insertarImagen(p, el);
            return;
        }
        if ("ul".equals(tag) || "ol".equals(tag)) {
            int i = 1;
            for (Element li : el.children()) {
                if (!"li".equals(li.tagName())) continue;
                XWPFParagraph p = nuevoParrafo(doc, ctx);
                p.createRun().setText("ol".equals(tag) ? (i++) + ". " : "• ");
                escribirInline(p, li, new RunStyle());
            }
            return;
        }
        if (Set.of("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote").contains(tag)) {
            XWPFParagraph p = nuevoParrafo(doc, ctx);
            aplicarEstiloParrafo(p, el, tag);
            escribirInline(p, el, new RunStyle());
            return;
        }
        for (Node child : el.childNodes()) escribirBloque(doc, child, ctx);
    }

    private XWPFParagraph nuevoParrafo(XWPFDocument doc, WriteContext ctx) {
        XWPFParagraph p = doc.createParagraph();
        if (ctx.saltoPaginaPendiente) {
            p.setPageBreak(true);
            ctx.saltoPaginaPendiente = false;
        }
        return p;
    }

    private void aplicarEstiloParrafo(XWPFParagraph p, Element el, String tag) {
        if ("h1".equals(tag)) p.setStyle("Heading1");
        if ("h2".equals(tag)) p.setStyle("Heading2");
        if ("h3".equals(tag)) p.setStyle("Heading3");

        String style = el.attr("style");
        String align = cssValor(style, "text-align");
        if ("center".equals(align)) p.setAlignment(ParagraphAlignment.CENTER);
        else if ("right".equals(align)) p.setAlignment(ParagraphAlignment.RIGHT);
        else if ("justify".equals(align)) p.setAlignment(ParagraphAlignment.BOTH);
        else p.setAlignment(ParagraphAlignment.LEFT);

        Double ml = cssPuntos(style, "margin-left");
        Double mr = cssPuntos(style, "margin-right");
        Double ti = cssPuntos(style, "text-indent");
        Double mt = cssPuntos(style, "margin-top");
        Double mb = cssPuntos(style, "margin-bottom");
        Double lh = cssNumero(style, "line-height");

        if (ml != null) p.setIndentationLeft(ptToTwips(ml));
        if (mr != null) p.setIndentationRight(ptToTwips(mr));
        if (ti != null) p.setIndentationFirstLine(ptToTwips(ti));
        if (mt != null) p.setSpacingBefore(ptToTwips(mt));
        if (mb != null) p.setSpacingAfter(ptToTwips(mb));
        if (lh != null && lh > 0) p.setSpacingBetween(lh);
    }

    private void escribirTabla(XWPFDocument doc, Element tablaHtml) {
        var filas = tablaHtml.select("tr");
        if (filas.isEmpty()) return;
        int columnas = filas.stream().mapToInt(f -> f.select("th,td").size()).max().orElse(1);
        var tabla = doc.createTable(filas.size(), Math.max(1, columnas));
        for (int i = 0; i < filas.size(); i++) {
            var celdas = filas.get(i).select("th,td");
            for (int j = 0; j < Math.min(columnas, celdas.size()); j++) {
                XWPFTableCell cell = tabla.getRow(i).getCell(j);
                cell.removeParagraph(0);
                Element htmlCell = celdas.get(j);
                if (htmlCell.children().isEmpty()) {
                    XWPFParagraph p = cell.addParagraph();
                    p.createRun().setText(htmlCell.text());
                } else {
                    for (Element child : htmlCell.children()) {
                        XWPFParagraph p = cell.addParagraph();
                        aplicarEstiloParrafo(p, child, child.tagName().toLowerCase(Locale.ROOT));
                        escribirInline(p, child, new RunStyle());
                    }
                }
            }
        }
    }

    private void escribirInline(XWPFParagraph p, Node node, RunStyle inherited) {
        if (node instanceof TextNode t) {
            if (t.getWholeText().isEmpty()) return;
            XWPFRun r = p.createRun();
            aplicarRunStyle(r, inherited);
            String texto = t.getWholeText().replace('\u00A0', ' ');
            String[] partes = texto.split("\\n", -1);
            for (int i = 0; i < partes.length; i++) {
                if (i > 0) r.addBreak();
                if (!partes[i].isEmpty()) r.setText(partes[i]);
            }
            return;
        }
        if (!(node instanceof Element el)) return;
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        if ("br".equals(tag)) {
            p.createRun().addBreak();
            return;
        }
        if ("img".equals(tag)) {
            insertarImagen(p, el);
            return;
        }

        RunStyle actual = inherited.copy();
        if ("b".equals(tag) || "strong".equals(tag)) actual.bold = true;
        if ("i".equals(tag) || "em".equals(tag)) actual.italic = true;
        if ("u".equals(tag)) actual.underline = true;

        String style = el.attr("style");
        String weight = cssValor(style, "font-weight");
        String fontStyle = cssValor(style, "font-style");
        String decoration = cssValor(style, "text-decoration");
        String family = cssValor(style, "font-family");
        Double size = cssPuntos(style, "font-size");
        String color = cssValor(style, "color");

        if ("bold".equalsIgnoreCase(weight) || "700".equals(weight) || "800".equals(weight) || "900".equals(weight)) actual.bold = true;
        if ("italic".equalsIgnoreCase(fontStyle)) actual.italic = true;
        if (decoration != null && decoration.contains("underline")) actual.underline = true;
        if (family != null && !family.isBlank()) actual.fontFamily = limpiarCssFont(family);
        if (size != null && size > 0) actual.fontSize = size;
        if (color != null && !color.isBlank()) actual.color = normalizarColor(color);

        for (Node child : el.childNodes()) escribirInline(p, child, actual);
    }

    private void aplicarRunStyle(XWPFRun r, RunStyle s) {
        r.setBold(s.bold);
        r.setItalic(s.italic);
        if (s.underline) r.setUnderline(UnderlinePatterns.SINGLE);
        if (s.fontFamily != null && !s.fontFamily.isBlank()) r.setFontFamily(s.fontFamily);
        if (s.fontSize != null && s.fontSize > 0) r.setFontSize(s.fontSize);
        if (s.color != null && s.color.matches("(?i)[0-9a-f]{6}")) r.setColor(s.color);
    }

    private void insertarImagen(XWPFParagraph p, Element img) {
        String src = img.attr("src");
        if (src == null || !src.startsWith("data:image/")) return;
        int comma = src.indexOf(',');
        if (comma < 0) return;

        try {
            String meta = src.substring(5, comma).toLowerCase(Locale.ROOT);
            byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));
            int pictureType = pictureType(meta);
            Double widthPt = attrDouble(img, "data-width-pt");
            Double heightPt = attrDouble(img, "data-height-pt");

            ImageSize natural = obtenerTamanoImagen(bytes);
            if (widthPt == null || widthPt <= 0) {
                widthPt = natural.width > 0 ? Math.min(425d, natural.width * 0.75d) : 360d;
            }
            if (heightPt == null || heightPt <= 0) {
                heightPt = natural.width > 0 && natural.height > 0
                        ? widthPt * ((double) natural.height / natural.width)
                        : widthPt * 0.70d;
            }

            // No dejar que una imagen desborde el ancho útil de A4.
            if (widthPt > 425d) {
                double ratio = 425d / widthPt;
                widthPt = 425d;
                heightPt *= ratio;
            }

            XWPFRun r = p.createRun();
            r.addPicture(new ByteArrayInputStream(bytes), pictureType, "imagen",
                    Units.toEMU(widthPt), Units.toEMU(heightPt));
        } catch (Exception ignored) {
            // Una imagen dañada no debe impedir guardar el resto del documento.
        }
    }

    // =====================================================================
    // LEGACY / TEXTO
    // =====================================================================

    private String extraerDoc(Path archivo) throws IOException {
        try (InputStream in = Files.newInputStream(archivo);
             HWPFDocument doc = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /**
     * PDF -> HTML para EDICIÓN WEB.
     *
     * Este método es deliberadamente diferente de la exportación PDF -> Word.
     * La exportación puede usar imágenes de página completa para conservar una
     * portada o una tabla exactamente igual. Eso es bueno al exportar, pero es
     * muy malo para editar: las imágenes no se pueden modificar como texto y
     * varios PNG/JPEG grandes incrustados en Base64 pueden congelar el navegador.
     *
     * Aquí priorizamos editabilidad y rendimiento. Reconstruimos cada página a
     * partir de las posiciones reales del texto del PDF, conservando de forma
     * aproximada fuente, tamaño, negrita/cursiva, alineación, sangrías y espacio
     * vertical. No incrustamos capturas completas de página.
     */
    private String pdfAHtmlEditable(Path archivo) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(archivo.toFile())) {
            if (pdf.getNumberOfPages() == 0) {
                return "<div class=\"lexia-page\" data-page=\"1\"><p><br></p></div>";
            }

            StringBuilder html = new StringBuilder(Math.max(8192, pdf.getNumberOfPages() * 4096));

            for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                EditablePdfStripper stripper = new EditablePdfStripper(pageIndex + 1);
                stripper.setSortByPosition(true);
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);
                stripper.getText(pdf);

                float pageWidth = pdf.getPage(pageIndex).getCropBox().getWidth();
                List<PdfTextRun> runs = new ArrayList<>(stripper.runs());
                runs.removeIf(r -> r.text == null || r.text.isBlank());
                runs.sort(Comparator.comparingDouble((PdfTextRun r) -> r.y)
                        .thenComparingDouble(r -> r.x));

                html.append("<div class=\"lexia-page lexia-pdf-editable-page\" data-page=\"")
                        .append(pageIndex + 1)
                        .append("\">");

                if (runs.isEmpty()) {
                    html.append("<p><br></p>");
                } else {
                    List<PdfLine> lines = agruparLineasPdf(runs);
                    double baseX = lines.stream()
                            .mapToDouble(PdfLine::minX)
                            .filter(x -> x >= 0 && x < pageWidth * 0.60)
                            .min()
                            .orElse(0d);

                    PdfLine anterior = null;
                    for (PdfLine line : lines) {
                        html.append(renderLineaPdf(line, anterior, baseX, pageWidth));
                        anterior = line;
                    }
                }

                html.append("</div>");
            }

            return html.toString();
        }
    }

    private static List<PdfLine> agruparLineasPdf(List<PdfTextRun> runs) {
        List<PdfLine> lines = new ArrayList<>();
        PdfLine actual = null;

        for (PdfTextRun run : runs) {
            double tolerance = Math.max(2.3d, run.fontSize * 0.38d);
            if (actual == null || Math.abs(actual.yPromedio() - run.y) > tolerance) {
                actual = new PdfLine();
                lines.add(actual);
            }
            actual.add(run);
        }

        for (PdfLine line : lines) {
            line.runs.sort(Comparator.comparingDouble(r -> r.x));
        }
        return lines;
    }

    private static String renderLineaPdf(
            PdfLine line,
            PdfLine anterior,
            double baseX,
            double pageWidth) {

        StringBuilder style = new StringBuilder();
        double lineWidth = Math.max(1d, line.maxX() - line.minX());
        double center = line.minX() + (lineWidth / 2d);
        boolean centered = lineWidth < pageWidth * 0.90d
                && Math.abs(center - pageWidth / 2d) < Math.max(20d, pageWidth * 0.055d);

        if (centered) {
            css(style, "text-align", "center");
        } else {
            double indent = Math.max(0d, line.minX() - baseX);
            if (indent > 2d) {
                css(style, "margin-left", cleanNumber(Math.min(indent, 220d)) + "pt");
            }
        }

        // Separación vertical aproximada entre líneas/bloques del PDF.
        double marginTop = 0d;
        if (anterior != null) {
            double rawGap = line.yPromedio() - anterior.yPromedio();
            double expected = Math.max(anterior.maxFontSize(), line.maxFontSize()) * 1.12d;
            marginTop = Math.max(0d, rawGap - expected);
            marginTop = Math.min(marginTop, 72d);
        }
        if (marginTop > 1.5d) {
            css(style, "margin-top", cleanNumber(marginTop) + "pt");
        }
        css(style, "margin-bottom", "2pt");
        css(style, "line-height", "1.12");

        StringBuilder out = new StringBuilder("<p");
        if (!style.isEmpty()) out.append(" style=\"").append(style).append("\"");
        out.append(">");

        PdfTextRun previo = null;
        for (PdfTextRun run : line.runs) {
            if (previo != null && necesitaEspacio(previo, run)) {
                out.append(' ');
            }

            StringBuilder runStyle = new StringBuilder();
            if (run.fontSize > 0) {
                css(runStyle, "font-size", cleanNumber(Math.max(7d, Math.min(36d, run.fontSize))) + "pt");
            }
            if (run.fontFamily != null && !run.fontFamily.isBlank()) {
                css(runStyle, "font-family", "'" + escaparAtributo(run.fontFamily) + "'");
            }
            if (run.bold) css(runStyle, "font-weight", "700");
            if (run.italic) css(runStyle, "font-style", "italic");

            out.append("<span");
            if (!runStyle.isEmpty()) out.append(" style=\"").append(runStyle).append("\"");
            out.append(">").append(escapar(run.text)).append("</span>");
            previo = run;
        }

        if (line.runs.isEmpty()) out.append("<br>");
        return out.append("</p>").toString();
    }

    private static boolean necesitaEspacio(PdfTextRun previo, PdfTextRun actual) {
        if (previo.text == null || previo.text.isEmpty() || actual.text == null || actual.text.isEmpty()) return false;
        char last = previo.text.charAt(previo.text.length() - 1);
        char first = actual.text.charAt(0);
        if (Character.isWhitespace(last) || Character.isWhitespace(first)) return false;
        double gap = actual.x - previo.xEnd;
        return gap > Math.max(1.2d, Math.min(previo.fontSize, actual.fontSize) * 0.16d);
    }

    private static String nombreFuentePdf(TextPosition pos) {
        if (pos == null || pos.getFont() == null || pos.getFont().getName() == null) return "Arial";
        String name = pos.getFont().getName();
        int plus = name.indexOf('+');
        if (plus >= 0 && plus + 1 < name.length()) name = name.substring(plus + 1);
        name = name.replace('-', ' ').replace('_', ' ').trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("times")) return "Times New Roman";
        if (lower.contains("helvetica") || lower.contains("arial")) return "Arial";
        if (lower.contains("courier")) return "Courier New";
        if (lower.contains("calibri")) return "Calibri";
        return name.replaceAll("(?i)\\b(bold|italic|oblique|regular|roman|mt|psmt)\\b", "").trim();
    }

    private static boolean esNegritaPdf(String fontName) {
        String s = fontName == null ? "" : fontName.toLowerCase(Locale.ROOT);
        return s.contains("bold") || s.contains("black") || s.contains("semibold")
                || s.contains("demi") || s.contains("heavy");
    }

    private static boolean esCursivaPdf(String fontName) {
        String s = fontName == null ? "" : fontName.toLowerCase(Locale.ROOT);
        return s.contains("italic") || s.contains("oblique");
    }

    private static final class EditablePdfStripper extends PDFTextStripper {
        private final List<PdfTextRun> runs = new ArrayList<>();

        EditablePdfStripper(int page) throws IOException {
            super();
            setStartPage(page);
            setEndPage(page);
            setSortByPosition(true);
        }

        List<PdfTextRun> runs() {
            return runs;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) return;

            TextPosition first = textPositions.get(0);
            TextPosition last = textPositions.get(textPositions.size() - 1);
            double fontSize = textPositions.stream()
                    .mapToDouble(TextPosition::getFontSizeInPt)
                    .filter(v -> v > 0)
                    .average()
                    .orElse(Math.max(10d, first.getFontSizeInPt()));
            String rawFont = first.getFont() == null ? "" : first.getFont().getName();
            double xEnd = last.getXDirAdj() + Math.max(last.getWidthDirAdj(), 0d);

            runs.add(new PdfTextRun(
                    text.replace('\u0000', ' '),
                    first.getXDirAdj(),
                    first.getYDirAdj(),
                    xEnd,
                    fontSize,
                    nombreFuentePdf(first),
                    esNegritaPdf(rawFont),
                    esCursivaPdf(rawFont)));
        }
    }

    private static final class PdfLine {
        private final List<PdfTextRun> runs = new ArrayList<>();

        void add(PdfTextRun run) { runs.add(run); }

        double yPromedio() {
            return runs.stream().mapToDouble(r -> r.y).average().orElse(0d);
        }

        double minX() {
            return runs.stream().mapToDouble(r -> r.x).min().orElse(0d);
        }

        double maxX() {
            return runs.stream().mapToDouble(r -> r.xEnd).max().orElse(minX());
        }

        double maxFontSize() {
            return runs.stream().mapToDouble(r -> r.fontSize).max().orElse(11d);
        }
    }

    private static final class PdfTextRun {
        final String text;
        final double x;
        final double y;
        final double xEnd;
        final double fontSize;
        final String fontFamily;
        final boolean bold;
        final boolean italic;

        PdfTextRun(String text, double x, double y, double xEnd, double fontSize,
                   String fontFamily, boolean bold, boolean italic) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.xEnd = xEnd;
            this.fontSize = fontSize;
            this.fontFamily = fontFamily;
            this.bold = bold;
            this.italic = italic;
        }
    }

    private static String textoAHtml(String texto) {
        if (texto == null || texto.isBlank()) return "<div class=\"lexia-page\"><p><br></p></div>";
        StringBuilder h = new StringBuilder("<div class=\"lexia-page\" data-page=\"1\">");
        String[] parrafos = texto.replace("\r\n", "\n").replace('\r', '\n').split("\\n\\s*\\n");
        for (String p : parrafos) {
            h.append("<p>").append(escapar(p).replace("\n", "<br>")).append("</p>");
        }
        return h.append("</div>").toString();
    }

    // =====================================================================
    // UTILIDADES
    // =====================================================================

    private void configurarPaginaA4(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        size.setW(BigInteger.valueOf(A4_WIDTH_TWIPS));
        size.setH(BigInteger.valueOf(A4_HEIGHT_TWIPS));
        CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        mar.setTop(BigInteger.valueOf(MARGIN_TOP_BOTTOM));
        mar.setBottom(BigInteger.valueOf(MARGIN_TOP_BOTTOM));
        mar.setLeft(BigInteger.valueOf(MARGIN_LEFT_RIGHT));
        mar.setRight(BigInteger.valueOf(MARGIN_LEFT_RIGHT));
    }

    private static void css(StringBuilder style, String key, String value) {
        if (value == null || value.isBlank()) return;
        if (!style.isEmpty()) style.append(';');
        style.append(key).append(':').append(value);
    }

    private static double twipsToPt(int twips) {
        return Math.round((twips / 20d) * 100d) / 100d;
    }

    private static int ptToTwips(double pt) {
        return (int) Math.round(pt * 20d);
    }

    private static String cleanNumber(double n) {
        if (Math.abs(n - Math.rint(n)) < 0.001) return Long.toString(Math.round(n));
        return String.format(Locale.US, "%.2f", n).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String cssValor(String style, String key) {
        if (style == null || style.isBlank()) return null;
        for (String part : style.split(";")) {
            int idx = part.indexOf(':');
            if (idx < 0) continue;
            String k = part.substring(0, idx).trim();
            if (k.equalsIgnoreCase(key)) return part.substring(idx + 1).trim();
        }
        return null;
    }

    private static Double cssPuntos(String style, String key) {
        String value = cssValor(style, key);
        if (value == null || value.isBlank()) return null;
        value = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.endsWith("pt")) return Double.parseDouble(value.substring(0, value.length() - 2).trim());
            if (value.endsWith("px")) return Double.parseDouble(value.substring(0, value.length() - 2).trim()) * 0.75d;
            if (value.endsWith("cm")) return Double.parseDouble(value.substring(0, value.length() - 2).trim()) * 28.3464567d;
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double cssNumero(String style, String key) {
        String value = cssValor(style, key);
        if (value == null) return null;
        try { return Double.parseDouble(value.replaceAll("[^0-9.]", "")); }
        catch (Exception e) { return null; }
    }

    private static String limpiarCssFont(String value) {
        String first = value.split(",")[0].trim();
        return first.replace("'", "").replace("\"", "");
    }

    private static String normalizarColor(String value) {
        String v = value.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.matches("(?i)[0-9a-f]{6}")) return v.toUpperCase(Locale.ROOT);
        return null;
    }

    private static Double attrDouble(Element el, String name) {
        try {
            String v = el.attr(name);
            return v == null || v.isBlank() ? null : Double.parseDouble(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static int pictureType(String meta) {
        if (meta.contains("png")) return Document.PICTURE_TYPE_PNG;
        if (meta.contains("gif")) return Document.PICTURE_TYPE_GIF;
        if (meta.contains("bmp")) return Document.PICTURE_TYPE_BMP;
        if (meta.contains("tiff")) return Document.PICTURE_TYPE_TIFF;
        return Document.PICTURE_TYPE_JPEG;
    }

    private static String extensionImagen(String name) {
        if (name == null) return "png";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "png";
    }

    private static String mimeImagen(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            default -> "image/png";
        };
    }

    private static ImageSize obtenerTamanoImagen(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            return image == null ? new ImageSize(0, 0) : new ImageSize(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            return new ImageSize(0, 0);
        }
    }

    private static String escapar(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escaparAtributo(String valor) {
        return escapar(valor == null ? "" : valor).replace("'", "&#39;");
    }

    private static final class WriteContext {
        boolean saltoPaginaPendiente;
        boolean yaEscribioPagina;
    }

    private static final class RunStyle {
        boolean bold;
        boolean italic;
        boolean underline;
        String fontFamily;
        Double fontSize;
        String color;

        RunStyle copy() {
            RunStyle x = new RunStyle();
            x.bold = bold;
            x.italic = italic;
            x.underline = underline;
            x.fontFamily = fontFamily;
            x.fontSize = fontSize;
            x.color = color;
            return x;
        }
    }

    private record ImageSize(int width, int height) { }
}
