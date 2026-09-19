package com.sbss.lexia.expedientes.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Contratos HTTP del modulo Expedientes. */
public final class ExpedienteDtos {

    private ExpedienteDtos() {
    }

    public record Resumen(
            long total,
            long activos,
            long enTramite,
            long criticos) {
    }

    public record ExpedienteLista(
            String uuid,
            String codigoInterno,
            String numeroExpediente,
            Integer anio,
            String clienteNombre,
            String clienteDocumento,
            String especialidad,
            String materia,
            String organo,
            String distritoJudicial,
            String responsable,
            String estadoCodigo,
            String estadoNombre,
            String estadoColor,
            String nivelRiesgo,
            BigDecimal cuantia,
            String moneda,
            LocalDate fechaInicio,
            LocalDateTime fechaUltimaActuacion,
            boolean confidencial) {
    }

    public record PersonaRelacionada(
            Long id,
            String nombre,
            String detalle) {
    }

    public record Equipo(
            Long usuarioId,
            String nombre,
            String rol,
            boolean puedeEditar) {
    }

    public record ExpedienteDetalle(
            String uuid,
            String codigoInterno,
            String numeroExpediente,
            String numeroCorrelativo,
            Integer anio,
            Long clienteId,
            String clienteNombre,
            String clienteDocumento,
            Long especialidadId,
            String especialidad,
            Long materiaId,
            String materia,
            Long organoId,
            String organo,
            String distritoJudicial,
            String etapaProcesal,
            String instancia,
            Long estadoId,
            String estadoCodigo,
            String estadoNombre,
            String estadoColor,
            Long abogadoResponsableId,
            String abogadoResponsable,
            Long socioSupervisorId,
            String socioSupervisor,
            String nivelRiesgo,
            BigDecimal cuantia,
            String moneda,
            LocalDate fechaInicio,
            LocalDate fechaIngresoEstudio,
            LocalDateTime fechaUltimaActuacion,
            LocalDate fechaCierre,
            String resultado,
            boolean confidencial,
            String resumen,
            List<PersonaRelacionada> contrapartes,
            List<Equipo> equipo) {
    }

    public record EventoLineaTiempo(
            String clave,
            String tipo,
            LocalDateTime fechaHora,
            String usuario,
            String titulo,
            String descripcion,
            String documentoUuid,
            String plazoUuid,
            String tareaUuid,
            String origen) {
    }

    public record Opcion(
            Long id,
            String nombre,
            String detalle) {
    }

    public record MateriaOpcion(
            Long id,
            Long especialidadId,
            String nombre) {
    }

    public record EstadoOpcion(
            Long id,
            String codigo,
            String nombre,
            String color) {
    }

    public record Catalogos(
            List<Opcion> clientes,
            List<Opcion> especialidades,
            List<MateriaOpcion> materias,
            List<Opcion> organos,
            List<EstadoOpcion> estados,
            List<Opcion> usuarios,
            List<Opcion> contrapartes) {
    }

    public record GuardarRequest(
            @Size(max = 50) String numeroExpediente,
            @Size(max = 10) String numeroCorrelativo,
            @NotNull @Min(2000) @Max(2100) Integer anio,
            @NotNull Long clienteId,
            @NotNull Long especialidadId,
            Long materiaId,
            Long organoId,
            @NotNull Long estadoId,
            @Size(max = 60) String etapaProcesal,
            @Size(max = 20) String instancia,
            @NotNull Long abogadoResponsableId,
            Long socioSupervisorId,
            @NotNull @Size(max = 10) String nivelRiesgo,
            @DecimalMin(value = "0.0", inclusive = true) BigDecimal cuantia,
            @Size(min = 3, max = 3) String moneda,
            @NotNull LocalDate fechaInicio,
            LocalDate fechaIngresoEstudio,
            LocalDate fechaCierre,
            @Size(max = 20) String resultado,
            boolean confidencial,
            String resumen,
            List<Long> equipoIds,
            List<Long> contraparteIds) {
    }
}
