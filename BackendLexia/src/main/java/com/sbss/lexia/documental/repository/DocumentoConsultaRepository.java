package com.sbss.lexia.documental.repository;

import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.ExpedienteOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.RelacionOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.UsuarioOpcion;
import com.sbss.lexia.documental.dto.DocumentoEstadisticasResponse;
import com.sbss.lexia.documental.dto.DocumentoResponse;
import com.sbss.lexia.documental.dto.DocumentoVersionResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentoConsultaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_BASE = """
            SELECT
                d.uuid_publico,
                d.nombre,
                d.descripcion,
                d.categoria,
                d.estado,
                d.es_confidencial,
                d.en_papelera,
                d.fecha_papelera,
                d.fecha_creacion,
                d.fecha_modificacion,
                e.id AS expediente_id,
                d.actuacion_id,
                d.tarea_id,
                e.codigo_interno AS expediente_codigo,
                e.numero_expediente,
                c.id AS cliente_id,
                c.nombre_busqueda AS cliente_nombre,
                creador.nombre_completo AS creado_por,
                responsable.nombre_completo AS responsable,
                dv.nombre_archivo,
                dv.extension,
                dv.mime_type,
                dv.tamano_bytes,
                dv.numero_version
            FROM documentos d
            JOIN expedientes e ON e.id = d.expediente_id
            JOIN clientes c ON c.id = e.cliente_id
            LEFT JOIN usuarios creador ON creador.id = d.usuario_creacion_id
            LEFT JOIN usuarios responsable ON responsable.id = e.abogado_responsable_id
            LEFT JOIN documento_versiones dv ON dv.id = d.version_vigente_id
            WHERE d.activo = 1
            """;

    public List<DocumentoResponse> listar(
            String texto,
            String categoria,
            String estado,
            Long expedienteId,
            Long clienteId,
            Long creadorId,
            String extension,
            Boolean confidencial,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            boolean papelera,
            int limite) {

        StringBuilder sql = new StringBuilder(SELECT_BASE);
        Map<String, Object> p = new HashMap<>();
        sql.append(" AND d.en_papelera = :papelera ");
        p.put("papelera", papelera);

        if (texto != null && !texto.isBlank()) {
            sql.append("""
                     AND (
                        LOWER(d.nombre) LIKE :texto
                        OR LOWER(COALESCE(d.descripcion, '')) LIKE :texto
                        OR LOWER(e.codigo_interno) LIKE :texto
                        OR LOWER(COALESCE(e.numero_expediente, '')) LIKE :texto
                        OR LOWER(c.nombre_busqueda) LIKE :texto
                        OR LOWER(COALESCE(dv.nombre_archivo, '')) LIKE :texto
                     )
                    """);
            p.put("texto", "%" + texto.trim().toLowerCase() + "%");
        }
        if (categoria != null && !categoria.isBlank()) {
            sql.append(" AND d.categoria = :categoria ");
            p.put("categoria", categoria);
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND d.estado = :estado ");
            p.put("estado", estado);
        }
        if (expedienteId != null) {
            sql.append(" AND d.expediente_id = :expedienteId ");
            p.put("expedienteId", expedienteId);
        }
        if (clienteId != null) {
            sql.append(" AND e.cliente_id = :clienteId ");
            p.put("clienteId", clienteId);
        }
        if (creadorId != null) {
            sql.append(" AND d.usuario_creacion_id = :creadorId ");
            p.put("creadorId", creadorId);
        }
        if (extension != null && !extension.isBlank()) {
            sql.append(" AND LOWER(COALESCE(dv.extension, '')) = :extension ");
            p.put("extension", extension.trim().toLowerCase());
        }
        if (confidencial != null) {
            sql.append(" AND d.es_confidencial = :confidencial ");
            p.put("confidencial", confidencial);
        }
        if (fechaDesde != null) {
            sql.append(" AND d.fecha_creacion >= :fechaDesde ");
            p.put("fechaDesde", fechaDesde.atStartOfDay());
        }
        if (fechaHasta != null) {
            sql.append(" AND d.fecha_creacion < :fechaHasta ");
            p.put("fechaHasta", fechaHasta.plusDays(1).atStartOfDay());
        }

        sql.append(" ORDER BY d.fecha_creacion DESC LIMIT :limite ");
        p.put("limite", Math.max(1, Math.min(limite, 500)));
        return jdbc.query(sql.toString(), p, this::mapDocumento);
    }

    public DocumentoResponse obtenerPorUuid(String uuid) {
        Map<String, Object> p = Map.of("uuid", uuid);
        List<DocumentoResponse> r = jdbc.query(
                SELECT_BASE + " AND d.uuid_publico = :uuid LIMIT 1", p, this::mapDocumento);
        return r.isEmpty() ? null : r.getFirst();
    }

    public DocumentoEstadisticasResponse estadisticas() {
        String sql = """
                SELECT
                  SUM(CASE WHEN activo = 1 AND en_papelera = 0 THEN 1 ELSE 0 END) total,
                  SUM(CASE WHEN activo = 1 AND en_papelera = 0 AND fecha_creacion >= NOW() - INTERVAL 7 DAY THEN 1 ELSE 0 END) recientes,
                  SUM(CASE WHEN activo = 1 AND en_papelera = 0 AND es_confidencial = 1 THEN 1 ELSE 0 END) confidenciales,
                  SUM(CASE WHEN activo = 1 AND en_papelera = 1 THEN 1 ELSE 0 END) papelera
                FROM documentos
                """;
        return jdbc.queryForObject(sql, Map.of(), (rs, row) -> new DocumentoEstadisticasResponse(
                rs.getLong("total"),
                rs.getLong("recientes"),
                rs.getLong("confidenciales"),
                rs.getLong("papelera")));
    }

    public List<DocumentoVersionResponse> versiones(Long documentoId, Long versionVigenteId) {
        String sql = """
                SELECT v.id, v.numero_version, v.nombre_archivo, v.extension, v.mime_type,
                       v.tamano_bytes, v.hash_sha256, u.nombre_completo cargado_por,
                       v.fecha_carga, v.observaciones
                FROM documento_versiones v
                JOIN usuarios u ON u.id = v.usuario_carga_id
                WHERE v.documento_id = :documentoId
                ORDER BY v.numero_version DESC
                """;
        return jdbc.query(sql, Map.of("documentoId", documentoId), (rs, row) -> new DocumentoVersionResponse(
                rs.getLong("id"),
                rs.getInt("numero_version"),
                rs.getString("nombre_archivo"),
                rs.getString("extension"),
                rs.getString("mime_type"),
                rs.getLong("tamano_bytes"),
                rs.getString("hash_sha256"),
                rs.getString("cargado_por"),
                toLocal(rs.getTimestamp("fecha_carga")),
                rs.getString("observaciones"),
                rs.getLong("id") == (versionVigenteId == null ? -1L : versionVigenteId)));
    }

    public List<ExpedienteOpcion> expedientes() {
        String sql = """
                SELECT e.id, e.codigo_interno, e.numero_expediente,
                       c.id cliente_id, c.nombre_busqueda cliente_nombre
                FROM expedientes e
                JOIN clientes c ON c.id = e.cliente_id
                WHERE e.activo = 1
                ORDER BY e.fecha_creacion DESC, e.codigo_interno
                """;
        return jdbc.query(sql, Map.of(), (rs, row) -> new ExpedienteOpcion(
                rs.getLong("id"), rs.getString("codigo_interno"), rs.getString("numero_expediente"),
                rs.getLong("cliente_id"), rs.getString("cliente_nombre")));
    }

    public List<UsuarioOpcion> creadores() {
        String sql = """
                SELECT DISTINCT u.id, u.nombre_completo
                FROM usuarios u
                JOIN documentos d ON d.usuario_creacion_id = u.id
                WHERE u.activo = 1
                ORDER BY u.nombre_completo
                """;
        return jdbc.query(sql, Map.of(), (rs, row) -> new UsuarioOpcion(
                rs.getLong("id"), rs.getString("nombre_completo")));
    }

    public List<RelacionOpcion> actuaciones(Long expedienteId) {
        String sql = """
                SELECT id, CONCAT(DATE_FORMAT(fecha_hora, '%d/%m/%Y'), ' - ', asunto) texto
                FROM actuaciones
                WHERE expediente_id = :expedienteId AND activo = 1
                ORDER BY fecha_hora DESC
                """;
        return jdbc.query(sql, Map.of("expedienteId", expedienteId),
                (rs, row) -> new RelacionOpcion(rs.getLong("id"), rs.getString("texto")));
    }

    public List<RelacionOpcion> tareas(Long expedienteId) {
        String sql = """
                SELECT id, titulo texto
                FROM tareas
                WHERE expediente_id = :expedienteId AND activo = 1
                ORDER BY fecha_creacion DESC
                """;
        return jdbc.query(sql, Map.of("expedienteId", expedienteId),
                (rs, row) -> new RelacionOpcion(rs.getLong("id"), rs.getString("texto")));
    }

    public boolean existeExpediente(Long id) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM expedientes WHERE id = :id AND activo = 1",
                Map.of("id", id), Integer.class);
        return n != null && n > 0;
    }

    public boolean actuacionPertenece(Long actuacionId, Long expedienteId) {
        if (actuacionId == null) return true;
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM actuaciones WHERE id = :id AND expediente_id = :expedienteId AND activo = 1",
                Map.of("id", actuacionId, "expedienteId", expedienteId), Integer.class);
        return n != null && n > 0;
    }

    public boolean tareaPertenece(Long tareaId, Long expedienteId) {
        if (tareaId == null) return true;
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tareas WHERE id = :id AND expediente_id = :expedienteId AND activo = 1",
                Map.of("id", tareaId, "expedienteId", expedienteId), Integer.class);
        return n != null && n > 0;
    }

    private DocumentoResponse mapDocumento(ResultSet rs, int row) throws SQLException {
        return new DocumentoResponse(
                rs.getString("uuid_publico"),
                rs.getString("nombre"),
                rs.getString("descripcion"),
                rs.getString("categoria"),
                rs.getString("estado"),
                rs.getBoolean("es_confidencial"),
                rs.getBoolean("en_papelera"),
                toLocal(rs.getTimestamp("fecha_papelera")),
                toLocal(rs.getTimestamp("fecha_creacion")),
                toLocal(rs.getTimestamp("fecha_modificacion")),
                rs.getLong("expediente_id"),
                rs.getObject("actuacion_id") == null ? null : rs.getLong("actuacion_id"),
                rs.getObject("tarea_id") == null ? null : rs.getLong("tarea_id"),
                rs.getString("expediente_codigo"),
                rs.getString("numero_expediente"),
                rs.getLong("cliente_id"),
                rs.getString("cliente_nombre"),
                rs.getString("creado_por"),
                rs.getString("responsable"),
                rs.getString("nombre_archivo"),
                rs.getString("extension"),
                rs.getString("mime_type"),
                rs.getObject("tamano_bytes") == null ? null : rs.getLong("tamano_bytes"),
                rs.getObject("numero_version") == null ? null : rs.getInt("numero_version"));
    }

    private static LocalDateTime toLocal(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
