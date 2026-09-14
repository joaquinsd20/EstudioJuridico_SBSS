package com.sbss.lexia.clientes.dto;

/** Una fila de la tabla "Lista de Clientes". */
public record ClienteResumenDto(
        String uuid,
        String tipoDocumento,
        String numeroDocumento,
        /** nombre_busqueda: ya unifica persona natural y juridica. */
        String nombre,
        String tipoPersona,
        String estado,
        String email,
        String telefonoPrincipal,
        /** Nombre completo del abogado responsable, o null si no tiene. */
        String abogadoResponsable,
        long totalExpedientes) {
}
