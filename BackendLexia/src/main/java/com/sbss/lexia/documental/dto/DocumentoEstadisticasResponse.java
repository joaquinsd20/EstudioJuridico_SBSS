package com.sbss.lexia.documental.dto;

public record DocumentoEstadisticasResponse(
        long total,
        long recientes,
        long confidenciales,
        long papelera) {
}
