package com.sbss.lexia.mensajeria.controller;

import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Conversacion;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Contacto;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Mensaje;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.PaginaContactos;
import com.sbss.lexia.mensajeria.service.MensajeriaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mensajeria interna.
 *
 * No hay `DELETE` de mensajes. Borrarlos haria que "lo que te dijo" dependiera
 * de cuando se mire, y en un estudio la trazabilidad de un intercambio es
 * justamente lo que importa; ademas, un unico `DELETE` sin filtro por
 * conversación seria un `DELETE` de toda la caja de entrada.
 *
 * El destinatario se manda como `destinatarioId` y no como uuid porque el
 * backend ya resuelve el usuario desde la sesion y comprueba que destino y
 * emisor sean del mismo estudio en la misma consulta que valida al destinatario.
 * Aceptar un id ajeno solo para rechazarlo despues seria confiar en el cliente
 * para una decision que el servidor tiene que tomar igual.
 */
@RestController
@RequestMapping("/api/v1/mensajeria")
@RequiredArgsConstructor
public class MensajeriaController {

    private final MensajeriaService service;
    private final com.sbss.lexia.mensajeria.service.MensajeriaEventosService eventos;

    @GetMapping(value = "/eventos", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter eventos() {
        return eventos.conectar(com.sbss.lexia.shared.security.SesionActual.usuarioId());
    }

    @GetMapping("/bandeja")
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public List<Conversacion> bandeja() {
        return service.bandeja();
    }

    /**
     * A quien se le puede escribir: los colleagues internos del estudio.
     *
     * Va antes que el chat en la interfaz porque sin esta lista no hay con quien
     * empezar una conversacion, y una bandeja solo muestra las que ya existen.
     */
    @GetMapping("/contactos")
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public List<Contacto> contactos() {
        // Compatibilidad con consumidores anteriores. El dashboard usa la ruta
        // paginada para no cargar el directorio completo.
        return service.contactos();
    }

    @GetMapping("/contactos/pagina")
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public PaginaContactos contactosPagina(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamano,
            @RequestParam(required = false) String busqueda) {
        return service.contactos(Math.max(0, pagina), Math.max(1, Math.min(tamano, 100)), busqueda);
    }

    @GetMapping("/no-leidos")
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public long contarNoLeidos() {
        return service.contarNoLeidos();
    }

    @GetMapping("/conversaciones/{id}")
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public Conversacion conversacion(@PathVariable long id,
                                     @RequestParam(defaultValue = "100") int limite) {
        return service.conversacion(id, Math.max(1, Math.min(limite, 100)));
    }

    /**
     * Abre el chat con un colega por su id de USUARIO, no de conversacion.
     *
     * Es la distincion que hace que la lista lateral pueda contener gente con
     * la que nunca se ha hablado. La interfaz guarda el id del contacto, que es
     * el unico identificador que tiene antes de que exista la conversacion; en
     * cuanto existe, el mismo id sigue siendo valido porque la conversacion
     * tiene un por contacto, no una por participant.
     */
    @GetMapping("/contactos/{contactoId}/conversacion")
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public Conversacion conversacionConContacto(@PathVariable long contactoId,
                                               @RequestParam(defaultValue = "100") int limite) {
        return service.abrirConContacto(contactoId, Math.max(1, Math.min(limite, 100)));
    }

    @PostMapping("/mensajes")
    @PreAuthorize("hasAuthority('MENSAJERIA_ENVIAR')")
    public Mensaje enviar(@Valid @RequestBody EnviarMensajeRequest peticion) {
        return service.enviar(peticion.destinatarioId(), peticion.cuerpo());
    }

    /**
     * Envío vinculado explícitamente al hilo abierto. El backend valida que el
     * usuario autenticado participe en esa conversación y deduce al destinatario
     * del par de participantes; el cliente ya no puede cruzar un mensaje al
     * mandar accidentalmente un ID de usuario en lugar de un ID de conversación.
     */
    @PostMapping("/conversaciones/{id}/mensajes")
    @PreAuthorize("hasAuthority('MENSAJERIA_ENVIAR')")
    public Mensaje enviarEnConversacion(
            @PathVariable long id,
            @Valid @RequestBody EnviarMensajeConversacionRequest peticion) {
        return service.enviarEnConversacion(id, peticion.cuerpo());
    }

    @PatchMapping("/conversaciones/{id}/leer")
    @PreAuthorize("hasAuthority('MENSAJERIA_LEER')")
    public long marcarLeida(@PathVariable long id) {
        return service.marcarLeida(id);
    }

    /**
     * El limite de 4000 caracteres esta en los dos sitios a proposito: el
     * `@Size` da el mensaje de error al usuario en el formulario, y el del
     * servicio protege a quien llame la API sin pasar por ahi.
     */
    public record EnviarMensajeRequest(
            @NotNull(message = "Falta el destinatario")
            Long destinatarioId,
            @NotBlank(message = "El mensaje no puede estar vacio")
            @Size(max = 4000, message = "El mensaje no puede pasar de 4000 caracteres")
            String cuerpo) {
    }

    public record EnviarMensajeConversacionRequest(
            @NotBlank(message = "El mensaje no puede estar vacio")
            @Size(max = 4000, message = "El mensaje no puede pasar de 4000 caracteres")
            String cuerpo) {
    }
}
