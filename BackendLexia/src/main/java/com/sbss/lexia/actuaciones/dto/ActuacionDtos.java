package com.sbss.lexia.actuaciones.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos HTTP del modulo Actuaciones. */
public final class ActuacionDtos {

    private ActuacionDtos() {
    }

    public record Resumen(
            long total,
            long pendientes,
            long enTramite,
            long completadas,
            long minutosFacturables,
            BigDecimal costoTotal) {
    }

    public record ActuacionLista(
            String uuid,
            LocalDateTime fechaHora,
            Long tipoId,
            String tipoCodigo,
            String tipoNombre,
            String categoria,
            Long expedienteId,
            String expedienteCodigo,
            String numeroExpediente,
            String clienteNombre,
            String asunto,
            Long responsableId,
            String responsable,
            String estado,
            String resultado,
            String proximaAccion,
            Integer tiempoMinutos,
            boolean facturable,
            BigDecimal costo,
            String moneda,
            int documentos,
            int tareas,
            int plazos,
            int eventos) {
    }

    public record DocumentoRelacionado(
            String uuid,
            String nombre,
            String categoria,
            String estado,
            String nombreArchivo,
            Integer numeroVersion) {
    }

    public record TareaRelacionada(
            String uuid,
            String titulo,
            String estado,
            String prioridad,
            LocalDateTime fechaLimite,
            String responsable) {
    }

    public record PlazoRelacionado(
            String uuid,
            String descripcion,
            String estado,
            String nivelRiesgo,
            LocalDate fechaVencimiento,
            LocalDate fechaVencimientoInterna,
            boolean requiereRevisionProfesional) {
    }

    public record EventoRelacionado(
            String uuid,
            String tipo,
            String titulo,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            String estado,
            String modalidad) {
    }

    public record AlertaRelacionada(
            Long id,
            String tipo,
            String mensaje,
            String severidad,
            LocalDateTime fechaProgramada,
            String estado) {
    }

    public record ActuacionDetalle(
            ActuacionLista actuacion,
            String descripcion,
            String origen,
            boolean requiereVerificacion,
            List<DocumentoRelacionado> documentos,
            List<TareaRelacionada> tareas,
            List<PlazoRelacionado> plazos,
            List<EventoRelacionado> eventos,
            List<AlertaRelacionada> alertas) {
    }

    public record ExpedienteOpcion(
            Long id,
            String codigo,
            String numeroExpediente,
            String clienteNombre,
            Long responsableId,
            String responsable) {
    }

    public record TipoActuacionOpcion(
            Long id,
            String codigo,
            String nombre,
            String categoria,
            boolean generaPlazo,
            boolean generaTarea,
            boolean generaEventoAgenda,
            boolean requiereDocumento) {
    }

    public record UsuarioOpcion(
            Long id,
            String nombre,
            String cargo) {
    }

    public record TipoPlazoOpcion(
            Long id,
            String codigo,
            String nombre,
            Integer diasPorDefecto,
            String tipoComputoDefecto,
            String nivelRiesgoDefecto) {
    }

    public record DocumentoOpcion(
            Long id,
            String uuid,
            String nombre,
            String categoria,
            String estado,
            String archivo) {
    }

    public record Catalogos(
            List<ExpedienteOpcion> expedientes,
            List<TipoActuacionOpcion> tiposActuacion,
            List<UsuarioOpcion> usuarios,
            List<TipoPlazoOpcion> tiposPlazo) {
    }

    public record TareaAutomatica(
            boolean generar,
            @Size(max = 200) String titulo,
            @Size(max = 5000) String descripcion,
            @Size(max = 10) String prioridad,
            LocalDateTime fechaLimite,
            Long supervisorId,
            @Min(1) Integer tiempoEstimadoMinutos) {
    }

    public record PlazoAutomatico(
            boolean generar,
            Long tipoPlazoId,
            LocalDate fechaNotificacion,
            @Min(1) Integer diasPlazo,
            @Size(max = 15) String tipoComputo,
            @Size(max = 10) String nivelRiesgo,
            Long revisorId,
            boolean requiereRevisionProfesional,
            @Size(max = 250) String motivoRevision,
            @Size(max = 200) String actuacionRequerida) {
    }

    public record EventoAutomatico(
            boolean generar,
            @Size(max = 25) String tipo,
            @Size(max = 200) String titulo,
            @Size(max = 5000) String descripcion,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            @Size(max = 15) String modalidad,
            @Size(max = 250) String ubicacion,
            @Size(max = 500) String enlaceVirtual,
            @Min(0) Integer recordatorioMinutos) {
    }

    public record AlertaAutomatica(
            boolean generar,
            LocalDateTime fechaProgramada,
            @Size(max = 255) String mensaje,
            @Size(max = 10) String severidad) {
    }

    public record DocumentoPendienteAutomatico(
            boolean generar,
            @Size(max = 250) String nombre,
            @Size(max = 25) String categoria,
            @Size(max = 5000) String descripcion,
            boolean confidencial) {
    }

    public record CrearRequest(
            @NotNull Long expedienteId,
            @NotNull Long tipoActuacionId,
            @NotNull LocalDateTime fechaHora,
            @NotNull Long responsableId,
            @NotBlank @Size(max = 200) String asunto,
            @Size(max = 10000) String descripcion,
            @Size(max = 20) String estado,
            @Size(max = 10000) String resultado,
            @Size(max = 250) String proximaAccion,
            @Min(0) Integer tiempoMinutos,
            boolean facturable,
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal costo,
            @Size(min = 3, max = 3) String moneda,
            Long documentoExistenteId,
            @Valid TareaAutomatica tarea,
            @Valid PlazoAutomatico plazo,
            @Valid EventoAutomatico evento,
            @Valid AlertaAutomatica alerta,
            @Valid DocumentoPendienteAutomatico documentoPendiente) {
    }

    public record CrearResponse(
            String uuid,
            String tareaUuid,
            String plazoUuid,
            String eventoUuid,
            String documentoPendienteUuid,
            int alertasGeneradas) {
    }
}
