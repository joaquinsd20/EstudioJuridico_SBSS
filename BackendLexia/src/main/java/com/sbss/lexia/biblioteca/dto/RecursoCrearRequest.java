package com.sbss.lexia.biblioteca.dto;

import lombok.Data;

@Data
public class RecursoCrearRequest {
    private String titulo;
    private String subtituloDescripcion;
    private String tipo;           // Jurisprudencia, Legislación, Doctrina, Contrato, Plantilla, Libro
    private String categoria;      // Derecho Civil, Procesal, General, etc.
    private Integer anio;
    private String expedienteCodigo;
    private String estado;         // Activo, Archivado
}