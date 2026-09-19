package com.sbss.lexia.clientes.repository;

import com.sbss.lexia.clientes.dto.ClienteGestionDtos.AbogadoOpcion;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.CoincidenciaConflicto;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.Comunicacion;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.Contacto;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.ContactoInput;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.ExpedienteVinculado;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.Vinculo;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.VinculoInput;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ClienteGestionRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    public void reemplazarContactos(Long clienteId, Long usuarioId, List<ContactoInput> contactos) {
        Map<String, Object> base = Map.of("clienteId", clienteId, "usuarioId", usuarioId);
        jdbc.update("""
                UPDATE cliente_contactos
                   SET activo = 0, fecha_modificacion = NOW(3)
                 WHERE cliente_id = :clienteId AND activo = 1
                """, base);

        if (contactos == null) return;
        for (ContactoInput c : contactos) {
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("clienteId", clienteId)
                    .addValue("nombre", limpio(c.nombreCompleto()))
                    .addValue("tipoDocumento", nuloSiVacio(c.tipoDocumento()))
                    .addValue("numeroDocumento", nuloSiVacio(c.numeroDocumento()))
                    .addValue("cargo", nuloSiVacio(c.cargo()))
                    .addValue("email", nuloSiVacio(c.email()))
                    .addValue("telefono", nuloSiVacio(c.telefono()))
                    .addValue("representante", c.representanteLegal())
                    .addValue("recibe", c.puedeRecibirDocumentos())
                    .addValue("poder", nuloSiVacio(c.numeroPoder()))
                    .addValue("usuarioId", usuarioId);
            jdbc.update("""
                    INSERT INTO cliente_contactos
                        (cliente_id, nombre_completo, tipo_documento, numero_documento,
                         cargo, email, telefono, es_representante_legal,
                         puede_recibir_documentos, numero_poder, usuario_creacion_id)
                    VALUES
                        (:clienteId, :nombre, :tipoDocumento, :numeroDocumento,
                         :cargo, :email, :telefono, :representante,
                         :recibe, :poder, :usuarioId)
                    """, p);
        }
    }

    public void reemplazarVinculos(Long clienteId, Integer estudioId, Long usuarioId, List<VinculoInput> vinculados) {
        jdbc.update("""
                UPDATE cliente_vinculos
                   SET activo = 0, fecha_modificacion = NOW(3), usuario_modificacion_id = :usuarioId
                 WHERE cliente_id = :clienteId AND estudio_id = :estudioId AND activo = 1
                """, Map.of("clienteId", clienteId, "estudioId", estudioId, "usuarioId", usuarioId));

        if (vinculados == null) return;
        for (VinculoInput v : vinculados) {
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("clienteId", clienteId)
                    .addValue("estudioId", estudioId)
                    .addValue("tipoPersona", normalizar(v.tipoPersona()))
                    .addValue("nombre", limpio(v.nombre()))
                    .addValue("tipoDocumento", nuloSiVacio(v.tipoDocumento()))
                    .addValue("numeroDocumento", nuloSiVacio(v.numeroDocumento()))
                    .addValue("relacion", nuloSiVacio(v.relacion()))
                    .addValue("observaciones", nuloSiVacio(v.observaciones()))
                    .addValue("usuarioId", usuarioId);
            jdbc.update("""
                    INSERT INTO cliente_vinculos
                        (cliente_id, estudio_id, tipo_persona, nombre, tipo_documento,
                         numero_documento, relacion, observaciones, usuario_creacion_id)
                    VALUES
                        (:clienteId, :estudioId, :tipoPersona, :nombre, :tipoDocumento,
                         :numeroDocumento, :relacion, :observaciones, :usuarioId)
                    """, p);
        }
    }

    public List<Contacto> contactos(Long clienteId) {
        return jdbc.query("""
                SELECT nombre_completo, tipo_documento, numero_documento, cargo,
                       email, telefono, es_representante_legal,
                       puede_recibir_documentos, numero_poder
                  FROM cliente_contactos
                 WHERE cliente_id = :clienteId AND activo = 1
                 ORDER BY es_representante_legal DESC, nombre_completo
                """, Map.of("clienteId", clienteId), (rs, n) -> new Contacto(
                rs.getString("nombre_completo"), rs.getString("tipo_documento"),
                rs.getString("numero_documento"), rs.getString("cargo"),
                rs.getString("email"), rs.getString("telefono"),
                rs.getBoolean("es_representante_legal"), rs.getBoolean("puede_recibir_documentos"),
                rs.getString("numero_poder")));
    }

    public List<Vinculo> vinculados(Long clienteId, Integer estudioId) {
        return jdbc.query("""
                SELECT tipo_persona, nombre, tipo_documento, numero_documento, relacion, observaciones
                  FROM cliente_vinculos
                 WHERE cliente_id = :clienteId AND estudio_id = :estudioId AND activo = 1
                 ORDER BY nombre
                """, Map.of("clienteId", clienteId, "estudioId", estudioId), (rs, n) -> new Vinculo(
                rs.getString("tipo_persona"), rs.getString("nombre"),
                rs.getString("tipo_documento"), rs.getString("numero_documento"),
                rs.getString("relacion"), rs.getString("observaciones")));
    }

    public List<ExpedienteVinculado> expedientes(Long clienteId, Integer estudioId) {
        return jdbc.query("""
                SELECT e.uuid_publico, e.codigo_interno, e.numero_expediente,
                       COALESCE(m.nombre, 'Sin materia') materia,
                       ee.nombre estado, ee.color_hex estado_color,
                       u.nombre_completo responsable, e.fecha_inicio, e.fecha_ultima_actuacion
                  FROM expedientes e
                  LEFT JOIN materias m ON m.id = e.materia_id
                  JOIN estados_expediente ee ON ee.id = e.estado_id
                  JOIN usuarios u ON u.id = e.abogado_responsable_id
                 WHERE e.cliente_id = :clienteId AND e.estudio_id = :estudioId AND e.activo = 1
                 ORDER BY COALESCE(e.fecha_ultima_actuacion, e.fecha_creacion) DESC
                """, Map.of("clienteId", clienteId, "estudioId", estudioId), (rs, n) -> new ExpedienteVinculado(
                rs.getString("uuid_publico"), rs.getString("codigo_interno"),
                rs.getString("numero_expediente"), rs.getString("materia"),
                rs.getString("estado"), rs.getString("estado_color"),
                rs.getString("responsable"), rs.getDate("fecha_inicio").toLocalDate(),
                rs.getTimestamp("fecha_ultima_actuacion") == null
                        ? null : rs.getTimestamp("fecha_ultima_actuacion").toLocalDateTime()));
    }

    public List<Comunicacion> comunicaciones(Long clienteId, Integer estudioId) {
        return jdbc.query("""
                SELECT cc.uuid_publico, cc.tipo, cc.asunto, cc.detalle, cc.canal,
                       cc.fecha_comunicacion, COALESCE(u.nombre_completo, 'Sistema') registrado_por
                  FROM cliente_comunicaciones cc
                  LEFT JOIN usuarios u ON u.id = cc.usuario_creacion_id
                 WHERE cc.cliente_id = :clienteId AND cc.estudio_id = :estudioId AND cc.activo = 1
                 ORDER BY cc.fecha_comunicacion DESC, cc.id DESC
                 LIMIT 100
                """, Map.of("clienteId", clienteId, "estudioId", estudioId), (rs, n) -> new Comunicacion(
                rs.getString("uuid_publico"), rs.getString("tipo"), rs.getString("asunto"),
                rs.getString("detalle"), rs.getString("canal"),
                rs.getTimestamp("fecha_comunicacion").toLocalDateTime(), rs.getString("registrado_por")));
    }

    public String registrarComunicacion(
            Long clienteId, Integer estudioId, Long usuarioId,
            String tipo, String asunto, String detalle, String canal, LocalDateTime fecha) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("clienteId", clienteId)
                .addValue("estudioId", estudioId)
                .addValue("tipo", normalizar(tipo))
                .addValue("asunto", limpio(asunto))
                .addValue("detalle", nuloSiVacio(detalle))
                .addValue("canal", nuloSiVacio(canal))
                .addValue("fecha", fecha)
                .addValue("usuarioId", usuarioId);
        jdbc.update("""
                INSERT INTO cliente_comunicaciones
                    (cliente_id, estudio_id, tipo, asunto, detalle, canal,
                     fecha_comunicacion, usuario_creacion_id)
                VALUES
                    (:clienteId, :estudioId, :tipo, :asunto, :detalle, :canal,
                     COALESCE(:fecha, NOW(3)), :usuarioId)
                """, p);
        return jdbc.queryForObject("SELECT uuid_publico FROM cliente_comunicaciones WHERE id = LAST_INSERT_ID()",
                Map.of(), String.class);
    }

    public List<AbogadoOpcion> abogados(Integer estudioId) {
        return jdbc.query("""
                SELECT DISTINCT u.uuid_publico, u.nombre_completo, u.cargo
                  FROM usuarios u
                  JOIN usuarios_roles ur ON ur.usuario_id = u.id
                  JOIN roles r ON r.id = ur.rol_id
                 WHERE u.estudio_id = :estudioId
                   AND u.activo = 1 AND u.estado = 'ACTIVO' AND u.tipo_usuario = 'INTERNO'
                   AND r.codigo IN ('ADMINISTRADOR','SOCIO','ABOGADO','ABOGADO_AUXILIAR')
                 ORDER BY u.nombre_completo
                """, Map.of("estudioId", estudioId), (rs, n) -> new AbogadoOpcion(
                rs.getString("uuid_publico"), rs.getString("nombre_completo"), rs.getString("cargo")));
    }

    public List<CoincidenciaConflicto> buscarConflictos(
            Integer estudioId, String nombre, String numeroDocumento, String excluirClienteUuid, String terminoBuscado) {
        String n = nuloSiVacio(nombre);
        String d = nuloSiVacio(numeroDocumento);
        if (n == null && d == null) return List.of();

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("estudioId", estudioId)
                .addValue("nombre", n == null ? null : "%" + n + "%")
                .addValue("documento", d)
                .addValue("excluir", nuloSiVacio(excluirClienteUuid))
                .addValue("termino", terminoBuscado);

        return jdbc.query("""
                SELECT origen, referencia_uuid, nombre, documento, relacion,
                       CASE WHEN :documento IS NOT NULL AND numero_documento = :documento
                            THEN 'DOCUMENTO_EXACTO' ELSE 'NOMBRE_RELACIONADO' END tipo_coincidencia,
                       :termino termino_buscado
                  FROM (
                    SELECT 'CLIENTE' origen, c.uuid_publico referencia_uuid,
                           c.nombre_busqueda nombre,
                           CONCAT_WS(' ', c.tipo_documento, c.numero_documento) documento,
                           c.numero_documento,
                           CONCAT('Cliente ', LOWER(c.estado)) relacion
                      FROM clientes c
                     WHERE c.estudio_id = :estudioId AND c.activo = 1
                       AND (:excluir IS NULL OR c.uuid_publico <> :excluir)
                       AND ((:documento IS NOT NULL AND c.numero_documento = :documento)
                         OR (:nombre IS NOT NULL AND c.nombre_busqueda LIKE :nombre))
                    UNION ALL
                    SELECT 'CONTACTO_CLIENTE', c.uuid_publico, cc.nombre_completo,
                           CONCAT_WS(' ', cc.tipo_documento, cc.numero_documento),
                           cc.numero_documento,
                           CONCAT('Contacto de ', c.nombre_busqueda,
                                  CASE WHEN cc.es_representante_legal = 1 THEN ' · representante legal' ELSE '' END)
                      FROM cliente_contactos cc
                      JOIN clientes c ON c.id = cc.cliente_id
                     WHERE c.estudio_id = :estudioId AND c.activo = 1 AND cc.activo = 1
                       AND (:excluir IS NULL OR c.uuid_publico <> :excluir)
                       AND ((:documento IS NOT NULL AND cc.numero_documento = :documento)
                         OR (:nombre IS NOT NULL AND cc.nombre_completo LIKE :nombre))
                    UNION ALL
                    SELECT 'CONTRAPARTE', cp.uuid_publico, cp.nombre_completo,
                           CONCAT_WS(' ', cp.tipo_documento, cp.numero_documento),
                           cp.numero_documento,
                           COALESCE(CONCAT('Contraparte · ', cp.empresa_vinculada), 'Contraparte registrada')
                      FROM contrapartes cp
                     WHERE cp.estudio_id = :estudioId AND cp.activo = 1
                       AND ((:documento IS NOT NULL AND cp.numero_documento = :documento)
                         OR (:nombre IS NOT NULL AND (cp.nombre_completo LIKE :nombre OR cp.empresa_vinculada LIKE :nombre)))
                    UNION ALL
                    SELECT 'VINCULADO_CLIENTE', c.uuid_publico, cv.nombre,
                           CONCAT_WS(' ', cv.tipo_documento, cv.numero_documento),
                           cv.numero_documento,
                           CONCAT(COALESCE(cv.relacion, 'Persona/empresa vinculada'), ' de ', c.nombre_busqueda)
                      FROM cliente_vinculos cv
                      JOIN clientes c ON c.id = cv.cliente_id
                     WHERE cv.estudio_id = :estudioId AND cv.activo = 1 AND c.activo = 1
                       AND (:excluir IS NULL OR c.uuid_publico <> :excluir)
                       AND ((:documento IS NOT NULL AND cv.numero_documento = :documento)
                         OR (:nombre IS NOT NULL AND cv.nombre LIKE :nombre))
                  ) coincidencias
                 ORDER BY (numero_documento = :documento) DESC, nombre
                 LIMIT 100
                """, p, (rs, rowNum) -> mapConflicto(rs));
    }

    private static CoincidenciaConflicto mapConflicto(ResultSet rs) throws SQLException {
        return new CoincidenciaConflicto(
                rs.getString("origen"), rs.getString("referencia_uuid"), rs.getString("nombre"),
                rs.getString("documento"), rs.getString("relacion"), rs.getString("tipo_coincidencia"),
                rs.getString("termino_buscado"));
    }

    private static String nuloSiVacio(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }

    private static String limpio(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String normalizar(String valor) {
        return limpio(valor).toUpperCase();
    }
}
