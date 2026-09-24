package com.sbss.lexia.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MonitorJudicialDtos {

    private MonitorJudicialDtos() {
    }

    public record Resumen(
            long expedientesMonitoreados,
            long movimientosHoy,
            long nuevasResolucionesHoy,
            long pendientesVerificacion) {
    }

    public record ExpedienteMonitor(
            String uuid,
            Long expedienteId,
            String codigoInterno,
            String numeroExpediente,
            String cliente,
            String organo,
            String estadoProcesal,
            String abogadoResponsable,
            String fuentePlataforma,
            String estadoMonitor,
            LocalDateTime fechaCreacion,
            LocalDateTime ultimaConsulta,
            long totalConsultas,
            long movimientos,
            long pendientesVerificacion,
            String ultimoMovimiento,
            LocalDateTime fechaUltimoMovimiento) {
    }

    public record Consulta(
            String uuid,
            Long expedienteId,
            String codigoInterno,
            String numeroExpediente,
            String cliente,
            LocalDateTime fechaConsulta,
            Long usuarioId,
            String usuario,
            String fuentePlataforma,
            boolean huboMovimiento,
            String tipoMovimiento,
            boolean nuevaResolucion,
            String numeroResolucion,
            String tipoResolucion,
            LocalDate fechaResolucion,
            LocalDate fechaNotificacion,
            String documentoDescargado,
            String notificacion,
            String plazo,
            String observaciones,
            String origenInformacion,
            String estadoVerificacion,
            String verificadoPor,
            LocalDateTime fechaVerificacion) {
    }

    public record ExpedienteOpcion(
            Long id,
            String codigoInterno,
            String numeroExpediente,
            String cliente,
            String organo,
            String abogadoResponsable) {
    }

    public record Catalogos(List<ExpedienteOpcion> expedientes) {
    }

    public record AgregarExpedienteRequest(
            @NotNull Long expedienteId,
            @NotBlank @Size(max = 150) String fuentePlataforma) {
    }

    public record RegistrarConsultaRequest(
            @NotNull Long expedienteId,
            @NotBlank @Size(max = 150) String fuentePlataforma,
            boolean huboMovimiento,
            @Size(max = 40) String tipoMovimiento,
            boolean nuevaResolucion,
            @Size(max = 30) String numeroResolucion,
            @Size(max = 25) String tipoResolucion,
            LocalDate fechaResolucion,
            LocalDate fechaNotificacion,
            @Size(max = 250) String documentoDescargado,
            @Size(max = 500) String notificacion,
            @Size(max = 250) String plazo,
            @Size(max = 10000) String observaciones,
            @NotBlank @Size(max = 15) String origenInformacion,
            @NotBlank @Size(max = 15) String estadoVerificacion) {
    }

    public record VerificarRequest(
            @NotBlank @Size(max = 15) String estadoVerificacion) {
    }
}
