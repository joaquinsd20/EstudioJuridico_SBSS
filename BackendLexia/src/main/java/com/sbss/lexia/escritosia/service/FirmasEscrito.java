package com.sbss.lexia.escritosia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta y ordena la zona de firmas de un escrito (todo lo que sigue a la línea de lugar y fecha).
 * Sin dependencias externas: se puede probar de forma aislada.
 *
 * Acepta las variantes que produce el modelo:
 *   A) BANCO DEL PERÚ S.A.A. / Representante legal: ... / ABOGADO PATROCINANTE / nombre / Colegiatura N.º: ...
 *   B) _ [Firma del Representante Legal] Banco ... / _ [Firma del Abogado] Abogado: ... Colegiatura N.º: ...
 *   C) [NOMBRE Y FIRMA DE LA PARTE O REPRESENTANTE] / [NOMBRE, FIRMA Y REGISTRO DEL ABOGADO PATROCINANTE]
 * y siempre entrega bloques con el mismo orden: título en mayúscula, luego los datos, uno por renglón.
 */
final class FirmasEscrito {

    static final String PENDIENTE = "[PENDIENTE DE COMPLETAR/VERIFICAR]";
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final Pattern FECHA = Pattern.compile(
            "^(?:TRUJILLO|LIMA|\\[LUGAR Y FECHA|[A-ZÁÉÍÓÚÑ ]{3,25},\\s*(?:\\d{1,2}\\s+DE\\b|\\[)).*");
    private static final Pattern MARCA = Pattern.compile("^[_—–-]+\\s*");
    private static final Pattern PLACEHOLDER_FIRMA = Pattern.compile("^(?:\\[\\s*Firma\\s+de(?:l|\\s+la)?\\s+([^\\]]+)\\]\\s*|Firma\\s+de(?:l|\\s+la)?\\s+([^\\n\\[]+?)\\s*(?:\\n|$))", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIE = Pattern.compile("(?i).*BORRADOR PARA REVISI[OÓ]N PROFESIONAL.*|P[AÁ]GINA\\s+\\d+(\\s+DE\\s+\\d+)?");

    /** Resultado: índice (en el arreglo de líneas) donde empieza la zona de firmas, o -1. */
    static int inicioZona(String[] lineas) {
        int fecha = -1;
        for (int i = 0; i < lineas.length; i++) {
            if (FECHA.matcher(limpio(lineas[i]).toUpperCase(ES)).matches()) fecha = i;
        }
        int inicio = -1;
        if (fecha >= 0) {
            inicio = fecha + 1;
        } else {
            for (int i = 0; i < lineas.length; i++) {
                String l = limpio(lineas[i]);
                if (!l.isBlank() && esMarcadorInicio(lineas[i].strip())) { inicio = i; break; }
            }
        }
        if (inicio < 0) return -1;
        boolean hayContenido = false;
        for (int i = inicio; i < lineas.length; i++) {
            String l = limpio(lineas[i]);
            if (l.isBlank() || PIE.matcher(l).matches()) continue;
            hayContenido = true;
            String u = l.toUpperCase(ES);
            // Si lo que sigue parece cuerpo del escrito (no firma), no se toca.
            if (l.length() > 160 || u.startsWith("OTROSÍ") || u.startsWith("OTROSI") || u.startsWith("POR TANTO")
                    || u.matches("^(I|II|III|IV|V|VI|VII|VIII|IX|X)[.)]\\s+.*")) return -1;
        }
        return hayContenido ? inicio : -1;
    }

    /** Convierte las líneas de la zona en bloques de firma ya normalizados (renglones separados por \n). */
    static List<String> bloques(String[] lineas, int inicio) {
        List<List<String>> grupos = new ArrayList<>();
        List<String> actual = null;
        boolean detalle = false;
        for (int i = inicio; i < lineas.length; i++) {
            String l = limpio(lineas[i]);
            if (l.isBlank() || l.matches("[-_=]{3,}") || PIE.matcher(l).matches()) continue;
            boolean nuevo = actual == null || esMarcadorInicio(lineas[i].strip()) || (esTitulo(l) && detalle)
                    || (detalle && l.toLowerCase(ES).matches("^abogado(\\s+patrocinante)?\\s*:.*"));
            if (nuevo) { actual = new ArrayList<>(); grupos.add(actual); detalle = false; }
            else if (!actual.isEmpty()) detalle = true;
            actual.add(l);
            if (actual.size() == 1 && !esTitulo(l)) detalle = false;
        }
        List<String> salida = new ArrayList<>();
        for (List<String> g : grupos) {
            String n = normalizar(String.join("\n", g));
            if (!n.isBlank()) salida.add(n);
        }
        return salida;
    }

    /** Deja el texto de una firma como: TÍTULO / datos... Idempotente. */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String t = texto.strip().replace("\r", "");
        t = MARCA.matcher(t).replaceFirst("");
        String etiquetaPlaceholder = null;
        Matcher m = PLACEHOLDER_FIRMA.matcher(t);
        if (m.find()) {
            etiquetaPlaceholder = (m.group(1) != null ? m.group(1) : m.group(2)).strip();
            t = t.substring(m.end());
        }

        t = t.replaceAll("(?i)\\s+(Representante\\s+Legal\\s*:)", "\n$1")
             .replaceAll("(?i)\\s+(Abogado\\s+Patrocinante\\s*:)", "\n$1")
             .replaceAll("(?i)\\s+(Abogado\\s*:)", "\n$1")
             .replaceAll("(?i)\\s+(Colegiatura\\s+N[.°º]*\\s*:)", "\n$1");
        List<String> r = new ArrayList<>();
        for (String linea : t.split("\n")) {
            String s = MARCA.matcher(linea.strip()).replaceFirst("").replaceAll("[ \\t]{2,}", " ");
            if (!s.isBlank()) r.add(s);
        }

        boolean esAbogado = (etiquetaPlaceholder != null && etiquetaPlaceholder.toLowerCase(ES).contains("abogado"))
                || (!r.isEmpty() && r.get(0).toLowerCase(ES).matches("^abogado(\\s+patrocinante)?\\s*:.*"));
        boolean esRepresentante = !esAbogado && etiquetaPlaceholder != null
                && etiquetaPlaceholder.toLowerCase(ES).contains("representante");

        if (esAbogado) {
            if (!r.isEmpty()) {
                String primera = r.get(0);
                String sinPrefijo = primera.replaceFirst("(?i)^abogado(\\s+patrocinante)?\\s*:\\s*", "");
                if (!sinPrefijo.equals(primera)) {
                    r.remove(0);
                    r.add(0, sinPrefijo.isBlank() ? PENDIENTE : sinPrefijo);
                }
            } else {
                r.add(PENDIENTE);
            }
            if (!r.get(0).equalsIgnoreCase("ABOGADO PATROCINANTE")) r.add(0, "ABOGADO PATROCINANTE");
        } else if (esRepresentante) {
            boolean tieneRep = r.stream().anyMatch(s -> s.toLowerCase(ES).startsWith("representante legal"));
            if (!r.isEmpty() && !r.get(0).toLowerCase(ES).startsWith("representante legal")) {
                r.set(0, r.get(0).toUpperCase(ES));
            }
            if (!tieneRep) r.add("Representante legal: " + PENDIENTE);
        } else if (!r.isEmpty() && !r.get(0).startsWith("[")) {
            r.set(0, r.get(0).toUpperCase(ES));
        }
        return String.join("\n", r);
    }

    private static boolean esMarcadorInicio(String linea) {
        String u = linea.toUpperCase(ES);
        return MARCA.matcher(linea).find() && linea.length() > 1 && !linea.matches("^[-_—–]{3,}$")
                || u.startsWith("[FIRMA DE") || u.startsWith("[NOMBRE") || u.startsWith("FIRMA DEL ");
    }

    private static boolean esTitulo(String l) {
        if (l.length() > 100 || l.startsWith("[") || l.contains(":")) return false;
        if (l.matches("^(COLEGIATURA|CAL|CAL\\.|DNI|RUC|REG\\.?)\\b.*")) return false;
        return l.chars().anyMatch(Character::isLetter) && l.equals(l.toUpperCase(ES));
    }

    private static String limpio(String s) {
        return s == null ? "" : s.strip().replace("**", "").replaceFirst("^#{1,6}\\s*", "");
    }

    private FirmasEscrito() { }
}
