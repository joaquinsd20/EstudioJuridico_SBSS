package com.sbss.lexia.notificaciones.controller;

import com.sbss.lexia.notificaciones.dto.NotificacionDtos.Bandeja;
import com.sbss.lexia.notificaciones.dto.NotificacionDtos.Notificacion;
import com.sbss.lexia.notificaciones.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bandeja de notificaciones.
 *
 * No hay forma de CREAR por HTTP a proposito: las notificaciones las genera el
 * sistema. Quien las escribe es un scheduled o un evento de dominio, y abrir
 * un endpoint para crearlas permitiria a un usuario injectar avisos falsos en
 * la bandeja de un companero.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService service;

    @GetMapping
    public Bandeja listar(
            @RequestParam(defaultValue = "30") int limite,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas) {
        return service.listar(limite, soloNoLeidas);
    }

    @PatchMapping("/{id}/leer")
    public Notificacion marcarLeida(@PathVariable long id) {
        return service.marcarLeida(id);
    }

    @PostMapping("/leer-todas")
    public long marcarTodasLeidas() {
        return service.marcarTodasLeidas();
    }

    @DeleteMapping("/leidas")
    public long eliminarLeidas() {
        return service.eliminarLeidas();
    }
}
