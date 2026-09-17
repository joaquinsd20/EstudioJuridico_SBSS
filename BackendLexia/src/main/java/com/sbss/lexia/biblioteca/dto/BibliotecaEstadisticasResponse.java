package com.sbss.lexia.biblioteca.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class BibliotecaEstadisticasResponse {
    private long recursosTotales;
    private long recursosActivos;
    private long conArchivo;
    private long totalCategorias;
}