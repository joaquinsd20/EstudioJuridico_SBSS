package com.sbss.lexia.biblioteca.dto;

import lombok.Data;

@Data
public class RecursoFiltroRequest {
    private String busqueda;
    private String tipo;
    private String categoria;
    private String estado;
    private Boolean conArchivo;
    private Integer anio;
}
