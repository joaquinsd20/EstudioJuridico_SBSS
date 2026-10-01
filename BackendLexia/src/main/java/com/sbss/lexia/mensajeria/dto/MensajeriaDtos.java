package com.sbss.lexia.mensajeria.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MensajeriaDtos {

    private MensajeriaDtos() {
    }

    /**
     * Un mensaje del chat.
     *
     * `leido` es un booleano y no un id, porque el front solo necesita pintar la
     * doble palomita. El id de lectura vive en la conversacion, no aqui: ver
     * MensajeriaRepositoryJdbc.
     */
    public record Mensaje(
            Long id,
            Long conversacionId,
            Long emisorId,
            String emisorNombre,
            boolean propio,
            String cuerpo,
            boolean leido,
            LocalDateTime fechaCreacion) {
    }

    /**
     * Una conversacion, tal como aparece en la bandeja.
     *
     * No lleva la lista de mensajes a proposito: la bandeja son 30 filas, y
     * meterle los mensajes de cada una convertiria el listado en miles de filas
     * para pintar una lista lateral. El chat se abre aparte.
     *
     * El nombre del otro se trae ya resuelto con un JOIN porque la bandeja
     * TODOS los interlocutores, y N+1 seria el caso normal, no la excepcion.
     */
    public record Conversacion(
            Long id,
            Long otroUsuarioId,
            String otroUsuarioNombre,
            String otroUsuarioCargo,
            String otroUsuarioFoto,
            String ultimoMensaje,
            LocalDateTime fechaUltimoMensaje,
            long noLeidos,
            List<Mensaje> mensajes) {

        /**
         * La cabecera de la bandeja con los mensajes de un chat ya cargados.
         *
         * Se devuelve una copia en vez de mutar la que vino de la cabecera para
         * no arrastrar un `null` en la bandeja y tener que comprobarlo en
         * plantilla: la cabecera nunca trae mensajes, el chat siempre.
         */
        public Conversacion conMensajes(List<Mensaje> lista) {
            return new Conversacion(id, otroUsuarioId, otroUsuarioNombre, otroUsuarioCargo,
                    otroUsuarioFoto, ultimoMensaje, fechaUltimoMensaje, noLeidos, lista);
        }
    }

    /**
     * Un colega al que se le puede escribir.
     *
     * Es una ficha de la persona, no una conversacion: son las columnas de
     * `usuarios` mas el `ultimo_mensaje`, para que la lista lateral pueda
     * pintar a alguien con quien aun no se ha hablado nunca.
     *
     * El `ultimoMensaje` nullable es lo que separa "nunca hemos hablado" de
     * "el ultimo mensaje fue el mio": en el primer caso no hay preview, y en el
     * segundo lo hay pero sin no leidos. Con un `""` en los dos casos, la lista
     * no puede distinguir los dos estados.
     */
    public record Contacto(
            Long id,
            String nombre,
            String cargo,
            String foto,
            String ultimoMensaje,
            LocalDateTime fechaUltimoMensaje,
            long noLeidos) {
    }
}
