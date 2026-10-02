package com.sbss.lexia.plazos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Contratos HTTP del modulo critico de Plazos. */
public final class PlazoDtos {
    private PlazoDtos() {}

    public record Resumen(
            long totalActivos,
            long vencidos,
            long venceHoy,
            long proximosSieteDias,
            long criticos,
            long revisionProfesional) {}

    public record BarraSemana(
            int semana,
            long rojo,
            long naranja,
            long amarillo) {}

    public record EvolucionMes(
            int anio,
            int mes,
            long vencidos,
            long cumplidos) {}

    public record Analitica(
            List<BarraSemana> semanas,
            List<EvolucionMes> evolucion) {}

    public record PlazoLista(
            String uuid,
            String origen,
            Long expedienteId,
            String expedienteCodigo,
            String numeroExpediente,
            String clienteNombre,
            String materia,
            String organo,
            String distritoJudicial,
            Long tipoPlazoId,
            String tipoCodigo,
            String tipoNombre,
            String descripcion,
            LocalDate fechaNotificacion,
            LocalDate fechaInicioComputo,
            int diasPlazo,
            String tipoComputo,
            LocalDate fechaVencimiento,
            LocalDate fechaRevisionInterna,
            int diasRestantes,
            Long responsableId,
            String responsable,
            Long revisorId,
            String revisor,
            String estado,
            String nivelRiesgo,
            boolean requiereRevisionProfesional,
            String motivoRevision,
            String actuacionRequerida,
            Long resolucionId,
            String resolucionNumero,
            Long actuacionOrigenId,
            String actuacionAsunto,
            Long documentoId,
            String documentoNombre,
            String observaciones,
            int alertas) {}

    public record Alerta(
            Long id,
            String tipo,
            Integer diasAntes,
            String mensaje,
            String severidad,
            LocalDateTime fechaProgramada,
            String estado) {}

    public record PlazoDetalle(PlazoLista plazo, List<Alerta> alertas) {}

    public record ExpedienteOpcion(
            Long id,
            String codigo,
            String numeroExpediente,
            String clienteNombre,
            Long responsableId,
            String responsable,
            String distritoJudicial) {}

    public record UsuarioOpcion(Long id, String nombre, String cargo) {}

    public record TipoPlazoOpcion(
            Long id,
            String codigo,
            String nombre,
            Integer diasPorDefecto,
            String tipoComputoDefecto,
            String nivelRiesgoDefecto) {}

    public record Catalogos(
            List<ExpedienteOpcion> expedientes,
            List<UsuarioOpcion> usuarios,
            List<TipoPlazoOpcion> tiposPlazo) {}

    public record ResolucionOpcion(
            Long id,
            String numero,
            String tipo,
            LocalDate fechaResolucion,
            LocalDate fechaNotificacion,
            String sumilla,
            String origen,
            String estadoVerificacion) {}

    public record ActuacionOpcion(Long id, LocalDateTime fechaHora, String tipo, String asunto) {}

    public record DocumentoOpcion(Long id, String uuid, String nombre, String categoria, String estado) {}

    public record Relaciones(
            List<ResolucionOpcion> resoluciones,
            List<ActuacionOpcion> actuaciones,
            List<DocumentoOpcion> documentos) {}

    public record CalcularRequest(
            @NotNull Long expedienteId,
            @NotNull LocalDate fechaNotificacion,
            @NotNull @Min(1) Integer diasPlazo,
            @NotBlank @Size(max = 15) String tipoComputo) {}

    public record Calculo(
            LocalDate fechaInicioComputo,
            LocalDate fechaVencimiento,
            LocalDate fechaRevisionInternaSugerida,
            String distritoJudicial,
            boolean requiereRevisionSugerida,
            List<String> advertencias) {}

    public record GuardarRequest(
            @NotNull Long expedienteId,
            Long actuacionOrigenId,
            Long resolucionId,
            @NotNull Long tipoPlazoId,
            @NotBlank @Size(max = 25) String origen,
            @NotBlank @Size(max = 200) String descripcion,
            @NotNull LocalDate fechaNotificacion,
            @NotNull @Min(1) Integer diasPlazo,
            @NotBlank @Size(max = 15) String tipoComputo,
            LocalDate fechaRevisionInterna,
            @NotNull Long responsableId,
            Long revisorId,
            @Size(max = 25) String estado,
            @NotBlank @Size(max = 10) String nivelRiesgo,
            boolean requiereRevisionProfesional,
            @Size(max = 250) String motivoRevision,
            @Size(max = 200) String actuacionRequerida,
            Long documentoId,
            @Size(max = 10000) String observaciones,
            List<Integer> alertasDias) {}
}
