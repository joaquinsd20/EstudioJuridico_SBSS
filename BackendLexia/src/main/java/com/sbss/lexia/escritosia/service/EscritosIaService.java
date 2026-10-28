package com.sbss.lexia.escritosia.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.EscritoGenerado;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.GenerarRequest;
import com.sbss.lexia.escritosia.dto.EscritosIaDtos.TipoEscrito;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteDetalle;
import com.sbss.lexia.expedientes.service.ExpedienteService;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscritosIaService {

    private final ExpedienteService expedienteService;
    private final ObjectMapper objectMapper;

    @Value("${lexia.ia.openai-api-key:}")
    private String openaiApiKey;

    @Value("${lexia.ia.openai-model:gpt-4o-mini}")
    private String openaiModel;

    @Value("${lexia.ia.openai-base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    private static final List<TipoEscrito> TIPOS = List.of(
            new TipoEscrito("DEMANDA", "Demanda", "Presentacion de demanda civil"),
            new TipoEscrito("CONTESTACION", "Contestacion", "Contestacion de demanda"),
            new TipoEscrito("RECURSO_APELACION", "Recurso de apelacion", "Recurso de apelacion"),
            new TipoEscrito("ESCRITO", "Escrito", "Escrito libre / peticion"),
            new TipoEscrito("INFORME", "Informe", "Informe procesal"));

    @Transactional(readOnly = true)
    public List<TipoEscrito> tipos() {
        return TIPOS;
    }

    @Transactional(readOnly = true)
    public EscritoGenerado generar(GenerarRequest request) {
        ExpedienteDetalle exp = expedienteService.obtener(request.expedienteUuid());
        if (exp == null) {
            throw new ParametroInvalidoException("Expediente no encontrado");
        }
        String tipoNombre = TIPOS.stream()
                .filter(t -> t.codigo().equals(request.tipoEscrito()))
                .map(TipoEscrito::nombre)
                .findFirst()
                .orElse(request.tipoEscrito());

        String titulo = tipoNombre + " - " + (exp.numeroExpediente() != null ? exp.numeroExpediente() : exp.codigoInterno());

        String contenido;
        boolean conIa;
        String modelo;

        if (openaiApiKey != null && !openaiApiKey.isBlank()) {
            try {
                contenido = llamarOpenAi(exp, request, tipoNombre);
                conIa = true;
                modelo = openaiModel;
            } catch (Exception e) {
                log.warn("Fallo llamada a IA, usando borrador local: {}", e.getMessage());
                contenido = borradorLocal(exp, request, tipoNombre);
                conIa = false;
                modelo = "plantilla-local";
            }
        } else {
            contenido = borradorLocal(exp, request, tipoNombre);
            conIa = false;
            modelo = "plantilla-local";
        }

        return new EscritoGenerado(titulo, contenido, modelo, conIa, exp.uuid());
    }

    private String llamarOpenAi(ExpedienteDetalle exp, GenerarRequest request, String tipoNombre) throws Exception {
        String system = "Eres un asistente juridico peruano. Redactas escritos procesales formales en espanol. "
                + "Usa un tono profesional y estructura: encabezado, antecedentes, fundamentos de derecho, petitorio. "
                + "No inventes datos que no esten en el contexto; si falta informacion, deja corchetes [ ] para completar.";

        String contexto = construirContexto(exp, request, tipoNombre);

        Map<String, Object> body = Map.of(
                "model", openaiModel,
                "temperature", 0.4,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", contexto)));

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(openaiBaseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("OpenAI HTTP " + resp.statusCode() + ": " + resp.body());
        }

        var root = objectMapper.readTree(resp.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private String construirContexto(ExpedienteDetalle exp, GenerarRequest request, String tipoNombre) {
        StringBuilder sb = new StringBuilder();
        sb.append("Redacta un ").append(tipoNombre).append(" para el siguiente expediente.\n\n");
        sb.append("Expediente: ").append(exp.numeroExpediente() != null ? exp.numeroExpediente() : exp.codigoInterno()).append("\n");
        sb.append("Cliente: ").append(exp.clienteNombre()).append(" (").append(exp.clienteDocumento()).append(")\n");
        sb.append("Especialidad: ").append(exp.especialidad()).append("\n");
        sb.append("Materia: ").append(exp.materia()).append("\n");
        sb.append("Organo / distrito: ").append(exp.organo()).append(" - ").append(exp.distritoJudicial()).append("\n");
        sb.append("Etapa procesal: ").append(exp.etapaProcesal()).append("\n");
        sb.append("Instancia: ").append(exp.instancia()).append("\n");
        sb.append("Estado: ").append(exp.estadoNombre()).append("\n");
        sb.append("Nivel de riesgo: ").append(exp.nivelRiesgo()).append("\n");
        sb.append("Cuantia: ").append(exp.cuantia()).append(" ").append(exp.moneda()).append("\n");
        sb.append("Fecha de inicio: ").append(exp.fechaInicio()).append("\n");
        sb.append("Resumen: ").append(exp.resumen()).append("\n");
        if (request.instrucciones() != null && !request.instrucciones().isBlank()) {
            sb.append("\nInstrucciones adicionales del abogado:\n").append(request.instrucciones()).append("\n");
        }
        sb.append("\nDevuelve solo el texto del escrito, sin explicaciones.");
        return sb.toString();
    }

    private String borradorLocal(ExpedienteDetalle exp, GenerarRequest request, String tipoNombre) {
        String numero = exp.numeroExpediente() != null ? exp.numeroExpediente() : exp.codigoInterno();
        StringBuilder sb = new StringBuilder();
        sb.append("SEÑOR JUEZ DEL JUZGADO COMPETENTE\n\n");
        sb.append(tipoNombre.toUpperCase()).append("\n\n");
        sb.append("Expediente N.º: ").append(numero).append("\n");
        sb.append("Materia: ").append(exp.materia()).append(" (").append(exp.especialidad()).append(")\n");
        sb.append("Distrito judicial: ").append(exp.distritoJudicial()).append("\n\n");
        sb.append("PARTE PROCESAL:\n");
        sb.append(exp.clienteNombre()).append(", identificado con ").append(exp.clienteDocumento())
                .append(", por intermedio de su abogado, se presenta y dice:\n\n");
        sb.append("I. ANTECEDENTES\n");
        sb.append("En el expediente referido, con fecha ").append(exp.fechaInicio())
                .append(" se inicio el proceso. Estado actual: ").append(exp.estadoNombre()).append(".\n");
        sb.append("Resumen: ").append(exp.resumen() != null ? exp.resumen() : "[completar]").append("\n\n");
        sb.append("II. FUNDAMENTOS DE DERECHO\n");
        sb.append("[Completar con la normativa aplicable]\n\n");
        sb.append("III. PETITORIO\n");
        sb.append("Por lo expuesto, solicito a su despacho: [completar petitorio].\n\n");
        if (request.instrucciones() != null && !request.instrucciones().isBlank()) {
            sb.append("Instrucciones del abogado:\n").append(request.instrucciones()).append("\n\n");
        }
        sb.append("______________________________\n");
        sb.append("Abogado responsable: ").append(exp.abogadoResponsable()).append("\n");
        sb.append("Lima, [fecha]\n\n");
        sb.append("NOTA: Borrador generado localmente (sin IA). Configure LEXIA_IA_OPENAI_API_KEY para generacion con IA.");
        return sb.toString();
    }
}