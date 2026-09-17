package com.sbss.lexia.biblioteca.dto;

import lombok.Data;

@Data
public class RecursoFiltroRequest {
    private String busqueda; // Título, autor, descripción, tags
    private String tipo;
    private String categoria;
    private String estado;
    private Boolean conArchivo; // true, false o null para ambos
}