package com.sbss.lexia.documental.service;

import com.sbss.lexia.documental.entity.DocumentoVersion;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exporta documentos entre formatos.
 *
 * La conversión PDF -> DOCX usa un modo híbrido:
 * - páginas simples: reconstrucción editable conservando posición, fuente, tamaño,
 *   negrita/cursiva, alineación, sangrías y espaciado;
 * - páginas complejas (portadas, tablas dibujadas, gráficos, escaneos): se conserva
 *   la fidelidad visual renderizando esa página como imagen de alta resolución.
 *
 * Esto evita el problema de la conversión anterior que solo extraía texto con
 * PDFTextStripper y perdía prácticamente todo el formato.
 */
@Service
public class ExportacionDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(ExportacionDocumentoService.class);

    // Calidad para páginas complejas convertidas como imagen.
    // 180 DPI ofrece muy buena nitidez sin generar DOCX excesivamente pesados.
    private static final float PDF_FIDELITY_DPI = 180f;

    // A partir de este número de trazos consideramos que hay tablas/diagramas complejos.
    private static final int COMPLEX_PATH_THRESHOLD = 28;

    // Si las imágenes ocupan más del 18% de la página, priorizamos fidelidad visual.
    private static final double LARGE_IMAGE_AREA_RATIO = 0.18d;

    // Márgenes del DOCX reconstruido. 0 conserva mejor las coordenadas del PDF.
    private static final long WORD_MARGIN_TWIPS = 0L;

    private final AlmacenamientoDocumentoService almacenamiento;
    private final EditorDocumentoService editor;

    public ExportacionDocumentoService(
            AlmacenamientoDocumentoService almacenamiento,
            EditorDocumentoService editor) {
        this.almacenamiento = almacenamiento;
        this.editor = editor;
    }

    public ArchivoExportado exportar(DocumentoVersion version, String formato, String nombreBase) {
        String f = formato == null ? "" : formato.toLowerCase(Locale.ROOT);
        return switch (f) {
            case "pdf" -> exportarPdf(version, nombreBase);
            case "docx", "word" -> exportarWord(version, nombreBase);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de exportacion invalido. Usa pdf o docx");
        };
    }

    /**
     * Convierte un Word recibido en la carga (DOC/DOCX) a PDF antes de que se
     * almacene como versión documental. La validación real del contenedor Word
     * la hace Apache POI; renombrar otro archivo a .doc/.docx no es suficiente.
     */
    public byte[] convertirWordSubidoAPdf(MultipartFile archivo, String extension) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un archivo");
        }
        String ext = safeExtension(extension);
        try (InputStream in = archivo.getInputStream()) {
            String texto = switch (ext) {
                case "docx" -> {
                    try (XWPFDocument doc = new XWPFDocument(in);
                         XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                        yield extractor.getText();
                    }
                }
                case "doc" -> {
                    try (HWPFDocument doc = new HWPFDocument(in);
                         WordExtractor extractor = new WordExtractor(doc)) {
                        yield extractor.getText();
                    }
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo se puede convertir Word DOC o DOCX");
            };
            return textoAPdf(texto);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo Word no es válido o está dañado", e);
        }
    }

    /**
     * Convierte una imagen subida (JPG/JPEG/PNG) a PDF. ImageIO valida el
     * contenido real del archivo, por lo que cambiar solamente la extensión
     * de otro archivo no permite superar la validación.
     */
    public byte[] convertirImagenSubidaAPdf(MultipartFile archivo, String extension) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes seleccionar un archivo");
        }

        String ext = safeExtension(extension);
        if (!esImagen(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se puede convertir JPG, JPEG o PNG");
        }

        try (InputStream in = archivo.getInputStream()) {
            BufferedImage imagen = ImageIO.read(in);
            if (imagen == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La imagen no es válida o está dañada");
            }
            return imagenAPdf(imagen);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo procesar la imagen", e);
        }
    }

    private ArchivoExportado exportarWord(DocumentoVersion version, String nombreBase) {
        Path origen = almacenamiento.resolver(version.getRutaAlmacenamiento());
        String ext = safeExtension(version.getExtension());
        validarExportable(ext);

        try {
            byte[] bytes;

            if ("docx".equals(ext)) {
                // Si ya es DOCX se conserva el original sin recomponerlo.
                bytes = Files.readAllBytes(origen);
            } else if ("pdf".equals(ext)) {
                // Conversión avanzada PDF -> Word.
                bytes = pdfADocxAvanzado(origen);
            } else if (esImagen(ext)) {
                bytes = imagenADocx(origen, ext);
            } else {
                String html = editor.extraerHtml(version);
                Path temp = editor.crearDocxDesdeHtml(html);
                try {
                    bytes = Files.readAllBytes(temp);
                } finally {
                    Files.deleteIfExists(temp);
                }
            }

            return new ArchivoExportado(
                    nombreArchivo(nombreBase, "docx"),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo exportar el documento a Word", e);
        }
    }

    private ArchivoExportado exportarPdf(DocumentoVersion version, String nombreBase) {
        Path origen = almacenamiento.resolver(version.getRutaAlmacenamiento());
        String ext = safeExtension(version.getExtension());
        validarExportable(ext);
        try {
            byte[] bytes;
            if ("pdf".equals(ext)) {
                bytes = Files.readAllBytes(origen);
            } else if (esImagen(ext)) {
                bytes = imagenAPdf(origen);
            } else {
                bytes = textoAPdf(extraerTexto(version, origen, ext));
            }
            return new ArchivoExportado(nombreArchivo(nombreBase, "pdf"), "application/pdf", bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo exportar el documento a PDF", e);
        }
    }

    // =====================================================================
    // PDF -> DOCX AVANZADO
    // =====================================================================

    /**
     * Convierte un PDF a DOCX preservando al máximo el aspecto visual sin perder
     * editabilidad en las páginas normales de texto.
     */
    private byte[] pdfADocxAvanzado(Path pdfPath) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile());
             XWPFDocument word = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            if (pdf.getNumberOfPages() == 0) {
                word.createParagraph().createRun().setText("Documento PDF vacío.");
                word.write(out);
                return out.toByteArray();
            }

            PDPage primeraPagina = pdf.getPage(0);
            configurarPaginaWord(word, primeraPagina);

            PDFRenderer renderer = new PDFRenderer(pdf);
            PositionedTextStripper textStripper = new PositionedTextStripper();

            for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                PDPage page = pdf.getPage(pageIndex);
                float pageWidth = anchoVisual(page);
                float pageHeight = altoVisual(page);

                List<TextToken> tokens = textStripper.extractPage(pdf, pageIndex);
                PageGraphicsScanner graphics = new PageGraphicsScanner(page);
                graphics.processPage(page);

                boolean paginaCompleja = esPaginaCompleja(page, tokens, graphics, pageWidth, pageHeight);

                if (paginaCompleja) {
                    insertarPaginaComoImagen(word, renderer, pageIndex, pageWidth, pageHeight, pageIndex > 0);
                } else {
                    insertarPaginaEditable(word, tokens, graphics.images, pageWidth, pageHeight, pageIndex > 0);
                }
            }

            word.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            // No dejamos al usuario sin exportación si un PDF extraño rompe el modo avanzado.
            log.warn("Falló conversión PDF->DOCX avanzada. Se usará conversión de respaldo: {}",
                    e.getMessage());
            return pdfADocxRespaldo(pdfPath);
        }
    }

    /**
     * Decide cuándo es mejor sacrificar editabilidad de una página concreta para
     * conservar su apariencia exactamente (portadas, diagramas, tablas gráficas,
     * escaneos, páginas rotadas, etc.).
     */
    private boolean esPaginaCompleja(
            PDPage page,
            List<TextToken> tokens,
            PageGraphicsScanner graphics,
            float pageWidth,
            float pageHeight) {

        if (page.getRotation() % 360 != 0) {
            return true;
        }

        int caracteres = tokens.stream()
                .map(TextToken::text)
                .filter(Objects::nonNull)
                .mapToInt(String::length)
                .sum();

        long rotados = tokens.stream().filter(t -> Math.abs(t.direction) > 1f).count();
        if (!tokens.isEmpty() && rotados > Math.max(2, tokens.size() / 50)) {
            return true;
        }

        double pageArea = Math.max(1d, (double) pageWidth * pageHeight);
        double imageAreaRatio = Math.min(1d, graphics.totalImageArea / pageArea);

        boolean scanOImagen = caracteres < 20 && !graphics.images.isEmpty();
        boolean imagenGrande = imageAreaRatio >= LARGE_IMAGE_AREA_RATIO;
        boolean vectoresComplejos = graphics.pathOperations >= COMPLEX_PATH_THRESHOLD;

        return scanOImagen || imagenGrande || vectoresComplejos;
    }

    /**
     * Página compleja -> imagen completa a alta resolución.
     * Es la forma de conservar logos, figuras, bordes, tablas, diagramas y portada
     * prácticamente idénticos al PDF.
     */
    private void insertarPaginaComoImagen(
            XWPFDocument word,
            PDFRenderer renderer,
            int pageIndex,
            float pageWidth,
            float pageHeight,
            boolean pageBreak) throws Exception {

        BufferedImage rendered = renderer.renderImageWithDPI(pageIndex, PDF_FIDELITY_DPI, ImageType.RGB);

        try (ByteArrayOutputStream imgOut = new ByteArrayOutputStream()) {
            ImageIO.write(rendered, "png", imgOut);

            XWPFParagraph p = word.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            p.setSpacingBefore(0);
            p.setSpacingAfter(0);
            if (pageBreak) {
                p.setPageBreak(true);
            }

            // Se reduce apenas 0.5% para evitar que Word empuje la imagen a una
            // segunda página por el alto interno del párrafo. Visualmente es imperceptible.
            float scale = 0.995f;
            XWPFRun run = p.createRun();
            run.addPicture(
                    new ByteArrayInputStream(imgOut.toByteArray()),
                    Document.PICTURE_TYPE_PNG,
                    "pagina-" + (pageIndex + 1) + ".png",
                    Units.toEMU(pageWidth * scale),
                    Units.toEMU(pageHeight * scale));
        }
    }

    /**
     * Página normal -> reconstrucción editable.
     * Se usan las coordenadas del PDF para aproximar el layout dentro de Word.
     */
    private void insertarPaginaEditable(
            XWPFDocument word,
            List<TextToken> tokens,
            List<PlacedImage> images,
            float pageWidth,
            float pageHeight,
            boolean pageBreak) throws Exception {

        List<TextLine> lines = agruparLineas(tokens);
        List<PageElement> elements = new ArrayList<>();

        for (TextLine line : lines) {
            elements.add(PageElement.text(line.y, line));
        }
        for (PlacedImage image : images) {
            // Ignorar miniaturas decorativas microscópicas.
            if (image.width >= 8f && image.height >= 8f) {
                elements.add(PageElement.image(image.top, image));
            }
        }

        elements.sort(Comparator.comparingDouble(PageElement::y));

        if (elements.isEmpty()) {
            XWPFParagraph p = word.createParagraph();
            if (pageBreak) p.setPageBreak(true);
            return;
        }

        float cursorY = 0f;
        boolean firstElement = true;

        for (PageElement element : elements) {
            if (element.line != null) {
                TextLine line = element.line;
                XWPFParagraph p = word.createParagraph();
                if (pageBreak && firstElement) {
                    p.setPageBreak(true);
                }

                configurarParrafoDesdeLinea(p, line, pageWidth, cursorY);
                escribirRuns(p, line);

                cursorY = Math.max(cursorY, line.y + Math.max(line.maxFontSize, line.height));
            } else if (element.image != null) {
                PlacedImage image = element.image;
                XWPFParagraph p = word.createParagraph();
                if (pageBreak && firstElement) {
                    p.setPageBreak(true);
                }

                float gap = Math.max(0f, image.top - cursorY);
                p.setSpacingBefore(pointsToTwips(gap));
                p.setSpacingAfter(0);

                ParagraphAlignment alignment = alignmentForImage(image, pageWidth);
                p.setAlignment(alignment);

                if (alignment == ParagraphAlignment.LEFT) {
                    p.setIndentationLeft(pointsToTwips(Math.max(0f, image.x)));
                }

                float safeWidth = Math.min(image.width, pageWidth);
                float safeHeight = image.height;
                if (image.width > pageWidth && image.width > 0f) {
                    float scale = pageWidth / image.width;
                    safeHeight *= scale;
                }

                XWPFRun run = p.createRun();
                run.addPicture(
                        new ByteArrayInputStream(image.pngBytes),
                        Document.PICTURE_TYPE_PNG,
                        image.name,
                        Units.toEMU(safeWidth),
                        Units.toEMU(safeHeight));

                cursorY = Math.max(cursorY, image.top + safeHeight);
            }

            firstElement = false;
        }
    }

    private void configurarParrafoDesdeLinea(
            XWPFParagraph p,
            TextLine line,
            float pageWidth,
            float cursorY) {

        p.setSpacingAfter(0);
        p.setSpacingBetween(1.0);

        float gap = Math.max(0f, line.y - cursorY);
        // Evita acumular microespacios que Word ya aporta por la altura de la fuente.
        float extraGap = Math.max(0f, gap - Math.max(1f, line.maxFontSize * 0.10f));
        p.setSpacingBefore(pointsToTwips(extraGap));

        float center = (line.minX + line.maxX) / 2f;
        float pageCenter = pageWidth / 2f;
        float lineWidth = Math.max(1f, line.maxX - line.minX);

        boolean shortLine = lineWidth < pageWidth * 0.72f;
        boolean centered = shortLine && Math.abs(center - pageCenter) <= Math.max(16f, pageWidth * 0.055f);
        boolean rightAligned = line.minX > pageWidth * 0.50f && line.maxX > pageWidth * 0.82f;

        if (centered) {
            p.setAlignment(ParagraphAlignment.CENTER);
        } else if (rightAligned) {
            p.setAlignment(ParagraphAlignment.RIGHT);
        } else {
            p.setAlignment(ParagraphAlignment.LEFT);
            p.setIndentationLeft(pointsToTwips(Math.max(0f, line.minX)));
        }
    }

    /**
     * Reconstruye runs para no perder negrita, cursiva, familia ni tamaño de letra.
     */
    private void escribirRuns(XWPFParagraph p, TextLine line) {
        TextStyle currentStyle = null;
        StringBuilder buffer = new StringBuilder();

        TextToken previous = null;
        for (TextToken token : line.tokens) {
            String text = limpiarTexto(token.text);
            if (text.isEmpty()) {
                previous = token;
                continue;
            }

            TextStyle style = styleOf(token);

            String prefix = "";
            if (previous != null) {
                float gap = token.x - (previous.x + previous.width);
                float expected = Math.max(1.2f, Math.min(previous.fontSize, token.fontSize) * 0.16f);
                boolean alreadySeparated = endsWithWhitespace(buffer) || startsWithWhitespace(text);
                if (gap > expected && !alreadySeparated) {
                    prefix = " ";
                }
            }

            if (currentStyle != null && !currentStyle.equals(style)) {
                flushRun(p, buffer.toString(), currentStyle);
                buffer.setLength(0);
            }

            currentStyle = style;
            buffer.append(prefix).append(text);
            previous = token;
        }

        if (currentStyle != null && !buffer.isEmpty()) {
            flushRun(p, buffer.toString(), currentStyle);
        }
    }

    private void flushRun(XWPFParagraph p, String text, TextStyle style) {
        if (text == null || text.isEmpty()) return;

        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(style.bold);
        run.setItalic(style.italic);
        run.setFontFamily(style.fontFamily);
        run.setFontSize(Math.max(6, Math.min(72, Math.round(style.fontSize))));
    }

    private List<TextLine> agruparLineas(List<TextToken> tokens) {
        List<TextToken> sorted = tokens.stream()
                .filter(t -> t.text != null && !t.text.isBlank())
                .sorted(Comparator
                        .comparingDouble((TextToken t) -> t.y)
                        .thenComparingDouble(t -> t.x))
                .toList();

        List<TextLine> lines = new ArrayList<>();

        for (TextToken token : sorted) {
            TextLine best = null;
            float bestDistance = Float.MAX_VALUE;

            // Buscar desde el final: normalmente la línea correcta es una de las últimas.
            for (int i = lines.size() - 1; i >= 0 && i >= lines.size() - 6; i--) {
                TextLine candidate = lines.get(i);
                float tolerance = Math.max(2.2f,
                        Math.max(candidate.maxFontSize, token.fontSize) * 0.38f);
                float distance = Math.abs(candidate.y - token.y);
                if (distance <= tolerance && distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }

            if (best == null) {
                best = new TextLine();
                best.y = token.y;
                lines.add(best);
            }

            best.add(token);
        }

        for (TextLine line : lines) {
            line.tokens.sort(Comparator.comparingDouble(t -> t.x));
            line.finish();
        }

        lines.sort(Comparator.comparingDouble(l -> l.y));
        return lines;
    }

    /**
     * Respaldo simple para PDFs muy raros/corruptos. Mantiene la función disponible
     * aunque el reconstruidor avanzado no pueda interpretar un contenido especial.
     */
    private byte[] pdfADocxRespaldo(Path pdfPath) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile());
             XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(pdf);

            for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(0);
                XWPFRun r = p.createRun();
                r.setFontFamily("Arial");
                r.setFontSize(11);
                r.setText(line.isEmpty() ? " " : line);
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    private void configurarPaginaWord(XWPFDocument word, PDPage page) {
        float width = anchoVisual(page);
        float height = altoVisual(page);

        CTSectPr sectPr = word.getDocument().getBody().isSetSectPr()
                ? word.getDocument().getBody().getSectPr()
                : word.getDocument().getBody().addNewSectPr();

        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(pointsToTwipsLong(width)));
        pageSize.setH(BigInteger.valueOf(pointsToTwipsLong(height)));

        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        BigInteger margin = BigInteger.valueOf(WORD_MARGIN_TWIPS);
        pageMar.setTop(margin);
        pageMar.setBottom(margin);
        pageMar.setLeft(margin);
        pageMar.setRight(margin);
        pageMar.setHeader(BigInteger.ZERO);
        pageMar.setFooter(BigInteger.ZERO);
        pageMar.setGutter(BigInteger.ZERO);
    }

    // =====================================================================
    // EXTRACCIÓN DE TEXTO POSICIONADO
    // =====================================================================

    private static final class PositionedTextStripper extends PDFTextStripper {
        private final List<TextToken> positions = new ArrayList<>();

        PositionedTextStripper() throws IOException {
            setSortByPosition(true);
            setShouldSeparateByBeads(false);
        }

        List<TextToken> extractPage(PDDocument pdf, int pageIndex) throws IOException {
            positions.clear();
            setStartPage(pageIndex + 1);
            setEndPage(pageIndex + 1);
            getText(pdf);
            return new ArrayList<>(positions);
        }

        @Override
        protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
            // writeString recibe las posiciones después del procesamiento interno de
            // PDFTextStripper, evitando buena parte de los glifos superpuestos/duplicados.
            for (TextPosition text : textPositions) {
                String unicode = text.getUnicode();
                if (unicode == null || unicode.isEmpty()) continue;

                String fontName = text.getFont() == null ? "Arial" : text.getFont().getName();
                positions.add(new TextToken(
                        unicode,
                        text.getXDirAdj(),
                        text.getYDirAdj(),
                        Math.max(0.1f, text.getWidthDirAdj()),
                        Math.max(0.1f, text.getHeightDir()),
                        Math.max(1f, text.getFontSizeInPt()),
                        fontName,
                        text.getDir()));
            }
        }
    }

    // =====================================================================
    // ANÁLISIS DE IMÁGENES Y COMPLEJIDAD GRÁFICA
    // =====================================================================

    /**
     * Recorre el content stream real del PDF. Captura posición y tamaño de imágenes
     * y cuenta trazos para detectar tablas/diagramas que sería peligroso reconstruir
     * solo con texto.
     */
    private static final class PageGraphicsScanner extends PDFGraphicsStreamEngine {
        private final List<PlacedImage> images = new ArrayList<>();
        private final float pageHeight;
        private int pathOperations = 0;
        private double totalImageArea = 0d;
        private Point2D currentPoint;
        private int imageCounter = 0;

        PageGraphicsScanner(PDPage page) {
            super(page);
            this.pageHeight = page.getCropBox().getHeight();
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();

            Point2D p0 = ctm.transformPoint(0, 0);
            Point2D p1 = ctm.transformPoint(1, 0);
            Point2D p2 = ctm.transformPoint(0, 1);
            Point2D p3 = ctm.transformPoint(1, 1);

            double minX = Math.min(Math.min(p0.getX(), p1.getX()), Math.min(p2.getX(), p3.getX()));
            double maxX = Math.max(Math.max(p0.getX(), p1.getX()), Math.max(p2.getX(), p3.getX()));
            double minY = Math.min(Math.min(p0.getY(), p1.getY()), Math.min(p2.getY(), p3.getY()));
            double maxY = Math.max(Math.max(p0.getY(), p1.getY()), Math.max(p2.getY(), p3.getY()));

            float width = (float) Math.max(1d, maxX - minX);
            float height = (float) Math.max(1d, maxY - minY);
            float x = (float) minX;
            float top = (float) Math.max(0d, pageHeight - maxY);

            BufferedImage buffered = pdImage.getImage();
            if (buffered != null) {
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    ImageIO.write(buffered, "png", out);
                    images.add(new PlacedImage(
                            x,
                            top,
                            width,
                            height,
                            out.toByteArray(),
                            "pdf-image-" + (++imageCounter) + ".png"));
                }
            }

            totalImageArea += (double) width * height;
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            pathOperations += 4;
            currentPoint = p3;
        }

        @Override
        public void clip(int windingRule) {
            // No necesitamos reconstruir clipping; solo medir complejidad.
        }

        @Override
        public void moveTo(float x, float y) {
            currentPoint = new Point2D.Float(x, y);
        }

        @Override
        public void lineTo(float x, float y) {
            pathOperations++;
            currentPoint = new Point2D.Float(x, y);
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            pathOperations += 2;
            currentPoint = new Point2D.Float(x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return currentPoint;
        }

        @Override
        public void closePath() {
            pathOperations++;
        }

        @Override
        public void endPath() {
            currentPoint = null;
        }

        @Override
        public void strokePath() {
            pathOperations++;
        }

        @Override
        public void fillPath(int windingRule) {
            pathOperations++;
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            pathOperations += 2;
        }

        @Override
        public void shadingFill(COSName shadingName) {
            pathOperations += 8;
        }
    }

    // =====================================================================
    // MODELOS INTERNOS
    // =====================================================================

    private static final class TextLine {
        private final List<TextToken> tokens = new ArrayList<>();
        private float y;
        private float minX = Float.MAX_VALUE;
        private float maxX = 0f;
        private float height = 0f;
        private float maxFontSize = 0f;

        void add(TextToken token) {
            tokens.add(token);
            if (tokens.size() == 1) {
                y = token.y;
            } else {
                // Promedio incremental para estabilizar ligeras diferencias de baseline.
                y = (y * (tokens.size() - 1) + token.y) / tokens.size();
            }
            minX = Math.min(minX, token.x);
            maxX = Math.max(maxX, token.x + token.width);
            height = Math.max(height, token.height);
            maxFontSize = Math.max(maxFontSize, token.fontSize);
        }

        void finish() {
            if (minX == Float.MAX_VALUE) minX = 0f;
        }
    }

    private record TextToken(
            String text,
            float x,
            float y,
            float width,
            float height,
            float fontSize,
            String fontName,
            float direction) {
    }

    private record TextStyle(String fontFamily, float fontSize, boolean bold, boolean italic) {
    }

    private record PlacedImage(
            float x,
            float top,
            float width,
            float height,
            byte[] pngBytes,
            String name) {
    }

    private static final class PageElement {
        private final float y;
        private final TextLine line;
        private final PlacedImage image;

        private PageElement(float y, TextLine line, PlacedImage image) {
            this.y = y;
            this.line = line;
            this.image = image;
        }

        static PageElement text(float y, TextLine line) {
            return new PageElement(y, line, null);
        }

        static PageElement image(float y, PlacedImage image) {
            return new PageElement(y, null, image);
        }

        float y() {
            return y;
        }
    }

    // =====================================================================
    // UTILIDADES DE ESTILO / CONVERSIÓN
    // =====================================================================

    private static TextStyle styleOf(TextToken token) {
        String raw = token.fontName == null ? "Arial" : token.fontName;
        String lower = raw.toLowerCase(Locale.ROOT);

        boolean bold = containsAny(lower,
                "bold", "black", "demi", "semibold", "semi-bold", "extrabold", "heavy");
        boolean italic = containsAny(lower,
                "italic", "oblique", "slanted");

        String family = normalizarFamiliaFuente(raw);
        return new TextStyle(family, token.fontSize, bold, italic);
    }

    private static String normalizarFamiliaFuente(String raw) {
        if (raw == null || raw.isBlank()) return "Arial";

        String name = raw;
        int plus = name.indexOf('+');
        if (plus >= 0 && plus + 1 < name.length()) {
            name = name.substring(plus + 1);
        }

        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("arial") || lower.contains("helvetica")) return "Arial";
        if (lower.contains("times") || lower.contains("timesnewroman")) return "Times New Roman";
        if (lower.contains("calibri")) return "Calibri";
        if (lower.contains("cambria")) return "Cambria";
        if (lower.contains("courier")) return "Courier New";
        if (lower.contains("verdana")) return "Verdana";
        if (lower.contains("tahoma")) return "Tahoma";
        if (lower.contains("georgia")) return "Georgia";

        name = name.replaceAll("(?i)(-?bold|-?italic|-?oblique|-?regular|-?roman|-?medium|-?semibold)$", "");
        name = name.replaceAll("(?i)(PSMT|MT)$", "");
        name = name.replace('_', ' ').trim();
        return name.isBlank() ? "Arial" : name;
    }

    private static ParagraphAlignment alignmentForImage(PlacedImage image, float pageWidth) {
        float center = image.x + image.width / 2f;
        if (Math.abs(center - pageWidth / 2f) < pageWidth * 0.08f) {
            return ParagraphAlignment.CENTER;
        }
        if (image.x > pageWidth * 0.55f) {
            return ParagraphAlignment.RIGHT;
        }
        return ParagraphAlignment.LEFT;
    }

    private static String limpiarTexto(String text) {
        if (text == null) return "";
        return text
                .replace('\u0000', ' ')
                .replace('\u00A0', ' ')
                .replaceAll("[\\p{Cc}&&[^\\t]]", "");
    }

    private static boolean endsWithWhitespace(StringBuilder sb) {
        return sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1));
    }

    private static boolean startsWithWhitespace(String value) {
        return value != null && !value.isEmpty() && Character.isWhitespace(value.charAt(0));
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    private static float anchoVisual(PDPage page) {
        PDRectangle box = page.getCropBox();
        int rotation = Math.floorMod(page.getRotation(), 360);
        return (rotation == 90 || rotation == 270) ? box.getHeight() : box.getWidth();
    }

    private static float altoVisual(PDPage page) {
        PDRectangle box = page.getCropBox();
        int rotation = Math.floorMod(page.getRotation(), 360);
        return (rotation == 90 || rotation == 270) ? box.getWidth() : box.getHeight();
    }

    private static int pointsToTwips(float points) {
        return Math.max(0, Math.round(points * 20f));
    }

    private static long pointsToTwipsLong(float points) {
        return Math.max(1L, Math.round(points * 20d));
    }

    private static String safeExtension(String ext) {
        return ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT).replace(".", "");
    }

    // =====================================================================
    // CONVERSIONES EXISTENTES
    // =====================================================================

    private String extraerTexto(DocumentoVersion version, Path origen, String ext) throws IOException {
        return switch (ext) {
            case "docx" -> {
                try (InputStream in = Files.newInputStream(origen); XWPFDocument doc = new XWPFDocument(in)) {
                    yield doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + b + "\n");
                }
            }
            case "doc" -> {
                try (InputStream in = Files.newInputStream(origen);
                     HWPFDocument doc = new HWPFDocument(in);
                     WordExtractor extractor = new WordExtractor(doc)) {
                    yield extractor.getText();
                }
            }
            case "txt" -> Files.readString(origen, StandardCharsets.UTF_8);
            case "pdf" -> {
                try (PDDocument pdf = Loader.loadPDF(origen.toFile())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    yield stripper.getText(pdf);
                }
            }
            default -> editor.extraerHtml(version).replaceAll("<[^>]+>", " ");
        };
    }

    private byte[] imagenADocx(Path imagen, String ext) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             InputStream in = Files.newInputStream(imagen)) {
            var p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            var run = p.createRun();
            int tipo = "png".equals(ext) ? Document.PICTURE_TYPE_PNG : Document.PICTURE_TYPE_JPEG;
            run.addPicture(in, tipo, imagen.getFileName().toString(), Units.toEMU(450), Units.toEMU(600));
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            if (e instanceof IOException io) throw io;
            throw new IOException("No se pudo insertar la imagen en Word", e);
        }
    }

    private byte[] imagenAPdf(Path imagen) throws IOException {
        BufferedImage buffered = ImageIO.read(imagen.toFile());
        if (buffered == null) throw new IOException("Imagen no reconocida");
        return imagenAPdf(buffered);
    }

    private byte[] imagenAPdf(BufferedImage buffered) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDRectangle paginaTam = PDRectangle.A4;
            PDPage pagina = new PDPage(paginaTam);
            doc.addPage(pagina);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, buffered);
            float margen = 36f;
            float maxW = paginaTam.getWidth() - margen * 2;
            float maxH = paginaTam.getHeight() - margen * 2;
            float escala = Math.min(maxW / buffered.getWidth(), maxH / buffered.getHeight());
            float w = buffered.getWidth() * escala;
            float h = buffered.getHeight() * escala;
            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                cs.drawImage(pdImage, (paginaTam.getWidth() - w) / 2, (paginaTam.getHeight() - h) / 2, w, h);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] textoAPdf(String texto) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 11f;
            float margen = 50f;
            float leading = 15f;
            List<String> lineas = envolver(texto == null ? "" : texto, 92);

            PDPage pagina = null;
            PDPageContentStream cs = null;
            float y = 0;
            try {
                for (String linea : lineas) {
                    if (pagina == null || y < margen + leading) {
                        if (cs != null) {
                            cs.endText();
                            cs.close();
                        }
                        pagina = new PDPage(PDRectangle.A4);
                        doc.addPage(pagina);
                        cs = new PDPageContentStream(doc, pagina);
                        cs.beginText();
                        cs.setFont(font, fontSize);
                        cs.newLineAtOffset(margen, pagina.getMediaBox().getHeight() - margen);
                        y = pagina.getMediaBox().getHeight() - margen;
                    }
                    cs.showText(textoCompatible(font, linea));
                    cs.newLineAtOffset(0, -leading);
                    y -= leading;
                }
            } finally {
                if (cs != null) {
                    try { cs.endText(); } finally { cs.close(); }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> envolver(String texto, int max) {
        List<String> r = new ArrayList<>();
        String normal = texto.replace("\r\n", "\n").replace('\r', '\n');
        for (String original : normal.split("\n", -1)) {
            String linea = original.trim();
            if (linea.isEmpty()) {
                r.add(" ");
                continue;
            }
            while (linea.length() > max) {
                int corte = linea.lastIndexOf(' ', max);
                if (corte < max / 2) corte = max;
                r.add(linea.substring(0, corte).trim());
                linea = linea.substring(corte).trim();
            }
            r.add(linea);
        }
        if (r.isEmpty()) r.add(" ");
        return r;
    }

    private static String textoCompatible(PDType1Font font, String texto) {
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            String c = String.valueOf(texto.charAt(i));
            try {
                font.encode(c);
                r.append(c);
            } catch (IllegalArgumentException | IOException e) {
                r.append('?');
            }
        }
        return r.toString();
    }

    private static void validarExportable(String ext) {
        if ("mp3".equals(ext) || "wav".equals(ext) || "mp4".equals(ext)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los archivos de audio y video se descargan en su formato original; no se convierten a PDF o Word");
        }
    }

    private static boolean esImagen(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext);
    }

    private static String nombreArchivo(String nombreBase, String ext) {
        String base = nombreBase == null || nombreBase.isBlank() ? "documento" : nombreBase.trim();
        base = base.replaceAll("[<>:\"/\\\\|?*]", "_");
        if (base.length() > 180) base = base.substring(0, 180);
        return base + "." + ext;
    }

    public record ArchivoExportado(String nombreArchivo, String mimeType, byte[] bytes) {
    }
}
