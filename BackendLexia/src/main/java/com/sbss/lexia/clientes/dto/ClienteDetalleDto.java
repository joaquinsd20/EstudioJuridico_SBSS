package com.sbss.lexia.clientes.dto;

import java.time.LocalDateTime;

/** Ficha completa del cliente, para el panel de detalle. */
public record ClienteDetalleDto(
        String uuid,
        String tipoPersona,
        String tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String razonSocial,
        String nombreComercial,
        /** nombre_busqueda: ya unifica persona natural y juridica. */
        String nombre,
        String email,
        String telefonoPrincipal,
        String telefonoSecundario,
        String direccion,
        String distrito,
        String provincia,
        String departamento,
        String estado,
        String origenCaptacion,
        String situacionFinanciera,
        /** Nombre completo del abogado responsable, o null si no tiene. */
        String abogadoResponsable,
        /** uuid publico del abogado responsable, o null. Para enlazar a su ficha. */
        String abogadoResponsableUuid,
        LocalDateTime fechaUltimoContacto,
        String observaciones,
        LocalDateTime fechaCreacion,
        long totalExpedientes) {
}
