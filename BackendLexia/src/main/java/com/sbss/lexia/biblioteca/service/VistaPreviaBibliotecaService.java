package com.sbss.lexia.biblioteca.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Genera la vista previa PDF de los recursos de Biblioteca Jurídica.
 *
 * Diseño:
 * - El archivo ORIGINAL nunca se reemplaza: Descargar sigue entregándolo.
 * - /ver siempre obtiene un PDF.
 * - PDF se sirve directamente, sin conversión.
 * - Word, Excel y PowerPoint se convierten a una representación PDF segura.
 * - El PDF resultante se cachea en disco junto al archivo original para no
 *   convertir repetidamente un documento en cada apertura.
 *
 * La conversión usa Apache POI + PDFBox, dependencias que ya utiliza LEXIA;
 * no requiere instalar LibreOffice en el servidor.
 */
@Service
public class VistaPreviaBibliotecaService {

    private static final Set<String> OFFICE = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx");

    /** Evita que una vista previa enorme agote memoria/CPU del servidor. */
    private static final int MAX_CARACTERES_PREVIEW = 4_000_000;
    private static final int MAX_FILAS_EXCEL = 50_000;
    private static final int MAX_CELDAS_EXCEL = 500_000;
    private static final int MAX_DIAPOSITIVAS = 2_000;

    private static final float MARGEN = 46f;
    private static final float FONT_SIZE = 10f;
    private static final float LEADING = 14f;
    private static final int CARACTERES_POR_LINEA = 105;

    private final ConcurrentHashMap<String, ReentrantLock> bloqueos = new ConcurrentHashMap<>();

    public Path obtenerPdf(Path original, String extension, String nombreOriginal) {
        if (original == null || !Files.isRegularFile(original)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "El archivo original del recurso no existe");
        }

        String ext = normalizarExtension(extension, nombreOriginal);

        if ("pdf".equals(ext)) {
            return original;
        }

