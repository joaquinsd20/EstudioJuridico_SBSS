package com.sbss.lexia.shared.correo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Envio de correos transaccionales por la API HTTP de Brevo.
 *
 * POR QUE HTTP Y NO SMTP. El plan gratuito de Render bloquea la salida por los
 * puertos SMTP (25, 465, 587): un JavaMailSender nunca llegaria a conectar.
 * Brevo da 300 correos al dia gratis y solo pide verificar el correo remitente
 * (no hace falta un dominio propio).
 *
 * Sin LEXIA_BREVO_API_KEY o LEXIA_CORREO_REMITENTE queda desactivado y
 * {@link #activo()} devuelve false; quien lo use decide el plan B.
 */
@Slf4j
@Service
public class CorreoService {

    private final String apiKey;
    private final String remitente;
    private final String nombreRemitente;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public CorreoService(
            @Value("${lexia.correo.brevo-api-key:}") String apiKey,
            @Value("${lexia.correo.remitente:}") String remitente,
            @Value("${lexia.correo.nombre-remitente:LEXIA}") String nombreRemitente,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
        this.objectMapper = objectMapper;
    }

    public boolean activo() {
        return !apiKey.isEmpty() && !remitente.isEmpty();
    }

    /** Envia un correo HTML. Devuelve true si el proveedor lo acepto. */
    public boolean enviar(String para, String nombre, String asunto, String html, String texto) {
        if (!activo()) return false;
        try {
            String cuerpo = objectMapper.writeValueAsString(Map.of(
                    "sender", Map.of("name", nombreRemitente, "email", remitente),
                    "to", List.of(Map.of("email", para, "name", nombre == null ? para : nombre)),
                    "subject", asunto,
                    "htmlContent", html,
                    "textContent", texto));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) return true;
            // No se registra el destinatario: es un dato personal.
            log.warn("Brevo rechazo el correo '{}': HTTP {}", asunto, resp.statusCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo '{}': {}", asunto, e.toString());
            return false;
        }
    }
}
