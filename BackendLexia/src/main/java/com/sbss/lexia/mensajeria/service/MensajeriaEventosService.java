package com.sbss.lexia.mensajeria.service;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Notificaciones sin contenido privado. Las consultas HTTP siguen siendo la fuente de verdad.
 * Solo funciona entre conexiones de la MISMA instancia; no es un bus distribuido.
 */
@Service
public class MensajeriaEventosService {
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> sesiones = new ConcurrentHashMap<>();
    @Value("${lexia.mensajeria.eventos.habilitados:false}") private boolean habilitados;

    public SseEmitter conectar(long usuarioId) {
        if (!habilitados) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Eventos deshabilitados");
        SseEmitter emitter = new SseEmitter(25000L);
        var lista = sesiones.computeIfAbsent(usuarioId, k -> new CopyOnWriteArrayList<>());
        if (lista.size() >= 3) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Demasiadas conexiones");
        lista.add(emitter);
        Runnable limpiar = () -> { lista.remove(emitter); if (lista.isEmpty()) sesiones.remove(usuarioId, lista); };
        emitter.onCompletion(limpiar);
        emitter.onTimeout(limpiar);
        emitter.onError(e -> limpiar.run());
        try { emitter.send(SseEmitter.event().name("conectado").data("ok")); }
        catch (IOException ex) { limpiar.run(); emitter.completeWithError(ex); }
        return emitter;
    }

    public void avisar(long usuarioId) {
        if (!habilitados) return;
        var lista = sesiones.get(usuarioId);
        if (lista == null) return;
        for (SseEmitter emitter : lista) {
            try { emitter.send(SseEmitter.event().name("mensajeria").data("actualizar")); }
            catch (Exception ex) { lista.remove(emitter); emitter.complete(); }
        }
    }
}
