package com.sbss.lexia.documental.dto;

public record DocumentoContenidoResponse(
        String uuid,
        String nombre,
        String extensionOrigen,
        String html,
        boolean editable) {
}
