package com.sbss.lexia.clientes.dto;

/**
 * KPIs de la cabecera de la pantalla de Clientes. Se calculan sobre TODO el
 * estudio, nunca sobre la pagina actual — ver ClienteRepository.resumen().
 */
public record ClientesResumenDto(
        long total,
        long activos,
        long naturales,
        long juridicas,
        long entidadesPublicas,
        long inactivos,
        long nuevosEsteMes,
        long conExpedientes) {
}
