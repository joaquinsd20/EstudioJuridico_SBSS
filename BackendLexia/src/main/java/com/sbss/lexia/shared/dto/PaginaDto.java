package com.sbss.lexia.shared.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envoltorio de paginacion para la API, segun CLAUDE.md (`page`/`size`/`sort`).
 *
 * No se expone `org.springframework.data.domain.Page` directamente en ningun
 * controller: su JSON trae campos internos de Spring Data (`pageable`,
 * `sort.unsorted`...) que no son parte del contrato con Angular y cambian entre
 * versiones del framework sin aviso. Este DTO es el contrato estable.
 */
public record PaginaDto<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas,
        boolean esUltima) {

    public static <T> PaginaDto<T> desde(Page<T> pagina) {
        return new PaginaDto<>(
                pagina.getContent(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isLast());
    }

    /** Para cuando el contenido de la pagina se transforma (entidad -> DTO). */
    public static <T, R> PaginaDto<R> desde(Page<T> pagina, List<R> contenidoMapeado) {
        return new PaginaDto<>(
                contenidoMapeado,
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.isLast());
    }
}
