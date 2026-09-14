package com.sbss.lexia.clientes.dto;

/**
 * Filtros opcionales del listado de clientes.
 *
 * Deliberadamente SIN un campo estudioId: si el campo no existe aqui, nadie
 * puede sobrescribirlo desde la query string. El acotado por estudio lo
 * construye ClienteService leyendo SesionActual, nunca esta peticion.
 */
public record ClienteFiltro(
        /** Busca por prefijo del numero de documento (DNI/RUC). */
        String documento,
        /** Busca por substring en el nombre unificado (natural o juridica). */
        String nombre,
        /** NATURAL · JURIDICA · ENTIDAD_PUBLICA. */
        String tipoPersona,
        /** PROSPECTO · ACTIVO · INACTIVO · ANTIGUO. */
        String estado) {
}
