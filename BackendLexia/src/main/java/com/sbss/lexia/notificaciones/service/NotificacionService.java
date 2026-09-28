package com.sbss.lexia.notificaciones.service;

import com.sbss.lexia.notificaciones.dto.NotificacionDtos.Bandeja;
import com.sbss.lexia.notificaciones.dto.NotificacionDtos.CrearNotificacion;
import com.sbss.lexia.notificaciones.dto.NotificacionDtos.Notificacion;
import com.sbss.lexia.notificaciones.repository.NotificacionRepositoryJdbc;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepositoryJdbc repo;

    public Bandeja listar(int limite, boolean soloNoLeidas) {
        return repo.listar(Math.min(Math.max(limite, 1), 200), soloNoLeidas);
    }

    @Transactional
    public Notificacion marcarLeida(long id) {
        Notificacion n = repo.marcarLeida(id);
        if (n == null) {
            throw new RecursoNoEncontradoException("La notificacion no existe o no es tuya");
        }
        return n;
    }

    @Transactional
    public long marcarTodasLeidas() {
        return repo.marcarTodasLeidas();
    }

    @Transactional
    public long eliminarLeidas() {
        return repo.eliminarLeidas();
    }

    /**
     * Crea una notificacion para un usuario.
     *
     * Vive en el servicio y no en una API publica: las notificaciones las
     * genera el sistema (un plazo vencido, una resolucion nueva, un comentario
     * en una tarea), nunca el usuario. Exponerla por HTTP permitiria a cualquiera
     * escribir en la bandeja de otro.
     */
    @Transactional
    public long crear(CrearNotificacion datos) {
        return repo.crear(datos);
    }
}
