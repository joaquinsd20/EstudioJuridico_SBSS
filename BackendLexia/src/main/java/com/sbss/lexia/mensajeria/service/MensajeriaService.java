package com.sbss.lexia.mensajeria.service;

import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Conversacion;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Contacto;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Mensaje;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.PaginaContactos;
import com.sbss.lexia.mensajeria.repository.MensajeriaRepositoryJdbc;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MensajeriaService {

    private final MensajeriaRepositoryJdbc repo;
    private final MensajeriaEventosService eventos;

    private void despuesDeCommit(long usuarioId, long emisorId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { eventos.avisar(usuarioId); eventos.avisar(emisorId); }
                });
        } else { eventos.avisar(usuarioId); eventos.avisar(emisorId); }
    }

    public List<Conversacion> bandeja() {
        return repo.bandeja();
    }

    public List<Contacto> contactos() {
        return repo.contactos();
    }

    public PaginaContactos contactos(int pagina, int tamano, String busqueda) {
        return repo.contactos(pagina, tamano, busqueda);
    }

    public long contarNoLeidos() {
        return repo.contarNoLeidos();
    }

    /**
     * Abre el chat con un colega, creandolo si es el primer mensaje.
     *
     * El frontend guarda el id del CONTACTO, no el de la conversacion: es lo
     * que aparece en la lista lateral y lo que sobrevive a que el contacto
     * escriba primero y cree la conversacion desde su lado. Por eso hace falta
     * un endpoint que resuelva contacto -> conversacion.
     */
    public Conversacion abrirConContacto(long contactoId, int limite) {
        long conversacionId = repo.buscarConversacionEntre(contactoId);

        if (conversacionId == 0) {
            conversacionId = repo.crearConversacion(contactoId);
        }

        return repo.conversacion(conversacionId, limite);
    }

    public Conversacion conversacion(long id, int limite) {
        return repo.conversacion(id, limite);
    }

    public Mensaje enviar(long destinatarioId, String cuerpo) {
        Mensaje mensaje = repo.enviar(destinatarioId, cuerpo);
        despuesDeCommit(destinatarioId, mensaje.emisorId());
        return mensaje;
    }

    public Mensaje enviarEnConversacion(long conversacionId, String cuerpo) {
        Mensaje mensaje = repo.enviarEnConversacion(conversacionId, cuerpo);
        despuesDeCommit(repo.destinatarioDeConversacion(conversacionId), mensaje.emisorId());
        return mensaje;
    }

    public long marcarLeida(long conversacionId) {
        long resultado = repo.marcarLeida(conversacionId);
        despuesDeCommit(repo.destinatarioDeConversacion(conversacionId), com.sbss.lexia.shared.security.SesionActual.usuarioId());
        return resultado;
    }
}
