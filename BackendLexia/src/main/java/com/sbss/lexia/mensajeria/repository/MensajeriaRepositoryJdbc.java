package com.sbss.lexia.mensajeria.repository;

import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Conversacion;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Contacto;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.Mensaje;
import com.sbss.lexia.mensajeria.dto.MensajeriaDtos.PaginaContactos;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MensajeriaRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    private static final int CARRACTERES_MAXIMOS = 4000;

    /**
     * La bandeja del usuario: sus conversaciones, de mas reciente a mas vieja.
     *
     * La parte "quien es el otro" se resuelve con dos COALESCE, uno por
     * posicion, porque el par esta ordenado (menor, mayor) y no se sabe de
     * entrada en que lado cae quien pregunta. Es una condicion sobre dos
     * columnas en vez de un JOIN con `ON u.id <> :yo`, y por eso la consulta
     * devuelve exactamente una fila por conversacion y no se duplica.
     */
    public List<Conversacion> bandeja() {
        return jdbc.query("""
                SELECT c.id,
                       CASE WHEN c.usuario_a_id = :yo THEN c.usuario_b_id ELSE c.usuario_a_id END otro_id,
                       otro.nombre_completo otro_nombre, otro.cargo otro_cargo, otro.foto_url otro_foto,
                       c.ultimo_mensaje_texto, c.fecha_ultimo_mensaje,
                       (SELECT COUNT(*) FROM mensajes m
                         WHERE m.conversacion_id = c.id
                           AND m.emisor_id <> :yo
                           AND m.leido_por_id IS NULL) no_leidos
                  FROM conversaciones c
                  JOIN usuarios otro
                    ON otro.id = CASE WHEN c.usuario_a_id = :yo THEN c.usuario_b_id ELSE c.usuario_a_id END
                 WHERE c.activo = 1
                   AND (c.usuario_a_id = :yo OR c.usuario_b_id = :yo)
                 ORDER BY c.fecha_ultimo_mensaje DESC, c.id DESC
                """, Map.of("yo", SesionActual.usuarioId()),
                (rs, i) -> mapearConversacion(rs, null));
    }

    /**
     * Los mensajes de UNA conversacion, en orden de envio.
     *
     * El limite es el que protege al usuario, no al servidor: un chat de mil
     * mensajes se pinta de una vez solo si se pide una ventana. Se devuelven los
     * ultimos, y el front sube el scroll al final, que es donde esta lo nuevo.
     */
    /**
     * Consulta una conversacion sin modificar el estado de lectura.
     *
     * Este metodo tambien se usa para refrescar mensajes en segundo plano; por
     * tanto, un GET no debe marcar mensajes como leidos. La lectura se registra
     * explicitamente al abrir el hilo, mediante PATCH /leer, o cuando llegan
     * mensajes nuevos a un hilo que permanece visible.
     */
    @Transactional(readOnly = true)
    public Conversacion conversacion(long conversacionId, int limite) {
        List<Conversacion> cabecera = jdbc.query("""
                SELECT c.id,
                       CASE WHEN c.usuario_a_id = :yo THEN c.usuario_b_id ELSE c.usuario_a_id END otro_id,
                       otro.nombre_completo otro_nombre, otro.cargo otro_cargo, otro.foto_url otro_foto,
                       c.ultimo_mensaje_texto, c.fecha_ultimo_mensaje,
                       0 no_leidos
                  FROM conversaciones c
                  JOIN usuarios otro
                    ON otro.id = CASE WHEN c.usuario_a_id = :yo THEN c.usuario_b_id ELSE c.usuario_a_id END
                 WHERE c.id = :id AND c.activo = 1
                   AND (c.usuario_a_id = :yo OR c.usuario_b_id = :yo)
                """, new MapSqlParameterSource()
                .addValue("id", conversacionId)
                .addValue("yo", SesionActual.usuarioId()),
                (rs, i) -> mapearConversacion(rs, null));

        if (cabecera.isEmpty()) {
            throw new RecursoNoEncontradoException("La conversacion no existe o no es tuya");
        }

        List<Mensaje> mensajes = jdbc.query("""
                SELECT m.id, m.conversacion_id, m.emisor_id, u.nombre_completo emisor_nombre,
                       m.cuerpo, m.leido_por_id, m.fecha_creacion
                  FROM mensajes m
                  JOIN usuarios u ON u.id = m.emisor_id
                 WHERE m.conversacion_id = :id
                 ORDER BY m.id DESC
                 LIMIT :limite
                """, new MapSqlParameterSource()
                .addValue("id", conversacionId)
                .addValue("limite", Math.min(Math.max(limite, 1), 500)),
                (rs, i) -> new Mensaje(
                        rs.getLong("id"),
                        rs.getLong("conversacion_id"),
                        rs.getLong("emisor_id"),
                        rs.getString("emisor_nombre"),
                        rs.getLong("emisor_id") == SesionActual.usuarioId(),
                        rs.getString("cuerpo"),
                        rs.getObject("leido_por_id") != null,
                        rs.getTimestamp("fecha_creacion").toLocalDateTime()));

        // Venian en DESC (para que el LIMIT se lleve los ultimos); se devuelven
        // en ASC porque el chat los pinta de arriba a abajo.
        return cabecera.get(0).conMensajes(mensajes.reversed());
    }

    /**
     * Envia un mensaje.
     *
     * Crea la conversacion si no existia, y por eso va en transaccion con sus
     * tres escrituras: sin ella, un envio a un contacto nuevo puede dejar la
     * fila de `mensajes` apuntando a una conversacion que se deshizo, o dejar
     * el contador de `conversaciones` desactualizado.
     */
    @Transactional
    public Mensaje enviar(long destinatarioId, String cuerpo) {
        long emisor = SesionActual.usuarioId();
        long destino = destinatarioId;

        if (destino == emisor) {
            throw new ParametroInvalidoException("No puedes enviarte un mensaje a ti mismo");
        }

        String texto = validarCuerpo(cuerpo);
        validarDestinatario(destino);

        long conversacionId = buscarConversacionEntre(destino);
        if (conversacionId == 0) {
            conversacionId = crearConversacion(destino);
        }

        return guardarMensaje(conversacionId, emisor, destino, texto);
    }

    /**
     * Nuevo flujo preferido: envía sobre el ID de conversación que el usuario
     * abrió, no vuelve a resolver el destinatario a partir de un ID ambiguo.
     * La pertenencia y el estudio se comprueban de nuevo en el servidor antes
     * de insertar. Si la interfaz intenta enviar con una conversación distinta
     * a la seleccionada, la API rechaza la operación en lugar de cruzar chats.
     */
    @Transactional
    public Mensaje enviarEnConversacion(long conversacionId, String cuerpo) {
        long emisor = SesionActual.usuarioId();
        String texto = validarCuerpo(cuerpo);

        List<Long> destinos = jdbc.query("""
                SELECT CASE WHEN c.usuario_a_id = :yo
                            THEN c.usuario_b_id ELSE c.usuario_a_id END destinatario_id
                  FROM conversaciones c
                  JOIN usuarios destino
                    ON destino.id = CASE WHEN c.usuario_a_id = :yo
                                         THEN c.usuario_b_id ELSE c.usuario_a_id END
                 WHERE c.id = :conversacion
                   AND c.activo = 1
                   AND (c.usuario_a_id = :yo OR c.usuario_b_id = :yo)
                   AND destino.estudio_id = :estudio
                   AND destino.tipo_usuario = 'INTERNO'
                   AND destino.activo = 1
                   AND destino.estado = 'ACTIVO'
                """, new MapSqlParameterSource()
                .addValue("yo", emisor)
                .addValue("conversacion", conversacionId)
                .addValue("estudio", SesionActual.estudioId()),
                (rs, i) -> rs.getLong("destinatario_id"));

        if (destinos.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "La conversación no existe, no te pertenece o el destinatario ya no está disponible");
        }

        long destino = destinos.get(0);
        return guardarMensaje(conversacionId, emisor, destino, texto);
    }

    private String validarCuerpo(String cuerpo) {
        if (cuerpo == null) {
            throw new ParametroInvalidoException("El mensaje no puede estar vacío");
        }
        String texto = cuerpo.strip();
        if (texto.isEmpty() || texto.length() > CARRACTERES_MAXIMOS) {
            throw new ParametroInvalidoException("El mensaje no puede estar vacío ni pasar de "
                    + CARRACTERES_MAXIMOS + " caracteres");
        }
        return texto;
    }

    private void validarDestinatario(long destino) {
        Long existe = jdbc.queryForObject("""
                SELECT COUNT(*) FROM usuarios
                 WHERE id = :id
                   AND estudio_id = :estudio
                   AND tipo_usuario = 'INTERNO'
                   AND activo = 1
                   AND estado = 'ACTIVO'
                """, Map.of("id", destino, "estudio", SesionActual.estudioId()), Long.class);
        if (existe == null || existe == 0L) {
            throw new ParametroInvalidoException("El destinatario no existe o no pertenece al estudio");
        }
    }

    private Mensaje guardarMensaje(long conversacionId, long emisor, long destino, String texto) {
        long menor = Math.min(emisor, destino);

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO mensajes (conversacion_id, emisor_id, cuerpo)
                VALUES (:conversacion, :emisor, :cuerpo)
                """, new MapSqlParameterSource()
                .addValue("conversacion", conversacionId)
                .addValue("emisor", emisor)
                .addValue("cuerpo", texto), kh, new String[]{"id"});

        long mensajeId = kh.getKey() == null ? 0 : kh.getKey().longValue();

        // El puntero de lectura solo avanza para el emisor actual. La columna
        // procede de comparar su ID con el extremo menor del par validado.
        String columna = emisor == menor ? "leido_a_por_id" : "leido_b_por_id";
        jdbc.update("UPDATE conversaciones"
                        + " SET ultimo_mensaje_id = :mensaje,"
                        + "     ultimo_mensaje_texto = :texto,"
                        + "     fecha_ultimo_mensaje = NOW(3),"
                        + "     " + columna + " = GREATEST(" + columna + ", :mensaje)"
                        + " WHERE id = :conversacion",
                new MapSqlParameterSource()
                        .addValue("mensaje", mensajeId)
                        .addValue("texto", recorte(texto))
                        .addValue("conversacion", conversacionId));

        String nombreEmisor = jdbc.queryForObject(
                "SELECT nombre_completo FROM usuarios WHERE id = :id",
                Map.of("id", emisor), String.class);

        return new Mensaje(
                mensajeId, conversacionId, emisor, nombreEmisor, true, texto, false,
                LocalDateTime.now());
    }

    /**
     * Marca leido lo recibido en la conversacion hasta donde se abrio.
     *
     * La pertenencia SE COMPRUEBA AQUI, y no se da por hecha. Sin esta
     * comprobacion, quien no participa en la conversacion cae en la rama
     * `leido_b_por_id` y avanza el puntero de lectura DEL OTRO: no solo ve
     * mensajes ajenos, sino que los marca como leidos en su bandeja y le roba
     * el aviso. La columna se elige con un `esParteA` que devuelve false cuando
     * no es parte, asi que el fallo era silencioso.
     *
     * Son dos escrituras, y por eso van en la misma transaccion: si fallara la
     * segunda, los mensajes quedarian marcados en la tabla pero el puntero de la
     * conversacion no avanzaria, y la bandeja los seguiria mostrando como
     * nuevos para siempre.
     */
    @Transactional
    public long marcarLeida(long conversacionId) {
        long yo = SesionActual.usuarioId();

        Boolean parteA = usuarioA(conversacionId);

        if (parteA == null) {
            throw new RecursoNoEncontradoException("La conversacion no existe o no es tuya");
        }

        // COALESCE(MAX(id), 0) nunca es NULL, pero se declara Long para no
        // depender de eso: si alguien quita el COALESCE, el null pasa a ser un
        // NPE en el UPDATE en vez de un 0 que no marca nada.
        Long ultimo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(id), 0) FROM mensajes
                 WHERE conversacion_id = :id AND emisor_id <> :yo
                """, Map.of("id", conversacionId, "yo", yo), Long.class);

        if (ultimo == null) {
            ultimo = 0L;
        }

        jdbc.update("""
                UPDATE mensajes
                   SET leido_por_id = :yo, fecha_lectura = NOW(3)
                 WHERE conversacion_id = :id AND emisor_id <> :yo AND leido_por_id IS NULL
                """, Map.of("id", conversacionId, "yo", yo));

        String columna = parteA ? "leido_a_por_id" : "leido_b_por_id";
        jdbc.update("UPDATE conversaciones SET " + columna + " = :ultimo WHERE id = :id",
                Map.of("ultimo", ultimo, "id", conversacionId));

        return ultimo;
    }

    /**
     * `usuario_a_id` de la conversacion, o null si NO es del usuario de la sesion.
     *
     * La pertenencia va en el propio SELECT y no como un filtro aparte: así es
     * una consulta, no dos, y no hay forma de que se compruebe en un sitio y se
     * use en otro.
     */
    private Boolean usuarioA(long conversacionId) {
        return jdbc.query("""
                SELECT usuario_a_id FROM conversaciones
                 WHERE id = :id AND (usuario_a_id = :yo OR usuario_b_id = :yo)
                """, Map.of("id", conversacionId, "yo", SesionActual.usuarioId()),
                (rs, i) -> (Boolean) (rs.getLong("usuario_a_id") == SesionActual.usuarioId()))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * A quien se le puede escribir: los colleagues internos del mismo estudio.
     *
     * Sale de `usuarios` y no de `conversaciones` a proposito. Si se listaran
     * solo las conversaciones, la lista lateral seria una bolsa de mensajes
     * y no de personas: para escribirle a alguien habria que haberle escrito
     * antes, que es justo lo que impide empezar un chat.
     *
     * La conversacion se engancha por el par ordenado con LEAST/GREATEST, que
     * reproduce la misma regla que usa `enviar` al crearla. Si aqui se
     * emparejara con `usuario_a_id = :yo` a secas, la conversacion solo
     * apareceria para uno de los dos sentidos de la fila.
     *
     * Los filtros no son cosmetics: `tipo_usuario = 'INTERNO'` deja fuera al
     * usuario tipo CLIENTE, que en este estudio existe con el MISMO nombre que
     * una abogada interna. Sin ese filtro, la listauria dos "Ana Maria
     * Fernandez Salas" y el que stature en el sitio equivocado escribiendo al
     * cliente en vez de a la colega.
     *
     * El ORDER BY pone primero las conversaciones con movimiento reciente y
     * deja las fichas sin historial al final, en orden alfabetico. Sin el
     * primer termino, `c.fecha_ultimo_mensaje DESC` de MySQL pondria los NULL
     * delante en MySQL pero no en estandar, y la lista cambiaba de orden segun
     * como se escribiera la consulta.
     */
    public List<Contacto> contactos() {
        long yo = SesionActual.usuarioId();

        return jdbc.query("""
                SELECT u.id, c.id conversacion_id, u.nombre_completo, u.cargo, u.foto_url,
                       c.ultimo_mensaje_texto, c.fecha_ultimo_mensaje,
                       COALESCE((SELECT COUNT(*) FROM mensajes m
                                  WHERE m.conversacion_id = c.id
                                    AND m.emisor_id <> :yo
                                    AND m.leido_por_id IS NULL), 0) no_leidos
                  FROM usuarios u
                  LEFT JOIN conversaciones c
                    ON c.activo = 1
                   AND c.usuario_a_id = LEAST(:yo, u.id)
                   AND c.usuario_b_id = GREATEST(:yo, u.id)
                 WHERE u.estudio_id = :estudio
                   AND u.tipo_usuario = 'INTERNO'
                   AND u.activo = 1
                   AND u.estado = 'ACTIVO'
                   AND u.id <> :yo
                 ORDER BY (c.fecha_ultimo_mensaje IS NULL),
                          c.fecha_ultimo_mensaje DESC,
                          u.nombre_completo
                """,
                new MapSqlParameterSource()
                        .addValue("yo", yo)
                        .addValue("estudio", SesionActual.estudioId()),
                (rs, i) -> new Contacto(
                        rs.getLong("id"),
                        rs.getObject("conversacion_id", Long.class),
                        rs.getString("nombre_completo"),
                        rs.getString("cargo"),
                        rs.getString("foto_url"),
                        rs.getString("ultimo_mensaje_texto"),
                        rs.getTimestamp("fecha_ultimo_mensaje") == null
                                ? null : rs.getTimestamp("fecha_ultimo_mensaje").toLocalDateTime(),
                        rs.getLong("no_leidos")));
    }

    /**
     * Directorio paginado de contactos. Solo consulta una ventana de usuarios;
     * la búsqueda se filtra en MySQL y nunca descarga el directorio completo al
     * navegador. El ID de usuario y el ID de conversación se devuelven separados
     * para evitar confundir destinatarios con conversaciones.
     */
    public PaginaContactos contactos(int pagina, int tamano, String busqueda) {
        long yo = SesionActual.usuarioId();
        int page = Math.max(0, pagina);
        int size = Math.min(Math.max(tamano, 10), 50);
        long offset = (long) page * size;
        String termino = busqueda == null ? "" : busqueda.strip().toLowerCase();
        String patron = termino.isEmpty() ? "" : "%" + termino + "%";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("yo", yo)
                .addValue("estudio", SesionActual.estudioId())
                .addValue("buscar", patron)
                .addValue("limite", size)
                .addValue("offset", offset);

        List<Contacto> items = jdbc.query("""
                SELECT pagina.id,
                       pagina.conversacion_id,
                       pagina.nombre_completo,
                       pagina.cargo,
                       pagina.foto_url,
                       pagina.ultimo_mensaje_texto,
                       pagina.fecha_ultimo_mensaje,
                       COALESCE((
                           SELECT COUNT(*)
                             FROM mensajes m
                            WHERE m.conversacion_id = pagina.conversacion_id
                              AND m.emisor_id <> :yo
                              AND m.leido_por_id IS NULL
                       ), 0) no_leidos
                  FROM (
                       SELECT u.id,
                              c.id conversacion_id,
                              u.nombre_completo,
                              u.cargo,
                              u.foto_url,
                              c.ultimo_mensaje_texto,
                              c.fecha_ultimo_mensaje
                         FROM usuarios u
                         LEFT JOIN conversaciones c
                           ON c.activo = 1
                          AND c.usuario_a_id = LEAST(:yo, u.id)
                          AND c.usuario_b_id = GREATEST(:yo, u.id)
                        WHERE u.estudio_id = :estudio
                          AND u.tipo_usuario = 'INTERNO'
                          AND u.activo = 1
                          AND u.estado = 'ACTIVO'
                          AND u.id <> :yo
                          AND (:buscar = ''
                               OR LOWER(u.nombre_completo) LIKE :buscar
                               OR LOWER(COALESCE(u.email, '')) LIKE :buscar
                               OR LOWER(COALESCE(u.cargo, '')) LIKE :buscar)
                        ORDER BY (c.fecha_ultimo_mensaje IS NULL),
                                 c.fecha_ultimo_mensaje DESC,
                                 u.nombre_completo,
                                 u.id
                        LIMIT :limite OFFSET :offset
                  ) pagina
                 ORDER BY (pagina.fecha_ultimo_mensaje IS NULL),
                          pagina.fecha_ultimo_mensaje DESC,
                          pagina.nombre_completo,
                          pagina.id
                """, params, (rs, i) -> new Contacto(
                rs.getLong("id"),
                rs.getObject("conversacion_id", Long.class),
                rs.getString("nombre_completo"),
                rs.getString("cargo"),
                rs.getString("foto_url"),
                rs.getString("ultimo_mensaje_texto"),
                rs.getTimestamp("fecha_ultimo_mensaje") == null
                        ? null : rs.getTimestamp("fecha_ultimo_mensaje").toLocalDateTime(),
                rs.getLong("no_leidos")));

        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM usuarios u
                 WHERE u.estudio_id = :estudio
                   AND u.tipo_usuario = 'INTERNO'
                   AND u.activo = 1
                   AND u.estado = 'ACTIVO'
                   AND u.id <> :yo
                   AND (:buscar = ''
                        OR LOWER(u.nombre_completo) LIKE :buscar
                        OR LOWER(COALESCE(u.email, '')) LIKE :buscar
                        OR LOWER(COALESCE(u.cargo, '')) LIKE :buscar)
                """, new MapSqlParameterSource()
                .addValue("estudio", SesionActual.estudioId())
                .addValue("yo", yo)
                .addValue("buscar", patron), Long.class);

        long cantidad = total == null ? 0L : total;
        boolean hayMas = ((long) page + 1L) * size < cantidad;
        return new PaginaContactos(items, page, size, cantidad, hayMas);
    }

    /** Contador global de no leídos sin tener que descargar todos los contactos. */
    public long contarNoLeidos() {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM mensajes m
                  JOIN conversaciones c ON c.id = m.conversacion_id
                 WHERE c.activo = 1
                   AND (c.usuario_a_id = :yo OR c.usuario_b_id = :yo)
                   AND m.emisor_id <> :yo
                   AND m.leido_por_id IS NULL
                """, Map.of("yo", SesionActual.usuarioId()), Long.class);
        return total == null ? 0L : total;
    }

    /**
     * Crea la conversacion con otro usuario, y devuelve su id.
     *
     * Valida que el otro sea interno, activo y del mismo estudio ANTES de
     * insertar. Sin esta comprobacion, un id cualquiera crearia una
     * conversacion con una ficha vacia, y el nombre del otro saldria en blanco
     * en la bandeja. `Long`, no `long`: `queryForObject` devuelve null si no hay
     * fila, y desempaquetar un null en un primitivo reventaria con un NPE en
     * lugar de dar el mensaje de "destinatario invalido".
     *
     * La carrera entre dos envios simultaneos al mismo contacto nuevo la
     * resuelve el UNIQUE del par: el segundo INSERT revienta con
     * DuplicateKey, y la transaccion de `enviar` se deshace. Por eso el
     * INSERT va siempre por el camino de "no existia", y nunca sobre una
     * conversacion que ya se esta usando.
     */
    @Transactional
    public long crearConversacion(long otroUsuarioId) {
        long emisor = SesionActual.usuarioId();

        if (otroUsuarioId == emisor) {
            throw new ParametroInvalidoException("No puedes abrir un chat contigo mismo");
        }

        Long existe = jdbc.queryForObject("""
                SELECT COUNT(*) FROM usuarios
                 WHERE id = :id AND activo = 1 AND estado = 'ACTIVO'
                   AND tipo_usuario = 'INTERNO' AND estudio_id = :estudioId
                """, Map.of("id", otroUsuarioId, "estudioId", SesionActual.estudioId()), Long.class);

        if (existe == null || existe == 0L) {
            throw new ParametroInvalidoException("El destinatario no existe o no pertenece al estudio");
        }

        try {
            // Un hilo vacío no debe parecer una conversación reciente: la fecha
            // del último mensaje queda NULL hasta que realmente se envíe algo.
            jdbc.update("""
                    INSERT INTO conversaciones (usuario_a_id, usuario_b_id)
                    VALUES (:menor, :mayor)
                    """, Map.of("menor", Math.min(emisor, otroUsuarioId),
                            "mayor", Math.max(emisor, otroUsuarioId)));
        } catch (DuplicateKeyException carrera) {
            // Si ambos participantes abren el primer chat simultáneamente, el
            // UNIQUE del par deja una sola conversación. Recuperamos esa fila
            // en vez de mostrar un error o crear un hilo gemelo.
            long existente = buscarConversacionEntre(otroUsuarioId);
            if (existente > 0) return existente;
            throw new ParametroInvalidoException(
                    "No se pudo abrir la conversación. Comprueba que el contacto siga activo.");
        }

        long creada = buscarConversacionEntre(otroUsuarioId);
        if (creada == 0) {
            throw new RecursoNoEncontradoException("No se pudo recuperar la conversación recién creada");
        }
        return creada;
    }

    /**
     * La conversacion del usuario de la sesion con otro, o 0 si no hay ninguna.
     *
     * El par ordenado se resuelve con LEAST/GREATEST en la propia consulta, en
     * vez de calcularlo en Java y pasarlo por dos parametros. Es la misma
     * condicion que usa el LEFT JOIN de `contactos`, y tenerlas en un solo
     * sitio es lo que impide que las dos se desincronicen.
     */
    public long buscarConversacionEntre(long otroUsuarioId) {
        return jdbc.query("""
                SELECT c.id FROM conversaciones c
                 WHERE c.activo = 1
                   AND c.usuario_a_id = LEAST(:yo, :otro)
                   AND c.usuario_b_id = GREATEST(:yo, :otro)
                """, Map.of("yo", SesionActual.usuarioId(), "otro", otroUsuarioId),
                (rs, i) -> rs.getLong("id"))
                .stream()
                .findFirst()
                .orElse(0L);
    }

    /**
     * Mapear la cabecera de la bandeja y de un chat.
     *
     * Las dos consultas proyectan las MISMAS columnas, incluida `no_leidos`.
     * Que la del chat proyecte un 0 es correcto y no es un atajo: el endpoint de
     * abrir el chat marca la conversacion como leida ANTES de devolverla, asi
     * que al serializar no queda nada pendiente por definición.
     *
     * `no_leidos` llega como COUNT desde la consulta, nunca como
     * `ultimo_mensaje_id - leido_hasta`. Los id de `mensajes` son una
     * auto_increment GLOBAL, no por conversacion: restar dos punteros cuenta
     * como no leidos todos los mensajes que el otro usuario escribio en el
     * resto de conversaciones. Con una conversacion de un solo mensajeFORDANDE
     * ids altos, el contador daba 4 sin leidos.
     */
    private Conversacion mapearConversacion(ResultSet rs, List<Mensaje> mensajes) throws SQLException {
        return new Conversacion(
                rs.getLong("id"),
                rs.getLong("otro_id"),
                rs.getString("otro_nombre"),
                rs.getString("otro_cargo"),
                rs.getString("otro_foto"),
                rs.getString("ultimo_mensaje_texto"),
                rs.getTimestamp("fecha_ultimo_mensaje") == null
                        ? null : rs.getTimestamp("fecha_ultimo_mensaje").toLocalDateTime(),
                rs.getLong("no_leidos"),
                mensajes);
    }

    /** El preview de la bandeja no necesita el mensaje entero. */
    private String recorte(String texto) {
        return texto.length() > 500 ? texto.substring(0, 497) + "..." : texto;
    }
}
