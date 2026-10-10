package com.sbss.lexia.busqueda.repository;

import com.sbss.lexia.busqueda.dto.BusquedaDtos.Resultado;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Buscador global.
 *
 * Cada tipo de entidad aporta su propia consulta y el repositorio las
 * ejecuta todas y las junta. No se hace un `UNION` gigante: las ocho ramas
 * tienen columnas distintas y una sola consulta unificada arrastraria el
 * resultado entero a un solo formato. Se ejecutan por separado y se combinan
 * en Java, que ademas es lo que permite limitar cada tipo por separado.
 *
 * <p><b>Por que LIKE y no el indice FULLTEXT.</b> Para DNI o RUC hace falta
 * coincidencia por prefijo o por numero exacto, y un `MATCH ... AGAINST`
 * descarta el 80% de un documento. `LIKE 'texto%'` sobre columnas indexadas
 * aguanta de sobra el volumen de un estudio juridico; `MATCH` se reserva para
 * la busqueda por nombre, donde de verdad aporta.
 */
@Repository
@RequiredArgsConstructor
public class BusquedaRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    /** Cuantos resultados se devuelven por tipo, como maximo. */
    private static final int LIMITE_POR_TIPO = 8;

    /**
     * Cuantos caracteres tiene que tener el texto para buscar.
     *
     * Sin un minimo, teclear "a" en la barra dispara las siete consultas y
     * devuelve casi todo el estudio. Con tres, un nombre corto ("Ana") sigue
     * funcionando y una pulsacion suelta no satura la base.
     */
    public static final int MIN_CARACTERES = 3;

    public List<Resultado> expedientes(String texto) {
        MapSqlParameterSource p = conVisibilidad(new MapSqlParameterSource()
                .addValue("texto", texto + "%")
                .addValue("textoLargo", "%" + texto + "%")
                .addValue("limite", LIMITE_POR_TIPO));

        return jdbc.query("""
                SELECT e.uuid_publico, e.id expediente_id, e.codigo_interno,
                       e.numero_expediente, c.nombre_busqueda cliente,
                       u.nombre_completo abogado, e.fecha_ultima_actuacion,
                       CASE
                         WHEN e.codigo_interno LIKE :texto THEN e.codigo_interno
                         WHEN e.numero_expediente LIKE :texto THEN e.numero_expediente
                         ELSE c.nombre_busqueda
                       END coincidencia
                  FROM expedientes e
                  JOIN clientes c ON c.id = e.cliente_id
                  JOIN usuarios u ON u.id = e.abogado_responsable_id
                 WHERE (e.codigo_interno LIKE :texto
                     OR e.numero_expediente LIKE :texto
                     OR c.nombre_busqueda LIKE :textoLargo)
                """ + VisibilidadExpedientes.sql("e") + """
                 ORDER BY e.codigo_interno
                 LIMIT :limite
                """, p, (rs, i) -> new Resultado(
                "EXPEDIENTE", "Expedientes",
                rs.getString("codigo_interno"),
                rs.getString("cliente"),
                "/expedientes",
                rs.getLong("expediente_id"),
                rs.getString("codigo_interno"),
                rs.getString("coincidencia"),
                rs.getTimestamp("fecha_ultima_actuacion") == null
                        ? null : rs.getTimestamp("fecha_ultima_actuacion").toLocalDateTime()));
    }

    /**
     * El cliente es del estudio entero (CLIENTE_LEER), pero el numero de sus
     * expedientes cuenta solo los que el usuario puede abrir: contar tambien los
     * confidenciales ajenos revelaria que existen.
     */
    public List<Resultado> clientes(String texto) {
        return jdbc.query("""
                SELECT c.uuid_publico, c.numero_documento, c.nombre_busqueda,
                       c.tipo_persona, c.estado,
                       (SELECT COUNT(*) FROM expedientes ex
                         WHERE ex.cliente_id = c.id
                """ + VisibilidadExpedientes.sql("ex") + """
                       ) expedientes
                  FROM clientes c
                 WHERE c.estudio_id = :estudioId
                   AND c.activo = 1
                   AND (c.numero_documento LIKE :texto
                     OR c.nombre_busqueda LIKE :textoLargo)
                 ORDER BY c.nombre_busqueda
                 LIMIT :limite
                """, conVisibilidad(base(texto)), (rs, i) -> new Resultado(
                "CLIENTE", "Clientes",
                rs.getString("nombre_busqueda"),
                rs.getString("numero_documento")
                        + (rs.getInt("expedientes") > 0
                           ? " · " + rs.getInt("expedientes") + " expediente(s)" : ""),
                "/clientes",
                null, null,
                rs.getString("numero_documento"),
                null));
    }

    /**
     * La contraparte se busca en todo el estudio: es lo que permite detectar un
     * conflicto de intereses antes de aceptar un caso. Lo que NO puede salir es
     * el expediente que la enlaza si el usuario no lo ve; por eso el codigo va
     * en una subconsulta con la regla de visibilidad, y no en un LEFT JOIN, que
     * ademas repetia la contraparte una vez por expediente.
     */
    public List<Resultado> contrapartes(String texto) {
        return jdbc.query("""
                SELECT cp.uuid_publico, cp.nombre_completo, cp.numero_documento,
                       cp.abogado_contrario, cp.empresa_vinculada,
                       (SELECT e.codigo_interno
                          FROM expediente_contrapartes ec
                          JOIN expedientes e ON e.id = ec.expediente_id
                         WHERE ec.contraparte_id = cp.id
                """ + VisibilidadExpedientes.sql("e") + """
                         ORDER BY e.codigo_interno
                         LIMIT 1) codigo_interno
                  FROM contrapartes cp
                 WHERE cp.estudio_id = :estudioId
                   AND cp.activo = 1
                   AND (cp.numero_documento LIKE :texto
                     OR cp.nombre_completo LIKE :textoLargo
                     OR cp.abogado_contrario LIKE :textoLargo)
                 ORDER BY cp.nombre_completo
                 LIMIT :limite
                """, conVisibilidad(base(texto)), (rs, i) -> new Resultado(
                "CONTRAPARTE", "Contrapartes",
                rs.getString("nombre_completo"),
                rs.getString("abogado_contrario") != null
                        ? "Abogado contrario: " + rs.getString("abogado_contrario")
                        : rs.getString("empresa_vinculada"),
                "/expedientes",
                null, rs.getString("codigo_interno"),
                rs.getString("numero_documento"),
                null));
    }

    public List<Resultado> resoluciones(String texto) {
        MapSqlParameterSource p = conVisibilidad(new MapSqlParameterSource()
                .addValue("texto", texto + "%")
                .addValue("textoLargo", "%" + texto + "%")
                .addValue("limite", LIMITE_POR_TIPO));

        return jdbc.query("""
                SELECT r.uuid_publico, r.numero_resolucion, r.tipo_resolucion,
                       r.sumilla, r.fecha_resolucion, r.fecha_notificacion,
                       e.id expediente_id, e.codigo_interno
                  FROM resoluciones r
                  JOIN expedientes e ON e.id = r.expediente_id
                 WHERE r.activo = 1
                   AND (r.numero_resolucion LIKE :texto OR r.sumilla LIKE :textoLargo)
                """ + VisibilidadExpedientes.sql("e") + """
                 ORDER BY r.fecha_notificacion DESC
                 LIMIT :limite
                """, p, (rs, i) -> new Resultado(
                "RESOLUCION", "Resoluciones",
                rs.getString("numero_resolucion"),
                rs.getString("codigo_interno")
                        + (rs.getString("tipo_resolucion") != null
                           ? " · " + rs.getString("tipo_resolucion") : ""),
                "/expedientes",
                rs.getLong("expediente_id"),
                rs.getString("codigo_interno"),
                 rs.getString("numero_resolucion"),
                 instante(rs.getDate("fecha_notificacion"))));
    }

    public List<Resultado> actuaciones(String texto) {
        MapSqlParameterSource p = conVisibilidad(new MapSqlParameterSource()
                .addValue("texto", texto + "%")
                .addValue("textoLargo", "%" + texto + "%")
                .addValue("limite", LIMITE_POR_TIPO));

        return jdbc.query("""
                SELECT a.uuid_publico, a.asunto, a.descripcion, a.fecha_hora,
                       e.id expediente_id, e.codigo_interno, c.nombre_busqueda cliente,
                       u.nombre_completo responsable
                  FROM actuaciones a
                  JOIN expedientes e ON e.id = a.expediente_id
                  JOIN clientes c ON c.id = e.cliente_id
                  JOIN usuarios u ON u.id = a.usuario_responsable_id
                 WHERE a.activo = 1
                   AND (a.asunto LIKE :texto OR a.descripcion LIKE :textoLargo)
                """ + VisibilidadExpedientes.sql("e") + """
                 ORDER BY a.fecha_hora DESC
                 LIMIT :limite
                """, p, (rs, i) -> new Resultado(
                "ACTUACION", "Actuaciones",
                rs.getString("asunto"),
                rs.getString("codigo_interno") + " · " + rs.getString("cliente"),
                "/actuaciones",
                rs.getLong("expediente_id"),
                rs.getString("codigo_interno"),
                rs.getString("asunto"),
                rs.getTimestamp("fecha_hora") == null
                        ? null : rs.getTimestamp("fecha_hora").toLocalDateTime()));
    }

    public List<Resultado> documentos(String texto) {
        MapSqlParameterSource p = conVisibilidad(new MapSqlParameterSource()
                .addValue("textoLargo", "%" + texto + "%")
                .addValue("limite", LIMITE_POR_TIPO));

        return jdbc.query("""
                SELECT d.uuid_publico, d.nombre, d.categoria, d.estado,
                       e.id expediente_id, e.codigo_interno
                  FROM documentos d
                  JOIN expedientes e ON e.id = d.expediente_id
                 WHERE d.activo = 1
                   AND d.en_papelera = 0
                   AND d.nombre LIKE :textoLargo
                """ + VisibilidadExpedientes.sql("e") + """
                 ORDER BY d.nombre
                 LIMIT :limite
                """, p, (rs, i) -> new Resultado(
                "DOCUMENTO", "Documentos",
                rs.getString("nombre"),
                rs.getString("codigo_interno"),
                "/documentos",
                rs.getLong("expediente_id"),
                rs.getString("codigo_interno"),
                rs.getString("nombre"),
                null));
    }

    /**
     * Solo personal interno: las cuentas del portal del cliente tambien viven en
     * `usuarios` y salian listadas como "Abogados". El contador sigue la regla
     * de visibilidad, como el de clientes. Y el DNI del personal solo lo busca
     * quien ve todo el estudio: con DASHBOARD_LEER entra hasta recepcion.
     */
    public List<Resultado> abogados(String texto) {
        String porDocumento = VisibilidadExpedientes.verTodos()
                ? " OR u.numero_documento LIKE :texto"
                : "";
        return jdbc.query("""
                SELECT u.uuid_publico, u.nombre_completo, u.cargo, u.email,
                       (SELECT COUNT(*) FROM expedientes e
                         WHERE e.abogado_responsable_id = u.id
                """ + VisibilidadExpedientes.sql("e") + """
                       ) expedientes
                  FROM usuarios u
                 WHERE u.estudio_id = :estudioId
                   AND u.activo = 1
                   AND u.tipo_usuario = 'INTERNO'
                   AND (u.nombre_completo LIKE :textoLargo
                """ + porDocumento + """
                       )
                 ORDER BY u.nombre_completo
                 LIMIT :limite
                """, conVisibilidad(base(texto)), (rs, i) -> new Resultado(
                "ABOGADO", "Abogados",
                rs.getString("nombre_completo"),
                (rs.getString("cargo") != null ? rs.getString("cargo") : "")
                        + " · " + rs.getInt("expedientes") + " expediente(s)",
                "/usuarios",
                null, null,
                rs.getString("nombre_completo"),
                null));
    }

    /**
     * Parametros comunes de clientes, contrapartes y abogados.
     *
     * Esas filas son del estudio entero y las acota `estudio_id`; la regla de
     * visibilidad entra solo en lo que cada una dice de sus EXPEDIENTES (un
     * contador, un codigo), y por eso las tres pasan ademas por conVisibilidad().
     */
    private MapSqlParameterSource base(String texto) {
        return new MapSqlParameterSource()
                .addValue("estudioId", SesionActual.estudioId())
                .addValue("texto", texto + "%")
                .addValue("textoLargo", "%" + texto + "%")
                .addValue("limite", LIMITE_POR_TIPO);
    }

    /**
     * Une los parametros propios de una consulta con los de la regla de
     * visibilidad, en un Map MUTABLE.
     *
     * No se puede hacer `VisibilidadExpedientes.parametros(p.getValues())`:
     * `parametros()` hace `put()` sobre el mapa que recibe, y la vista que
     * devuelve `MapSqlParameterSource.getValues()` es inmutable. Revienta con
     * UnsupportedOperationException en la primera peticion, y solo en las
     * ramas que necesitan `visUsuarioId` o `visClienteId`.
     */
    private MapSqlParameterSource conVisibilidad(MapSqlParameterSource propio) {
        Map<String, Object> valores = new HashMap<>(propio.getValues());
        VisibilidadExpedientes.parametros(valores);
        return new MapSqlParameterSource(valores);
    }

    /** `java.sql.Date` no trae `atStartOfDay`; el DTO pide `LocalDateTime`. */
    private LocalDateTime instante(java.sql.Date fecha) {
        return fecha == null ? null : fecha.toLocalDate().atStartOfDay();
    }
}
