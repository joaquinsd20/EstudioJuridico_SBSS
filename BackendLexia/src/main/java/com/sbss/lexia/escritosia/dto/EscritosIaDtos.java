package com.sbss.lexia.escritosia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos HTTP del modulo "Escritos con IA". */
public final class EscritosIaDtos {

    private EscritosIaDtos() {
    }

    public record TipoEscrito(
            String codigo,
            String nombre,
            String descripcion) {
    }

    public record GenerarRequest(
            @NotBlank String expedienteUuid,
            @NotBlank String tipoEscrito,
            @Size(max = 2000) String instrucciones) {
    }

    public record EscritoGenerado(
            String titulo,
            String contenido,
            String modelo,
            boolean generadoConIa,
            String expedienteUuid) {
    }

    /** Contenido final (posiblemente editado por el abogado) que se exportará. */
    public record ExportarEscritoRequest(
            @NotBlank @Size(max = 200) String titulo,
            @NotBlank @Size(max = 100000) String contenido) {
    }
}