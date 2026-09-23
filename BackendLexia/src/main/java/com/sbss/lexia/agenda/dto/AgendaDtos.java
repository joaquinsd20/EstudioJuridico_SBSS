package com.sbss.lexia.agenda.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Contratos HTTP del módulo Agenda. El Monitor Judicial se implementa aparte. */
public final class AgendaDtos {

    private AgendaDtos() {
    }

    public record Resumen(
            long eventosHoy,
            long proximosSieteDias,
            long audienciasMes,
            long pendientesVerificacion,
            long totalMes) {
    }

    public record EventoLista(
            String uuid,
            String tipo,
            String titulo,
            String descripcion,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            boolean todoElDia,
            String modalidad,
            String ubicacion,
            String enlaceVirtual,
            String estado,
            String resultado,
            Integer recordatorioMinutos,
            String colorHex,
            String origenInformacion,
            boolean requiereVerificacion,
            Long expedienteId,
            String expedienteCodigo,
            String numeroExpediente,
            String clienteNombre,
            Long creadorId,
            String creador,
            int participantes) {
    }

    public record Participante(
            Long id,
            String nombre,
            String cargo,
            String tipoParticipacion,
            boolean confirmado) {
    }

    public record Reprogramacion(
            Long id,
            LocalDateTime fechaInicioAnterior,
            LocalDateTime fechaFinAnterior,
            LocalDateTime fechaInicioNueva,
            LocalDateTime fechaFinNueva,
            String motivo,
            String usuario,
            LocalDateTime fechaReprogramacion) {
    }

    public record EventoDetalle(
            EventoLista evento,
            List<Participante> participantes,
            List<Reprogramacion> reprogramaciones) {
    }

    public record ExpedienteOpcion(
            Long id,
            String codigo,
            String numeroExpediente,
            String clienteNombre) {
    }

    public record UsuarioOpcion(
            Long id,
            String nombre,
            String cargo) {
    }

    public record Catalogos(
            List<ExpedienteOpcion> expedientes,
            List<UsuarioOpcion> usuarios) {
    }

    public record GuardarRequest(
            Long expedienteId,
            @NotBlank @Size(max = 25) String tipo,
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 10000) String descripcion,
            @NotNull LocalDateTime fechaInicio,
            @NotNull LocalDateTime fechaFin,
            boolean todoElDia,
            @NotBlank @Size(max = 15) String modalidad,
            @Size(max = 250) String ubicacion,
            @Size(max = 500) String enlaceVirtual,
            @Min(0) Integer recordatorioMinutos,
            @Size(max = 7) String colorHex,
            @NotBlank @Size(max = 15) String origenInformacion,
            boolean requiereVerificacion,
            List<Long> participanteIds) {
    }

    public record ActualizarRequest(
            Long expedienteId,
            @NotBlank @Size(max = 25) String tipo,
            @NotBlank @Size(max = 200) String titulo,
            @Size(max = 10000) String descripcion,
            @NotNull LocalDateTime fechaInicio,
            @NotNull LocalDateTime fechaFin,
            boolean todoElDia,
            @NotBlank @Size(max = 15) String modalidad,
            @NotBlank @Size(max = 15) String estado,
            @Size(max = 10000) String resultado,
            @Size(max = 250) String ubicacion,
            @Size(max = 500) String enlaceVirtual,
            @Min(0) Integer recordatorioMinutos,
            @Size(max = 7) String colorHex,
            @NotBlank @Size(max = 15) String origenInformacion,
            boolean requiereVerificacion,
            List<Long> participanteIds) {
    }

    public record ReprogramarRequest(
            @NotNull LocalDateTime fechaInicio,
            @NotNull LocalDateTime fechaFin,
            @NotBlank @Size(max = 500) String motivo,
            @Min(0) Integer recordatorioMinutos) {
    }

    public record ResultadoRequest(
            @NotBlank @Size(max = 10000) String resultado,
            boolean verificado) {
    }
}
