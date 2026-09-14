package com.sbss.lexia.documental.dto;

public final class CatalogoDocumentoResponse {

    private CatalogoDocumentoResponse() {
    }

    public record ExpedienteOpcion(
            Long id,
            String codigo,
            String numeroExpediente,
            Long clienteId,
            String clienteNombre) {
    }

    public record UsuarioOpcion(Long id, String nombre) {
    }

    public record RelacionOpcion(Long id, String texto) {
    }
}
