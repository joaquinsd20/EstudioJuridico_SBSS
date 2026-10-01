package com.sbss.lexia.documental.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.ExpedienteOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.RelacionOpcion;
import com.sbss.lexia.documental.dto.CatalogoDocumentoResponse.UsuarioOpcion;
import com.sbss.lexia.documental.dto.DocumentoEstadisticasResponse;
import com.sbss.lexia.documental.dto.DocumentoResponse;
import com.sbss.lexia.documental.dto.DocumentoVersionResponse;
import com.sbss.lexia.shared.dto.Limites;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;

import lombok.RequiredArgsConstructor;

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
                CONCAT(
                    COALESCE(NULLIF(TRIM(e.numero_correlativo), ''), REPLACE(e.codigo_interno, 'EXP-', '')),
                    '-', e.anio
                ) AS expediente_codigo,
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

    /**
     * Frontera de visibilidad. Se agrega al WHERE de TODA consulta que exponga
     * documentos o datos del expediente. Vive en un solo metodo a proposito:
     * repartirlo por cada consulta es como se olvida en la siguiente que
     * alguien escriba.
     *
     * Dos capas:
     *  1. estudio_id, siempre. `documentos` no tiene esa columna, asi que sale
     *     del expediente — por eso todas estas consultas unen con expedientes.
     *  2. Si quien consulta es del portal del cliente, ademas: solo los
     *     expedientes de SU cliente, sin confidenciales y sin documentos en
     *     curso. Un cliente viendo el borrador sin revisar de su propia demanda
     *     es un problema real, no teorico.
     *
     * Si un usuario de portal quedara mal configurado (sin cliente_id), la
     * comparacion contra NULL no casa con ninguna fila y no ve nada. Falla
     * cerrado a proposito.
     */
    private String filtroVisibilidad(Map<String, Object> p) {
        // Estudio y asignacion salen de la regla comun (RF-02): antes esto solo
        // acotaba por estudio, asi que cualquiera del estudio veia los documentos
        // de un expediente confidencial que no tenia asignado.
        StringBuilder f = new StringBuilder(VisibilidadExpedientes.sql("e"));
        VisibilidadExpedientes.parametros(p);
        if (SesionActual.esPortalCliente()) {
            // Lo que sigue es propio del documento, no del expediente: el cliente
            // no ve lo confidencial ni lo que todavia se esta redactando.
            f.append(" AND d.es_confidencial = 0 ")
             .append(" AND d.estado NOT IN ('BORRADOR', 'EN_REVISION', 'OBSERVADO') ");
        }
        return f.toString();
    }

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
            int limite,
            int desplazamiento) {

        StringBuilder sql = new StringBuilder(SELECT_BASE);
        Map<String, Object> p = new HashMap<>();
        sql.append(filtroVisibilidad(p));
        sql.append(" AND d.en_papelera = :papelera ");
        p.put("papelera", papelera);

        if (texto != null && !texto.isBlank()) {
            sql.append("""
                     AND (
                        LOWER(d.nombre) LIKE :texto
                        OR LOWER(COALESCE(d.descripcion, '')) LIKE :texto
                        OR LOWER(e.codigo_interno) LIKE :texto
                        OR LOWER(CONCAT(
                            COALESCE(NULLIF(TRIM(e.numero_correlativo), ''), REPLACE(e.codigo_interno, 'EXP-', '')),
                            '-', e.anio
                        )) LIKE :texto
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

        sql.append(" ORDER BY d.fecha_creacion DESC, d.id DESC LIMIT :limite OFFSET :desplazamiento ");
        p.put("limite", Limites.limite(limite));
        p.put("desplazamiento", Limites.desplazamiento(desplazamiento));
        return jdbc.query(sql.toString(), p, this::mapDocumento);
    }

    public DocumentoResponse obtenerPorUuid(String uuid) {
        Map<String, Object> p = new HashMap<>();
        p.put("uuid", uuid);
        // Un documento fuera de la visibilidad del usuario devuelve null, que el
        // servicio traduce a 404: igual que si no existiera. Un 403 confirmaria
        // que existe en otro estudio o en el expediente de otro cliente.
        String sql = SELECT_BASE + filtroVisibilidad(p) + " AND d.uuid_publico = :uuid LIMIT 1";
        List<DocumentoResponse> r = jdbc.query(sql, p, this::mapDocumento);
        return r.isEmpty() ? null : r.getFirst();
    }

    public DocumentoEstadisticasResponse estadisticas() {
        Map<String, Object> p = new HashMap<>();
        // Se une con expedientes solo para poder acotar: documentos no tiene
        // estudio_id propio.
        String sql = """
                SELECT
                  SUM(CASE WHEN d.activo = 1 AND d.en_papelera = 0 THEN 1 ELSE 0 END) total,
                  SUM(CASE WHEN d.activo = 1 AND d.en_papelera = 0 AND d.fecha_creacion >= NOW() - INTERVAL 7 DAY THEN 1 ELSE 0 END) recientes,
                  SUM(CASE WHEN d.activo = 1 AND d.en_papelera = 0 AND d.es_confidencial = 1 THEN 1 ELSE 0 END) confidenciales,
                  SUM(CASE WHEN d.activo = 1 AND d.en_papelera = 1 THEN 1 ELSE 0 END) papelera
                FROM documentos d
                JOIN expedientes e ON e.id = d.expediente_id
                WHERE 1 = 1
                """ + filtroVisibilidad(p);
        return jdbc.queryForObject(sql, p, (rs, row) -> new DocumentoEstadisticasResponse(
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
                SELECT e.id,
                       CONCAT(
                           COALESCE(NULLIF(TRIM(e.numero_correlativo), ''), REPLACE(e.codigo_interno, 'EXP-', '')),
                           '-', e.anio
                       ) AS codigo_documental,
                       e.numero_expediente,
                       c.id cliente_id, c.nombre_busqueda cliente_nombre
                FROM expedientes e
                JOIN clientes c ON c.id = e.cliente_id
                WHERE e.activo = 1
                  AND e.estudio_id = :estudioId
                """ + (SesionActual.esPortalCliente() ? " AND e.cliente_id = :clienteId " : "")
                + " ORDER BY e.fecha_creacion DESC, e.codigo_interno";
        Map<String, Object> p = new HashMap<>();
        p.put("estudioId", SesionActual.estudioId());
        if (SesionActual.esPortalCliente()) {
            p.put("clienteId", SesionActual.clienteId());
        }
        return jdbc.query(sql, p, (rs, row) -> new ExpedienteOpcion(
                rs.getLong("id"), rs.getString("codigo_documental"), rs.getString("numero_expediente"),
                rs.getLong("cliente_id"), rs.getString("cliente_nombre")));
    }

    public List<UsuarioOpcion> creadores() {
        // Este desplegable es un filtro de la lista de documentos. Para un
        // usuario del portal no tiene sentido y ademas le entregaria la
        // plantilla completa del estudio, asi que se le devuelve vacio.
        if (SesionActual.esPortalCliente()) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT u.id, u.nombre_completo
                FROM usuarios u
                JOIN documentos d ON d.usuario_creacion_id = u.id
                WHERE u.activo = 1 AND u.estudio_id = :estudioId
                ORDER BY u.nombre_completo
                """;
        return jdbc.query(sql, Map.of("estudioId", SesionActual.estudioId()),
                (rs, row) -> new UsuarioOpcion(
                        rs.getLong("id"), rs.getString("nombre_completo")));
    }

    /**
     * Condicion para que un expediente sea visible. Los metodos de abajo
     * reciben el expedienteId del cliente HTTP, asi que sin esto bastaria
     * probar numeros para leer las actuaciones y tareas de otro estudio.
     */
    /** Misma regla comun, para las consultas que reciben un expedienteId del cliente. */
    private String expedienteVisible(Map<String, Object> p) {
        VisibilidadExpedientes.parametros(p);
        return VisibilidadExpedientes.sql("e");
    }

    public List<RelacionOpcion> actuaciones(Long expedienteId) {
        Map<String, Object> p = new HashMap<>();
        p.put("expedienteId", expedienteId);
        String sql = """
                SELECT a.id, CONCAT(DATE_FORMAT(a.fecha_hora, '%d/%m/%Y'), ' - ', a.asunto) texto
                FROM actuaciones a
                JOIN expedientes e ON e.id = a.expediente_id
                WHERE a.expediente_id = :expedienteId AND a.activo = 1
                """ + expedienteVisible(p) + " ORDER BY a.fecha_hora DESC";
        return jdbc.query(sql, p,
                (rs, row) -> new RelacionOpcion(rs.getLong("id"), rs.getString("texto")));
    }

    public List<RelacionOpcion> tareas(Long expedienteId) {
        Map<String, Object> p = new HashMap<>();
        p.put("expedienteId", expedienteId);
        String sql = """
                SELECT t.id, t.titulo texto
                FROM tareas t
                JOIN expedientes e ON e.id = t.expediente_id
                WHERE t.expediente_id = :expedienteId AND t.activo = 1
                """ + expedienteVisible(p) + " ORDER BY t.fecha_creacion DESC";
        return jdbc.query(sql, p,
                (rs, row) -> new RelacionOpcion(rs.getLong("id"), rs.getString("texto")));
    }


    public String codigoCarpetaExpediente(Long expedienteId) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", expedienteId);
        String sql = """
                SELECT CONCAT(
                    COALESCE(NULLIF(TRIM(e.numero_correlativo), ''), REPLACE(e.codigo_interno, 'EXP-', '')),
                    '-', e.anio
                )
                FROM expedientes e
                WHERE e.id = :id AND e.activo = 1
                """ + expedienteVisible(p);
        List<String> codigos = jdbc.query(sql, p, (rs, row) -> rs.getString(1));
        if (codigos.isEmpty() || codigos.getFirst() == null || codigos.getFirst().isBlank()) {
            throw new IllegalArgumentException("No se pudo obtener el codigo del expediente");
        }
        return codigos.getFirst();
    }

    public boolean existeExpediente(Long id) {
        // Acotado a proposito: este metodo valida el expediente al que se va a
        // colgar un documento. Sin el filtro, bastaria mandar el id de un
        // expediente ajeno para escribir en el.
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        String sql = "SELECT COUNT(*) FROM expedientes e WHERE e.id = :id AND e.activo = 1"
                + expedienteVisible(p);
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
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
