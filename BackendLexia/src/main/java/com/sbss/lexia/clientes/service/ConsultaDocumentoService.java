package com.sbss.lexia.clientes.service;

import com.sbss.lexia.clientes.dto.ClienteGestionDtos.DatosDocumento;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Autocompleta la ficha de un cliente consultando su DNI (RENIEC) o RUC
 * (SUNAT) en Decolecta, un servicio privado que expone esos datos publicos.
 *
 * RENIEC no ofrece una API publica gratuita; Decolecta da 100 consultas al mes
 * sin costo con un token propio (LEXIA_DECOLECTA_TOKEN). Sin token el endpoint
 * responde 503 y el formulario se sigue llenando a mano, como siempre.
 *
 * El token nunca sale del backend: el navegador solo habla con LEXIA.
 */
@Slf4j
@Service
public class ConsultaDocumentoService {

    private final String token;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ConsultaDocumentoService(
            @Value("${lexia.consulta-documentos.token:}") String token,
            @Value("${lexia.consulta-documentos.base-url:https://api.decolecta.com/v1}") String baseUrl,
            ObjectMapper objectMapper) {
        this.token = token == null ? "" : token.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
    }

    public boolean disponible() {
        return !token.isEmpty();
    }

    public DatosDocumento consultar(String tipo, String numero) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        String n = numero == null ? "" : numero.trim();
        if (!disponible()) {
            throw new ConsultaDocumentoException(HttpStatus.SERVICE_UNAVAILABLE, "CONSULTA_NO_CONFIGURADA",
                    "La consulta automatica de DNI/RUC no esta configurada. Completa los datos a mano.");
        }
        return switch (t) {
            case "DNI" -> {
                if (!n.matches("\\d{8}")) throw invalido("El DNI debe tener 8 digitos.");
                yield desdeDni(llamar("/reniec/dni", n), n);
            }
            case "RUC" -> {
                if (!n.matches("(10|15|17|20)\\d{9}")) throw invalido("El RUC debe tener 11 digitos y empezar con 10, 15, 17 o 20.");
                yield desdeRuc(llamar("/sunat/ruc", n), n);
            }
            default -> throw invalido("Solo se pueden consultar DNI o RUC.");
        };
    }

    private JsonNode llamar(String ruta, String numero) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + ruta + "?numero=" + numero))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ConsultaDocumentoException(HttpStatus.GATEWAY_TIMEOUT, "CONSULTA_SIN_RESPUESTA",
                    "El servicio de consulta no respondio a tiempo. Intenta de nuevo o completa a mano.");
        } catch (IOException e) {
            log.warn("Consulta de documento: no se pudo conectar con el proveedor ({})", e.getMessage());
            throw new ConsultaDocumentoException(HttpStatus.BAD_GATEWAY, "CONSULTA_NO_DISPONIBLE",
                    "No se pudo conectar con el servicio de consulta. Completa los datos a mano.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConsultaDocumentoException(HttpStatus.SERVICE_UNAVAILABLE, "CONSULTA_INTERRUMPIDA",
                    "La consulta se interrumpio. Intenta de nuevo.");
        }

        int s = resp.statusCode();
        if (s == 200) {
            JsonNode raiz = objectMapper.readTree(resp.body());
            if (raiz == null || raiz.isEmpty()) throw noEncontrado();
            return raiz;
        }
        // No se registra el numero consultado: es un dato personal.
        log.warn("Consulta de documento {}: el proveedor respondio HTTP {}", ruta, s);
        if (s == 400 || s == 404 || s == 422) throw noEncontrado();
        if (s == 401 || s == 403) {
            throw new ConsultaDocumentoException(HttpStatus.BAD_GATEWAY, "CONSULTA_TOKEN_INVALIDO",
                    "El token del servicio de consulta no es valido. Avisa al administrador.");
        }
        if (s == 429) {
            throw new ConsultaDocumentoException(HttpStatus.TOO_MANY_REQUESTS, "CONSULTA_LIMITE",
                    "Se alcanzo el limite de consultas del mes. Completa los datos a mano.");
        }
        throw new ConsultaDocumentoException(HttpStatus.BAD_GATEWAY, "CONSULTA_NO_DISPONIBLE",
                "El servicio de consulta no esta disponible. Completa los datos a mano.");
    }

    private static DatosDocumento desdeDni(JsonNode j, String numero) {
        String nombres = texto(j, "first_name");
        String paterno = texto(j, "first_last_name");
        if (nombres == null && paterno == null) throw noEncontrado();
        return new DatosDocumento("DNI", numero, nombres, paterno, texto(j, "second_last_name"),
                null, null, null, null, null, null, null);
    }

    private static DatosDocumento desdeRuc(JsonNode j, String numero) {
        String razon = texto(j, "razon_social");
        if (razon == null) throw noEncontrado();
        return new DatosDocumento("RUC", numero, null, null, null, razon,
                texto(j, "direccion"), texto(j, "distrito"), texto(j, "provincia"),
                texto(j, "departamento"), texto(j, "estado"), texto(j, "condicion"));
    }

    /** Texto limpio o null; SUNAT usa "-" para los campos vacios. */
    private static String texto(JsonNode j, String campo) {
        JsonNode v = j.get(campo);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim().replaceAll("\\s+", " ");
        return s.isEmpty() || s.equals("-") ? null : s;
    }

    private static ConsultaDocumentoException invalido(String mensaje) {
        return new ConsultaDocumentoException(HttpStatus.BAD_REQUEST, "DOCUMENTO_INVALIDO", mensaje);
    }

    private static ConsultaDocumentoException noEncontrado() {
        return new ConsultaDocumentoException(HttpStatus.NOT_FOUND, "DOCUMENTO_NO_ENCONTRADO",
                "No se encontraron datos para ese numero. Revisa el numero o completa a mano.");
    }

    /** Error de la consulta, con el estado y el mensaje que vera el usuario. */
    public static class ConsultaDocumentoException extends RuntimeException {
        private final HttpStatus estado;
        private final String codigo;

        public ConsultaDocumentoException(HttpStatus estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }

        public HttpStatus getEstado() { return estado; }
        public String getCodigo() { return codigo; }
    }
}
