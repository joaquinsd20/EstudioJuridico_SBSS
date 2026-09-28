package com.sbss.lexia.notificaciones.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class NotificacionDtos {

    private NotificacionDtos() {
    }

    public record Notificacion(
            Long id,
            String tipo,
            String titulo,
            String mensaje,
            String severidad,
            String entidadOrigen,
            Long registroId,
            Long expedienteId,
            String expedienteCodigo,
            String urlDestino,
            boolean leida,
            LocalDateTime fechaCreacion) {
    }

    /** El contador de la campanita y la lista, en una sola respuesta. */
    public record Bandeja(
            long noLeidas,
            List<Notificacion> items) {
    }

    public record CrearNotificacion(
            Long usuarioId,
            String tipo,
            String titulo,
            String mensaje,
            String severidad,
            String entidadOrigen,
            Long registroId,
            Long expedienteId,
            String urlDestino) {
    }
}