        if (!OFFICE.contains(ext)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "No existe conversión PDF para el formato ." + ext);
        }

        try {
            Path carpetaCache = original.getParent().resolve(".lexia-preview").normalize();
            Files.createDirectories(carpetaCache);

            String idOriginal = sha256Texto(original.toAbsolutePath().normalize().toString())
                    .substring(0, 20);
            long tamano = Files.size(original);
            long modificado = Files.getLastModifiedTime(original).toMillis();
            String version = idOriginal + "-" + tamano + "-" + modificado;
            Path destino = carpetaCache.resolve(version + ".pdf").normalize();

            if (!destino.startsWith(carpetaCache)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Ruta de vista previa inválida");
            }

            if (pdfValido(destino)) {
                return destino;
            }

            ReentrantLock lock = bloqueos.computeIfAbsent(version, k -> new ReentrantLock());
            lock.lock();
            try {
                if (pdfValido(destino)) {
                    return destino;
                }

                limpiarVersionesAnteriores(carpetaCache, idOriginal, destino);
                generarPdf(original, ext, nombreOriginal, destino);

                if (!pdfValido(destino)) {
                    Files.deleteIfExists(destino);
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "No se pudo generar una vista previa PDF válida");
                }

                return destino;
            } finally {
                lock.unlock();
                if (!lock.hasQueuedThreads()) {
                    bloqueos.remove(version, lock);
                }
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo convertir el documento a PDF para vista previa",
                    e);
        }
    }

    private void generarPdf(
            Path original,
            String extension,
            String nombreOriginal,
            Path destino) throws IOException {

        TextoLimitado texto = new TextoLimitado(MAX_CARACTERES_PREVIEW);
        texto.linea("LEXIA - Biblioteca Jurídica");
        texto.linea("Vista previa convertida a PDF");
        texto.linea("Archivo original: " + nombreSeguro(nombreOriginal, original));
        texto.linea("");

        switch (extension) {
            case "docx" -> extraerDocx(original, texto);
            case "doc" -> extraerDoc(original, texto);
            case "xls", "xlsx" -> extraerExcel(original, texto);
            case "pptx" -> extraerPptx(original, texto);
            case "ppt" -> extraerPpt(original, texto);
            default -> throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato no convertible a PDF: ." + extension);
        }

        if (texto.truncado()) {
            texto.linea("");
            texto.linea("[Vista previa truncada por tamaño. Descarga el archivo original para consultar el contenido completo.]");
        }

        escribirPdfAtomico(destino, texto.texto());
    }

    private void extraerDocx(Path archivo, TextoLimitado salida) throws IOException {
        try (InputStream in = Files.newInputStream(archivo);
             XWPFDocument doc = new XWPFDocument(in)) {

            for (XWPFParagraph p : doc.getParagraphs()) {
                if (salida.lleno()) return;
                salida.linea(p.getText());
            }

            for (XWPFTable tabla : doc.getTables()) {
                if (salida.lleno()) return;
                salida.linea("");
                for (XWPFTableRow fila : tabla.getRows()) {
                    if (salida.lleno()) return;
                    StringBuilder linea = new StringBuilder();
                    for (XWPFTableCell celda : fila.getTableCells()) {
                        if (!linea.isEmpty()) linea.append(" | ");
                        linea.append(celda.getText());
                    }
                    salida.linea(linea.toString());
                }
            }
        } catch (Exception e) {
            throw archivoDanado("Word DOCX", e);
        }
    }

    private void extraerDoc(Path archivo, TextoLimitado salida) throws IOException {
        try (InputStream in = Files.newInputStream(archivo);
             HWPFDocument doc = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(doc)) {

            for (String parrafo : extractor.getParagraphText()) {
                if (salida.lleno()) return;
                salida.linea(parrafo);
            }
        } catch (Exception e) {
            throw archivoDanado("Word DOC", e);
        }
    }

    private void extraerExcel(Path archivo, TextoLimitado salida) throws IOException {
        DataFormatter formatter = new DataFormatter(new Locale("es", "PE"));
        int filasProcesadas = 0;
        int celdasProcesadas = 0;

        try (InputStream in = Files.newInputStream(archivo);
             Workbook workbook = WorkbookFactory.create(in)) {

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                if (salida.lleno()) return;

                Sheet sheet = workbook.getSheetAt(s);
                salida.linea("");
                salida.linea("HOJA: " + sheet.getSheetName());

                for (Row row : sheet) {
                    if (salida.lleno()) return;
                    if (++filasProcesadas > MAX_FILAS_EXCEL) {
                        salida.marcarTruncado();
                        return;
                    }

                    StringBuilder linea = new StringBuilder();
                    short primera = row.getFirstCellNum();
                    short ultima = row.getLastCellNum();
                    if (primera < 0 || ultima < 0) continue;

                    for (int c = primera; c < ultima; c++) {
                        if (++celdasProcesadas > MAX_CELDAS_EXCEL) {
                            salida.marcarTruncado();
                            return;
                        }

                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String valor = cell == null ? "" : formatter.formatCellValue(cell);
                        if (!linea.isEmpty()) linea.append(" | ");
                        linea.append(valor);
                    }

                    salida.linea(linea.toString());
                }
            }
        } catch (Exception e) {
            throw archivoDanado("Excel", e);
        }
    }

    private void extraerPptx(Path archivo, TextoLimitado salida) throws IOException {
        try (InputStream in = Files.newInputStream(archivo);
             XMLSlideShow ppt = new XMLSlideShow(in)) {

            int numero = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                if (salida.lleno()) return;
                if (++numero > MAX_DIAPOSITIVAS) {
                    salida.marcarTruncado();
                    return;
                }

                salida.linea("");
                salida.linea("DIAPOSITIVA " + numero);

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        salida.linea(textShape.getText());
                    }
                }
            }
        } catch (Exception e) {
            throw archivoDanado("PowerPoint PPTX", e);
        }
    }

    private void extraerPpt(Path archivo, TextoLimitado salida) throws IOException {
        try (InputStream in = Files.newInputStream(archivo);
             HSLFSlideShow ppt = new HSLFSlideShow(in)) {

            int numero = 0;
            for (HSLFSlide slide : ppt.getSlides()) {
                if (salida.lleno()) return;
                if (++numero > MAX_DIAPOSITIVAS) {
                    salida.marcarTruncado();
                    return;
                }

                salida.linea("");
                salida.linea("DIAPOSITIVA " + numero);

                for (HSLFShape shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) {
                        salida.linea(textShape.getText());
                    }
                }
            }
        } catch (Exception e) {
            throw archivoDanado("PowerPoint PPT", e);
        }
    }

    private void escribirPdfAtomico(Path destino, String contenido) throws IOException {
        Path temporal = Files.createTempFile(destino.getParent(), "preview-", ".tmp");
        try {
            escribirPdf(temporal, contenido);
            moverSeguro(temporal, destino);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private void escribirPdf(Path destino, String contenido) throws IOException {
        try (PDDocument documento = new PDDocument()) {
            PDType1Font fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage pagina = null;
            PDPageContentStream cs = null;
            float y = 0f;

            try {
                String normalizado = limpiarParaPdf(contenido);
                String[] bloques = normalizado.replace("\r", "").split("\n", -1);

                for (String bloque : bloques) {
                    for (String linea : envolver(bloque, CARACTERES_POR_LINEA)) {
                        if (pagina == null || y < MARGEN + LEADING) {
                            if (cs != null) {
                                cs.endText();
                                cs.close();
                            }

                            pagina = new PDPage(PDRectangle.A4);
                            documento.addPage(pagina);
                            cs = new PDPageContentStream(documento, pagina);
                            cs.beginText();
                            cs.setFont(fuente, FONT_SIZE);
                            cs.newLineAtOffset(MARGEN, pagina.getMediaBox().getHeight() - MARGEN);
                            y = pagina.getMediaBox().getHeight() - MARGEN;
                        }

                        cs.showText(linea.isEmpty() ? " " : linea);
                        cs.newLineAtOffset(0, -LEADING);
                        y -= LEADING;
                    }
                }
            } finally {
                if (cs != null) {
                    try {
                        cs.endText();
                    } finally {
                        cs.close();
                    }
                }
            }

            if (documento.getNumberOfPages() == 0) {
                documento.addPage(new PDPage(PDRectangle.A4));
            }

            documento.save(destino.toFile());
        }
    }

    private String[] envolver(String texto, int max) {
        if (texto == null || texto.isEmpty()) return new String[] {""};
        if (texto.length() <= max) return new String[] {texto};

        StringBuilder resultado = new StringBuilder(texto.length() + 32);
        int inicio = 0;
        while (inicio < texto.length()) {
            int fin = Math.min(inicio + max, texto.length());
            if (fin < texto.length()) {
                int espacio = texto.lastIndexOf(' ', fin);
                if (espacio > inicio + max / 2) fin = espacio;
            }
            resultado.append(texto, inicio, fin).append('\n');
            inicio = fin;
            while (inicio < texto.length() && texto.charAt(inicio) == ' ') inicio++;
        }
        return resultado.toString().split("\n", -1);
    }

    private String limpiarParaPdf(String valor) {
        if (valor == null) return "";
        StringBuilder out = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || (c >= 32 && c <= 255)) {
                out.append(c == '\t' ? ' ' : c);
            } else {
                out.append('?');
            }
        }
        return out.toString();
    }

    private void limpiarVersionesAnteriores(Path carpeta, String prefijo, Path conservar) {
        try (var stream = Files.list(carpeta)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefijo + "-"))
                    .filter(p -> p.getFileName().toString().endsWith(".pdf"))
                    .filter(p -> !p.equals(conservar))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                    });
        } catch (IOException ignored) {
            // La limpieza del cache nunca debe impedir abrir la vista previa.
        }
    }

    private boolean pdfValido(Path archivo) {
        try {
            if (!Files.isRegularFile(archivo) || Files.size(archivo) < 5) return false;
            byte[] cabecera = new byte[5];
            try (InputStream in = Files.newInputStream(archivo)) {
                if (in.read(cabecera) != 5) return false;
            }
            return cabecera[0] == '%'
                    && cabecera[1] == 'P'
                    && cabecera[2] == 'D'
                    && cabecera[3] == 'F'
                    && cabecera[4] == '-';
        } catch (IOException e) {
            return false;
        }
    }

    private void moverSeguro(Path origen, Path destino) throws IOException {
        try {
            Files.move(
                    origen,
                    destino,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String normalizarExtension(String extension, String nombreOriginal) {
        String ext = extension;
        if ((ext == null || ext.isBlank()) && nombreOriginal != null) {
            int i = nombreOriginal.lastIndexOf('.');
            if (i >= 0 && i + 1 < nombreOriginal.length()) {
                ext = nombreOriginal.substring(i + 1);
            }
        }

        ext = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT).replace(".", "");
        if (ext.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se pudo determinar la extensión del recurso");
        }
        return ext;
    }

    private String nombreSeguro(String nombre, Path original) {
        if (nombre != null && !nombre.isBlank()) return nombre.trim();
        return original.getFileName() == null ? "documento" : original.getFileName().toString();
    }

    private IOException archivoDanado(String tipo, Exception causa) {
        return new IOException("No se pudo leer el documento " + tipo + ". Puede estar dañado o protegido.", causa);
    }

    private String sha256Texto(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    private static final class TextoLimitado {
        private final StringBuilder contenido = new StringBuilder();
        private final int max;
        private boolean truncado;

        private TextoLimitado(int max) {
            this.max = max;
        }

        void linea(String valor) {
            if (lleno()) {
                truncado = true;
                return;
            }

            String v = valor == null ? "" : valor.replace('\u0000', ' ');
            int restante = max - contenido.length();
            if (v.length() + 1 > restante) {
                if (restante > 1) contenido.append(v, 0, restante - 1);
                contenido.append('\n');
                truncado = true;
                return;
            }

            contenido.append(v).append('\n');
        }

        boolean lleno() {
            return contenido.length() >= max;
        }

        boolean truncado() {
            return truncado;
        }

        void marcarTruncado() {
            truncado = true;
        }

        String texto() {
            return contenido.toString();
        }
    }
}
