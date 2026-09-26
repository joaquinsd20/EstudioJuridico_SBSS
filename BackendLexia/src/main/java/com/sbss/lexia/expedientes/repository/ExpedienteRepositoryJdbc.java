package com.sbss.lexia.expedientes.repository;

import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Catalogos;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Equipo;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.EstadoOpcion;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.EventoLineaTiempo;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteDetalle;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteLista;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.GuardarRequest;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.MateriaOpcion;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Opcion;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.PersonaRelacionada;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Resumen;
import com.sbss.lexia.shared.dto.Limites;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ExpedienteRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    public List<ExpedienteLista> listar(
            Integer estudioId,
            Long usuarioId,
            boolean verTodos,
            boolean verConfidenciales,
            String texto,
            String cliente,
            String estado,
            Long materiaId,
            String organo,
            int limite,
            int desplazamiento) {

        List<Long> visibles = idsVisibles(estudioId, usuarioId, verTodos, verConfidenciales);
        if (visibles.isEmpty()) return List.of();

        StringBuilder sql = new StringBuilder("""
                SELECT e.uuid_publico, e.codigo_interno, e.numero_expediente, e.anio,
                       c.nombre_busqueda cliente_nombre,
                       CONCAT(c.tipo_documento, ' ', c.numero_documento) cliente_documento,
                       esp.nombre especialidad, COALESCE(m.nombre, 'Sin materia') materia,
                       COALESCE(o.nombre, 'Sin órgano asignado') organo,
                       o.distrito_judicial,
                       u.nombre_completo responsable,
                       ee.codigo estado_codigo, ee.nombre estado_nombre, ee.color_hex estado_color,
                       e.nivel_riesgo, e.cuantia, e.moneda, e.fecha_inicio,
                       e.fecha_ultima_actuacion, e.es_confidencial
                  FROM expedientes e
                  JOIN clientes c ON c.id = e.cliente_id
                  JOIN especialidades esp ON esp.id = e.especialidad_id
                  LEFT JOIN materias m ON m.id = e.materia_id
                  LEFT JOIN organos_jurisdiccionales o ON o.id = e.organo_id
                  JOIN estados_expediente ee ON ee.id = e.estado_id
                  JOIN usuarios u ON u.id = e.abogado_responsable_id
                 WHERE e.id IN (:visibles)
                """);

        MapSqlParameterSource p = new MapSqlParameterSource().addValue("visibles", visibles);

        if (texto != null && !texto.isBlank()) {
            sql.append(" AND LOWER(CONCAT_WS(' ', e.codigo_interno, e.numero_expediente, c.nombre_busqueda)) LIKE :texto");
            p.addValue("texto", like(texto));
        }
        if (cliente != null && !cliente.isBlank()) {
            sql.append(" AND LOWER(c.nombre_busqueda) LIKE :cliente");
            p.addValue("cliente", like(cliente));
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND ee.codigo = :estado");
            p.addValue("estado", normalizar(estado));
        }
        if (materiaId != null) {
            sql.append(" AND e.materia_id = :materiaId");
            p.addValue("materiaId", materiaId);
        }
        if (organo != null && !organo.isBlank()) {
            sql.append(" AND LOWER(COALESCE(o.nombre,'')) LIKE :organo");
            p.addValue("organo", like(organo));
        }

        // El LIMIT 250 estaba escrito a mano: el cliente no podia ver la fila 251.
        sql.append(" ORDER BY COALESCE(e.fecha_ultima_actuacion, e.fecha_creacion) DESC, e.id DESC")
           .append(" LIMIT ").append(Limites.limite(limite))
           .append(" OFFSET ").append(Limites.desplazamiento(desplazamiento));

        return jdbc.query(sql.toString(), p, (rs, rowNum) -> new ExpedienteLista(
                rs.getString("uuid_publico"),
                rs.getString("codigo_interno"),
                rs.getString("numero_expediente"),
                rs.getInt("anio"),
                rs.getString("cliente_nombre"),
                rs.getString("cliente_documento"),
                rs.getString("especialidad"),
                rs.getString("materia"),
                rs.getString("organo"),
                rs.getString("distrito_judicial"),
                rs.getString("responsable"),
                rs.getString("estado_codigo"),
                rs.getString("estado_nombre"),
                rs.getString("estado_color"),
                rs.getString("nivel_riesgo"),
                rs.getBigDecimal("cuantia"),
                rs.getString("moneda"),
                getLocalDate(rs, "fecha_inicio"),
                rs.getTimestamp("fecha_ultima_actuacion") == null ? null : rs.getTimestamp("fecha_ultima_actuacion").toLocalDateTime(),
                rs.getBoolean("es_confidencial")));
    }

    public Resumen resumen(Integer estudioId, Long usuarioId, boolean verTodos, boolean verConfidenciales) {
        List<Long> visibles = idsVisibles(estudioId, usuarioId, verTodos, verConfidenciales);
        if (visibles.isEmpty()) return new Resumen(0, 0, 0, 0);

        String sql = """
                SELECT ee.codigo estado_codigo, e.nivel_riesgo
                  FROM expedientes e
                  JOIN estados_expediente ee ON ee.id = e.estado_id
                 WHERE e.id IN (:visibles)
                """;

        List<Map<String, Object>> filas = jdbc.queryForList(
                sql, new MapSqlParameterSource().addValue("visibles", visibles));

        long total = filas.size();
        long activos = filas.stream()
                .map(f -> String.valueOf(f.get("estado_codigo")))
                .filter(codigo -> !List.of("ARCHIVADO", "CONCLUIDO", "GANADO", "PERDIDO", "DERIVADO").contains(codigo))
                .count();
        long enTramite = filas.stream()
                .filter(f -> "TRAMITE".equals(String.valueOf(f.get("estado_codigo"))))
                .count();
        long criticos = filas.stream()
                .filter(f -> "CRITICO".equals(String.valueOf(f.get("nivel_riesgo"))))
                .count();

        return new Resumen(total, activos, enTramite, criticos);
    }

    public ExpedienteDetalle detalle(
            String uuid,
            Integer estudioId,
            Long usuarioId,
            boolean verTodos,
            boolean verConfidenciales) {

        Long expedienteIdVisible = idVisible(uuid, estudioId, usuarioId, verTodos, verConfidenciales);
        if (expedienteIdVisible == null) return null;

        // Las relaciones se consultan ANTES de abrir el ResultSet principal.
        // Ejecutar consultas anidadas dentro de un RowMapper puede fallar con
        // ciertos drivers/configuraciones de MySQL al reutilizar la conexión.
        List<PersonaRelacionada> contrapartes = contrapartesSeguras(expedienteIdVisible);
        List<Equipo> equipo = equipoSeguro(expedienteIdVisible);

        String sql = """
                SELECT e.id, e.uuid_publico, e.codigo_interno, e.numero_expediente,
                       e.numero_correlativo, e.anio,
                       c.id cliente_id, c.nombre_busqueda cliente_nombre,
                       CONCAT(c.tipo_documento, ' ', c.numero_documento) cliente_documento,
                       esp.id especialidad_id, esp.nombre especialidad,
                       m.id materia_id, m.nombre materia,
                       o.id organo_id, o.nombre organo, o.distrito_judicial,
                       e.etapa_procesal, e.instancia,
                       ee.id estado_id, ee.codigo estado_codigo, ee.nombre estado_nombre,
                       ee.color_hex estado_color,
                       u.id abogado_id, u.nombre_completo abogado_nombre,
                       s.id socio_id, s.nombre_completo socio_nombre,
                       e.nivel_riesgo, e.cuantia, e.moneda, e.fecha_inicio,
                       e.fecha_ingreso_estudio, e.fecha_ultima_actuacion, e.fecha_cierre,
                       e.resultado, e.es_confidencial, e.resumen
                  FROM expedientes e
                  JOIN clientes c ON c.id = e.cliente_id
                  JOIN especialidades esp ON esp.id = e.especialidad_id
                  LEFT JOIN materias m ON m.id = e.materia_id
                  LEFT JOIN organos_jurisdiccionales o ON o.id = e.organo_id
                  JOIN estados_expediente ee ON ee.id = e.estado_id
                  JOIN usuarios u ON u.id = e.abogado_responsable_id
                  LEFT JOIN usuarios s ON s.id = e.socio_supervisor_id
                 WHERE e.id = :id
                """;

        List<ExpedienteDetalle> encontrados = jdbc.query(
                sql,
                Map.of("id", expedienteIdVisible),
                (rs, rowNum) -> new ExpedienteDetalle(
                        rs.getString("uuid_publico"),
                        rs.getString("codigo_interno"),
                        rs.getString("numero_expediente"),
                        rs.getString("numero_correlativo"),
                        rs.getInt("anio"),
                        rs.getLong("cliente_id"),
                        rs.getString("cliente_nombre"),
                        rs.getString("cliente_documento"),
                        rs.getLong("especialidad_id"),
                        rs.getString("especialidad"),
                        getNullableLong(rs, "materia_id"),
                        rs.getString("materia"),
                        getNullableLong(rs, "organo_id"),
                        rs.getString("organo"),
                        rs.getString("distrito_judicial"),
                        rs.getString("etapa_procesal"),
                        rs.getString("instancia"),
                        rs.getLong("estado_id"),
                        rs.getString("estado_codigo"),
                        rs.getString("estado_nombre"),
                        rs.getString("estado_color"),
                        rs.getLong("abogado_id"),
                        rs.getString("abogado_nombre"),
                        getNullableLong(rs, "socio_id"),
                        rs.getString("socio_nombre"),
                        rs.getString("nivel_riesgo"),
                        rs.getBigDecimal("cuantia"),
                        rs.getString("moneda"),
                        getLocalDate(rs, "fecha_inicio"),
                        getLocalDate(rs, "fecha_ingreso_estudio"),
                        getLocalDateTime(rs, "fecha_ultima_actuacion"),
                        getLocalDate(rs, "fecha_cierre"),
                        rs.getString("resultado"),
                        rs.getBoolean("es_confidencial"),
                        rs.getString("resumen"),
                        contrapartes,
                        equipo));
        return encontrados.isEmpty() ? null : encontrados.getFirst();
    }

    public List<EventoLineaTiempo> lineaTiempo(
            String uuid,
            Integer estudioId,
            Long usuarioId,
            boolean verTodos,
            boolean verConfidenciales) {

        Long expedienteId = idVisible(uuid, estudioId, usuarioId, verTodos, verConfidenciales);
        if (expedienteId == null) return List.of();

        Map<String, Object> p = Map.of("expedienteId", expedienteId);
        List<EventoLineaTiempo> eventos = new ArrayList<>();

        // Cada fuente se consulta de forma independiente. Así una tabla auxiliar
        // desactualizada no provoca un HTTP 500 para toda la ficha del expediente.
        agregarEventosSeguros(eventos, "EXPEDIENTE", () -> jdbc.query("""
                SELECT CONCAT('EXP-', e.id) clave,
                       e.fecha_creacion fecha_hora,
                       COALESCE(u.nombre_completo, 'Sistema') usuario,
                       'Expediente registrado' titulo,
                       CONCAT('Se registró ', e.codigo_interno) descripcion
                  FROM expedientes e
                  LEFT JOIN usuarios u ON u.id = e.usuario_creacion_id
                 WHERE e.id = :expedienteId
                """, p, (rs, n) -> evento(rs, "HECHO", null, null, null, "EXPEDIENTE")));

        agregarEventosSeguros(eventos, "ACTUACION", () -> jdbc.query("""
                SELECT CONCAT('ACT-', a.id) clave,
                       a.fecha_hora fecha_hora,
                       COALESCE(u.nombre_completo, 'Sistema') usuario,
                       a.asunto titulo,
                       COALESCE(a.descripcion, a.resultado, a.proxima_accion) descripcion,
                       CASE
                         WHEN ta.codigo = 'NOTIFICACION' THEN 'NOTIFICACION'
                         WHEN ta.categoria = 'AUDIENCIA' THEN 'AUDIENCIA'
                         WHEN ta.codigo = 'LLAMADA' THEN 'LLAMADA'
                         WHEN ta.codigo = 'REUNION_CLIENTE' THEN 'REUNION'
                         ELSE 'HECHO'
                       END tipo
                  FROM actuaciones a
                  JOIN tipos_actuacion ta ON ta.id = a.tipo_actuacion_id
                  LEFT JOIN usuarios u ON u.id = COALESCE(a.usuario_creacion_id, a.usuario_responsable_id)
                 WHERE a.expediente_id = :expedienteId AND a.activo = 1
                """, p, (rs, n) -> evento(rs, rs.getString("tipo"), null, null, null, "ACTUACION")));

        agregarEventosSeguros(eventos, "DOCUMENTO", () -> jdbc.query("""
                SELECT CONCAT('DOC-', d.id) clave,
                       d.fecha_creacion fecha_hora,
                       COALESCE(u.nombre_completo, 'Sistema') usuario,
                       d.nombre titulo,
                       COALESCE(d.descripcion, CONCAT('Documento en estado ', d.estado)) descripcion,
                       d.uuid_publico documento_uuid,
                       t.uuid_publico tarea_uuid,
                       CASE WHEN d.categoria = 'NOTIFICACION' THEN 'NOTIFICACION' ELSE 'DOCUMENTO' END tipo
                  FROM documentos d
                  LEFT JOIN usuarios u ON u.id = d.usuario_creacion_id
                  LEFT JOIN tareas t ON t.id = d.tarea_id
                 WHERE d.expediente_id = :expedienteId AND d.activo = 1 AND d.en_papelera = 0
                """, p, (rs, n) -> evento(rs, rs.getString("tipo"), rs.getString("documento_uuid"), null,
                        rs.getString("tarea_uuid"), "DOCUMENTO")));

        agregarEventosSeguros(eventos, "RESOLUCION", () -> jdbc.query("""
                SELECT CONCAT('RES-', r.id) clave,
                       COALESCE(r.fecha_creacion, CAST(r.fecha_notificacion AS DATETIME)) fecha_hora,
                       COALESCE(u.nombre_completo, 'Sistema') usuario,
                       CONCAT('Resolución ', r.numero_resolucion) titulo,
                       COALESCE(r.sumilla, CONCAT('Notificada el ', DATE_FORMAT(r.fecha_notificacion, '%d/%m/%Y'))) descripcion,
                       d.uuid_publico documento_uuid
                  FROM resoluciones r
                  LEFT JOIN usuarios u ON u.id = r.usuario_creacion_id
                  LEFT JOIN documentos d ON d.id = r.documento_id
                 WHERE r.expediente_id = :expedienteId AND r.activo = 1
                """, p, (rs, n) -> evento(rs, "RESOLUCION", rs.getString("documento_uuid"), null, null, "RESOLUCION")));

        agregarEventosSeguros(eventos, "PLAZO", () -> jdbc.query("""
                SELECT CONCAT('PLZ-', p.id) clave,
                       p.fecha_creacion fecha_hora,
                       COALESCE(u.nombre_completo, 'Sistema') usuario,
                       p.descripcion titulo,
                       CONCAT('Vence ', DATE_FORMAT(p.fecha_vencimiento, '%d/%m/%Y'), ' · ', p.estado) descripcion,
                       d.uuid_publico documento_uuid,
                       p.uuid_publico plazo_uuid
                  FROM plazos p
                  LEFT JOIN usuarios u ON u.id = COALESCE(p.usuario_creacion_id, p.usuario_responsable_id)
                  LEFT JOIN documentos d ON d.id = p.documento_resultante_id
                 WHERE p.expediente_id = :expedienteId AND p.activo = 1
                """, p, (rs, n) -> evento(rs, "PLAZO", rs.getString("documento_uuid"),
                        rs.getString("plazo_uuid"), null, "PLAZO")));

        agregarEventosSeguros(eventos, "TAREA", () -> jdbc.query("""
                SELECT CONCAT('TAR-', t.id) clave,
                       t.fecha_creacion fecha_hora,
                       COALESCE(u.nombre_completo, 'Sistema') usuario,
                       t.titulo titulo,
                       COALESCE(t.descripcion, CONCAT('Estado: ', t.estado)) descripcion,
                       p.uuid_publico plazo_uuid,
                       t.uuid_publico tarea_uuid
                  FROM tareas t
                  LEFT JOIN usuarios u ON u.id = COALESCE(t.usuario_creacion_id, t.usuario_responsable_id)
                  LEFT JOIN plazos p ON p.id = t.plazo_id
                 WHERE t.expediente_id = :expedienteId AND t.activo = 1
                """, p, (rs, n) -> evento(rs, "TAREA", null, rs.getString("plazo_uuid"),
                        rs.getString("tarea_uuid"), "TAREA")));

        agregarEventosSeguros(eventos, "AGENDA", () -> jdbc.query("""
                SELECT CONCAT('AGE-', ag.id) clave,
                       ag.fecha_inicio fecha_hora,
                       COALESCE(u.nombre_completo, 'Sistema') usuario,
                       ag.titulo titulo,
                       COALESCE(ag.descripcion, ag.ubicacion, ag.modalidad) descripcion,
                       p.uuid_publico plazo_uuid,
                       CASE
                         WHEN ag.tipo = 'AUDIENCIA' THEN 'AUDIENCIA'
                         WHEN ag.tipo = 'LLAMADA' THEN 'LLAMADA'
                         WHEN ag.tipo IN ('REUNION_CLIENTE','VIDEOCONFERENCIA') THEN 'REUNION'
                         WHEN ag.tipo = 'VENCIMIENTO_PLAZO' THEN 'PLAZO'
                         ELSE 'HECHO'
                       END tipo
                  FROM eventos_agenda ag
                  LEFT JOIN usuarios u ON u.id = ag.usuario_creacion_id
                  LEFT JOIN plazos p ON p.id = ag.plazo_id
                 WHERE ag.expediente_id = :expedienteId AND ag.activo = 1
                """, p, (rs, n) -> evento(rs, rs.getString("tipo"), null, rs.getString("plazo_uuid"), null, "AGENDA")));

        // El historial de responsables/equipo se toma de auditoria y no del estado
        // actual de expediente_equipo. De esta forma un cambio de rol no sobrescribe
        // el evento anterior y la linea de tiempo conserva la trazabilidad real.
        agregarEventosSeguros(eventos, "HISTORIAL_EQUIPO", () -> jdbc.query("""
                SELECT CONCAT('AUD-EQ-', a.id) clave,
                       a.fecha fecha_hora,
                       COALESCE(actor.nombre_completo, 'Sistema') usuario,
                       CASE
                         WHEN a.entidad = 'expediente_responsable' AND a.accion = 'CREAR'
                           THEN 'Responsable asignado'
                         WHEN a.entidad = 'expediente_responsable'
                           THEN 'Cambio de responsable'
                         WHEN a.accion = 'CREAR'
                           THEN 'Integrante añadido al equipo'
                         WHEN a.accion = 'ELIMINAR'
                           THEN 'Integrante retirado del equipo'
                         ELSE 'Cambio en el equipo'
                       END titulo,
                       a.descripcion descripcion,
                       CASE
                         WHEN a.entidad = 'expediente_responsable' THEN 'CAMBIO_RESPONSABLE'
                         ELSE 'CAMBIO_EQUIPO'
                       END tipo
                  FROM auditoria a
                  LEFT JOIN usuarios actor ON actor.id = a.usuario_id
                 WHERE a.expediente_id = :expedienteId
                   AND a.entidad IN ('expediente_responsable', 'expediente_equipo')
                   AND a.exito = 1
                """, p, (rs, n) -> evento(rs, rs.getString("tipo"), null, null, null, "EQUIPO")));

        return eventos.stream()
                .filter(e -> e.fechaHora() != null)
                .sorted(Comparator.comparing(EventoLineaTiempo::fechaHora, Comparator.reverseOrder())
                        .thenComparing(EventoLineaTiempo::clave, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(300)
                .toList();
    }

    private EventoLineaTiempo evento(
            ResultSet rs,
            String tipo,
            String documentoUuid,
            String plazoUuid,
            String tareaUuid,
            String origen) throws SQLException {
        return new EventoLineaTiempo(
                rs.getString("clave"),
                tipo,
                getLocalDateTime(rs, "fecha_hora"),
                rs.getString("usuario"),
                rs.getString("titulo"),
                rs.getString("descripcion"),
                documentoUuid,
                plazoUuid,
                tareaUuid,
                origen);
    }

    private void agregarEventosSeguros(
            List<EventoLineaTiempo> destino,
            String origen,
            Supplier<List<EventoLineaTiempo>> consulta) {
        try {
            destino.addAll(consulta.get());
        } catch (DataAccessException ex) {
            log.warn("No se pudo leer la fuente {} de la línea de tiempo: {}", origen, ex.getMostSpecificCause().getMessage());
        }
    }

    public Catalogos catalogos(Integer estudioId) {
        List<Opcion> clientes = jdbc.query("""
                SELECT id, nombre_busqueda nombre, CONCAT(tipo_documento, ' ', numero_documento) detalle
                  FROM clientes WHERE estudio_id = :estudioId AND activo = 1
                 ORDER BY nombre_busqueda
                """, Map.of("estudioId", estudioId), (rs, n) -> opcion(rs));

        List<Opcion> especialidades = jdbc.query("""
                SELECT id, nombre, codigo detalle FROM especialidades
                 WHERE estudio_id = :estudioId AND activo = 1 ORDER BY orden, nombre
                """, Map.of("estudioId", estudioId), (rs, n) -> opcion(rs));

        List<MateriaOpcion> materias = jdbc.query("""
                SELECT m.id, m.especialidad_id, m.nombre
                  FROM materias m JOIN especialidades e ON e.id = m.especialidad_id
                 WHERE e.estudio_id = :estudioId AND m.activo = 1
                 ORDER BY m.nombre
                """, Map.of("estudioId", estudioId),
                (rs, n) -> new MateriaOpcion(rs.getLong("id"), rs.getLong("especialidad_id"), rs.getString("nombre")));

        List<Opcion> organos = jdbc.query("""
                SELECT id, nombre, distrito_judicial detalle FROM organos_jurisdiccionales
                 WHERE estudio_id = :estudioId AND activo = 1 ORDER BY nombre
                """, Map.of("estudioId", estudioId), (rs, n) -> opcion(rs));

        List<EstadoOpcion> estados = jdbc.query("""
                SELECT id, codigo, nombre, color_hex FROM estados_expediente
                 WHERE estudio_id = :estudioId AND activo = 1 ORDER BY orden, nombre
                """, Map.of("estudioId", estudioId),
                (rs, n) -> new EstadoOpcion(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getString("color_hex")));

        List<Opcion> usuarios = jdbc.query("""
                SELECT id, nombre_completo nombre, COALESCE(cargo, email) detalle FROM usuarios
                 WHERE estudio_id = :estudioId AND tipo_usuario = 'INTERNO' AND activo = 1 AND estado = 'ACTIVO'
                 ORDER BY nombre_completo
                """, Map.of("estudioId", estudioId), (rs, n) -> opcion(rs));

        List<Opcion> contrapartes = jdbc.query("""
                SELECT id, nombre_completo nombre, COALESCE(numero_documento, tipo_persona) detalle FROM contrapartes
                 WHERE estudio_id = :estudioId AND activo = 1 ORDER BY nombre_completo
                """, Map.of("estudioId", estudioId), (rs, n) -> opcion(rs));

        return new Catalogos(clientes, especialidades, materias, organos, estados, usuarios, contrapartes);
    }

    public boolean referenciasValidas(Integer estudioId, GuardarRequest r) {
        if (!existe("SELECT COUNT(*) FROM clientes WHERE id = :id AND estudio_id = :estudioId AND activo = 1", estudioId, r.clienteId())) return false;
        if (!existe("SELECT COUNT(*) FROM especialidades WHERE id = :id AND estudio_id = :estudioId AND activo = 1", estudioId, r.especialidadId())) return false;
        if (r.materiaId() != null) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM materias m
                    JOIN especialidades e ON e.id = m.especialidad_id
                    WHERE m.id = :id AND m.especialidad_id = :especialidadId
                      AND e.estudio_id = :estudioId AND m.activo = 1
                    """, new MapSqlParameterSource()
                    .addValue("id", r.materiaId())
                    .addValue("especialidadId", r.especialidadId())
                    .addValue("estudioId", estudioId), Integer.class);
            if (count == null || count != 1) return false;
        }
        if (r.organoId() != null && !existe("SELECT COUNT(*) FROM organos_jurisdiccionales WHERE id = :id AND estudio_id = :estudioId AND activo = 1", estudioId, r.organoId())) return false;
        if (!existe("SELECT COUNT(*) FROM estados_expediente WHERE id = :id AND estudio_id = :estudioId AND activo = 1", estudioId, r.estadoId())) return false;
        if (!existe("SELECT COUNT(*) FROM usuarios WHERE id = :id AND estudio_id = :estudioId AND activo = 1 AND tipo_usuario = 'INTERNO'", estudioId, r.abogadoResponsableId())) return false;
        if (r.socioSupervisorId() != null && !existe("SELECT COUNT(*) FROM usuarios WHERE id = :id AND estudio_id = :estudioId AND activo = 1 AND tipo_usuario = 'INTERNO'", estudioId, r.socioSupervisorId())) return false;

        List<Long> equipo = r.equipoIds() == null ? List.of() : r.equipoIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!equipo.isEmpty()) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM usuarios
                    WHERE estudio_id = :estudioId AND activo = 1 AND tipo_usuario = 'INTERNO' AND id IN (:ids)
                    """, new MapSqlParameterSource().addValue("estudioId", estudioId).addValue("ids", equipo), Integer.class);
            if (count == null || count != equipo.size()) return false;
        }

        List<Long> contrapartes = r.contraparteIds() == null ? List.of() : r.contraparteIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!contrapartes.isEmpty()) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM contrapartes
                    WHERE estudio_id = :estudioId AND activo = 1 AND id IN (:ids)
                    """, new MapSqlParameterSource().addValue("estudioId", estudioId).addValue("ids", contrapartes), Integer.class);
            if (count == null || count != contrapartes.size()) return false;
        }
        return true;
    }

    private boolean existe(String sql, Integer estudioId, Long id) {
        Integer count = jdbc.queryForObject(sql, Map.of("id", id, "estudioId", estudioId), Integer.class);
        return count != null && count == 1;
    }


    public boolean existeNumeroExpediente(Integer estudioId, String numeroExpediente, String uuidExcluir) {
        if (numeroExpediente == null || numeroExpediente.isBlank()) return false;

        String sql = """
                SELECT COUNT(*)
                  FROM expedientes
                 WHERE estudio_id = :estudioId
                   AND activo = 1
                   AND numero_expediente = :numeroExpediente
                   AND (:uuidExcluir IS NULL OR uuid_publico <> :uuidExcluir)
                """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("estudioId", estudioId)
                .addValue("numeroExpediente", numeroExpediente.trim())
                .addValue("uuidExcluir", uuidExcluir, Types.VARCHAR);

        Long total = jdbc.queryForObject(sql, p, Long.class);
        return total != null && total > 0;
    }

    @Transactional
    public String crear(Integer estudioId, Long usuarioId, GuardarRequest r) {
        String temporal = "TMP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String sql = """
                INSERT INTO expedientes (
                    estudio_id, codigo_interno, numero_expediente, numero_correlativo, anio,
                    cliente_id, especialidad_id, materia_id, organo_id, estado_id,
                    etapa_procesal, instancia, abogado_responsable_id, socio_supervisor_id,
                    nivel_riesgo, cuantia, moneda, fecha_inicio, fecha_ingreso_estudio,
                    fecha_cierre, resultado, es_confidencial, resumen, usuario_creacion_id
                ) VALUES (
                    :estudioId, :codigo, :numeroExpediente, :numeroCorrelativo, :anio,
                    :clienteId, :especialidadId, :materiaId, :organoId, :estadoId,
                    :etapaProcesal, :instancia, :abogadoId, :socioId,
                    :riesgo, :cuantia, :moneda, :fechaInicio, :fechaIngreso,
                    :fechaCierre, :resultado, :confidencial, :resumen, :usuarioId
                )
                """;
        MapSqlParameterSource p = parametrosGuardar(estudioId, usuarioId, temporal, r);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(sql, p, kh, new String[]{"id"});
        if (kh.getKey() == null) throw new IllegalStateException("No se obtuvo el id del expediente creado");
        long id = kh.getKey().longValue();
        String codigo = "EXP-" + String.format("%04d", id);
        jdbc.update("UPDATE expedientes SET codigo_interno = :codigo WHERE id = :id",
                Map.of("codigo", codigo, "id", id));
        sincronizarRelaciones(estudioId, id, usuarioId, null, r.abogadoResponsableId(), r.equipoIds(), r.contraparteIds());
        return jdbc.queryForObject("SELECT uuid_publico FROM expedientes WHERE id = :id",
                Map.of("id", id), String.class);
    }

    @Transactional
    public boolean actualizar(String uuid, Integer estudioId, Long usuarioId, GuardarRequest r) {
        ExpedienteEdicion previo = jdbc.query("""
                SELECT id, abogado_responsable_id
                  FROM expedientes
                 WHERE uuid_publico = :uuid AND estudio_id = :estudioId AND activo = 1
                """, Map.of("uuid", uuid, "estudioId", estudioId),
                rs -> rs.next() ? new ExpedienteEdicion(rs.getLong("id"), rs.getLong("abogado_responsable_id")) : null);
        if (previo == null) return false;
        Long id = previo.id();

        String sql = """
                UPDATE expedientes SET
                    numero_expediente = :numeroExpediente,
                    numero_correlativo = :numeroCorrelativo,
                    anio = :anio,
                    cliente_id = :clienteId,
                    especialidad_id = :especialidadId,
                    materia_id = :materiaId,
                    organo_id = :organoId,
                    estado_id = :estadoId,
                    etapa_procesal = :etapaProcesal,
                    instancia = :instancia,
                    abogado_responsable_id = :abogadoId,
                    socio_supervisor_id = :socioId,
                    nivel_riesgo = :riesgo,
                    cuantia = :cuantia,
                    moneda = :moneda,
                    fecha_inicio = :fechaInicio,
                    fecha_ingreso_estudio = :fechaIngreso,
                    fecha_cierre = :fechaCierre,
                    resultado = :resultado,
                    es_confidencial = :confidencial,
                    resumen = :resumen,
                    usuario_modificacion_id = :usuarioId
                 WHERE id = :id AND estudio_id = :estudioId AND activo = 1
                """;
        MapSqlParameterSource p = parametrosGuardar(estudioId, usuarioId, null, r).addValue("id", id);
        int filas = jdbc.update(sql, p);
        if (filas > 0) {
            sincronizarRelaciones(estudioId, id, usuarioId, previo.responsableId(),
                    r.abogadoResponsableId(), r.equipoIds(), r.contraparteIds());
        }
        return filas > 0;
    }

    private void sincronizarRelaciones(Integer estudioId, Long expedienteId, Long usuarioId,
                                       Long responsableAnteriorId, Long responsableId,
                                       List<Long> equipoIds, List<Long> contraparteIds) {
        Map<Long, String> equipoActual = equipoActivoConRol(expedienteId);
        Map<Long, String> equipoDeseado = new LinkedHashMap<>();
        equipoDeseado.put(responsableId, "RESPONSABLE");
        if (equipoIds != null) {
            equipoIds.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !id.equals(responsableId))
                    .distinct()
                    .forEach(id -> equipoDeseado.put(id, "APOYO"));
        }

        registrarCambiosEquipo(estudioId, expedienteId, usuarioId, responsableAnteriorId,
                responsableId, equipoActual, equipoDeseado);

        List<Long> equipo = new ArrayList<>(equipoDeseado.keySet());
        jdbc.update("UPDATE expediente_equipo SET activo = 0, fecha_desasignacion = NOW(3) " +
                        "WHERE expediente_id = :id AND activo = 1 AND usuario_id NOT IN (:equipo)",
                new MapSqlParameterSource().addValue("id", expedienteId).addValue("equipo", equipo));

        for (Map.Entry<Long, String> miembro : equipoDeseado.entrySet()) {
            jdbc.update("""
                    INSERT INTO expediente_equipo
                        (expediente_id, usuario_id, rol_en_expediente, puede_editar, fecha_asignacion, usuario_asignacion_id, activo)
                    VALUES (:expedienteId, :usuarioId, :rol, 1, NOW(3), :asignador, 1)
                    ON DUPLICATE KEY UPDATE
                        rol_en_expediente = VALUES(rol_en_expediente),
                        puede_editar = 1,
                        fecha_asignacion = IF(activo = 0, NOW(3), fecha_asignacion),
                        fecha_desasignacion = NULL,
                        usuario_asignacion_id = :asignador,
                        activo = 1
                    """, Map.of(
                    "expedienteId", expedienteId,
                    "usuarioId", miembro.getKey(),
                    "rol", miembro.getValue(),
                    "asignador", usuarioId));
        }

        jdbc.update("DELETE FROM expediente_contrapartes WHERE expediente_id = :id", Map.of("id", expedienteId));
        if (contraparteIds != null) {
            for (Long contraparteId : contraparteIds.stream().filter(Objects::nonNull).distinct().toList()) {
                jdbc.update("""
                        INSERT INTO expediente_contrapartes (expediente_id, contraparte_id, rol_procesal)
                        VALUES (:expedienteId, :contraparteId, 'DEMANDADO')
                        """, Map.of("expedienteId", expedienteId, "contraparteId", contraparteId));
            }
        }
    }

    private Map<Long, String> equipoActivoConRol(Long expedienteId) {
        Map<Long, String> resultado = new LinkedHashMap<>();
        jdbc.query("""
                SELECT usuario_id, rol_en_expediente
                  FROM expediente_equipo
                 WHERE expediente_id = :expedienteId AND activo = 1
                """, Map.of("expedienteId", expedienteId), rs -> {
            resultado.put(rs.getLong("usuario_id"), rs.getString("rol_en_expediente"));
        });
        return resultado;
    }

    private void registrarCambiosEquipo(Integer estudioId, Long expedienteId, Long usuarioAccionId,
                                        Long responsableAnteriorId, Long responsableNuevoId,
                                        Map<Long, String> equipoActual, Map<Long, String> equipoDeseado) {
        if (responsableAnteriorId == null) {
            registrarAuditoriaEquipo(estudioId, expedienteId, usuarioAccionId,
                    "expediente_responsable", "CREAR",
                    nombreUsuario(responsableNuevoId) + " fue asignado como responsable del expediente.");
        } else if (!responsableAnteriorId.equals(responsableNuevoId)) {
            registrarAuditoriaEquipo(estudioId, expedienteId, usuarioAccionId,
                    "expediente_responsable", "ACTUALIZAR",
                    "Responsable anterior: " + nombreUsuario(responsableAnteriorId)
                            + ". Nuevo responsable: " + nombreUsuario(responsableNuevoId) + ".");
        }

        for (Map.Entry<Long, String> anterior : equipoActual.entrySet()) {
            Long usuarioEquipoId = anterior.getKey();
            String rolAnterior = anterior.getValue();
            String rolNuevo = equipoDeseado.get(usuarioEquipoId);

            if ("APOYO".equals(rolAnterior) && rolNuevo == null) {
                registrarAuditoriaEquipo(estudioId, expedienteId, usuarioAccionId,
                        "expediente_equipo", "ELIMINAR",
                        nombreUsuario(usuarioEquipoId) + " fue retirado del equipo de apoyo.");
            } else if ("RESPONSABLE".equals(rolAnterior) && "APOYO".equals(rolNuevo)) {
                registrarAuditoriaEquipo(estudioId, expedienteId, usuarioAccionId,
                        "expediente_equipo", "ACTUALIZAR",
                        nombreUsuario(usuarioEquipoId) + " pasó de responsable a integrante del equipo de apoyo.");
            }
        }

        for (Map.Entry<Long, String> nuevo : equipoDeseado.entrySet()) {
            Long usuarioEquipoId = nuevo.getKey();
            String rolNuevo = nuevo.getValue();
            String rolAnterior = equipoActual.get(usuarioEquipoId);

            if ("APOYO".equals(rolNuevo) && rolAnterior == null) {
                registrarAuditoriaEquipo(estudioId, expedienteId, usuarioAccionId,
                        "expediente_equipo", "CREAR",
                        nombreUsuario(usuarioEquipoId) + " fue añadido al equipo de apoyo.");
            }
        }
    }

    private void registrarAuditoriaEquipo(Integer estudioId, Long expedienteId, Long usuarioAccionId,
                                          String entidad, String accion, String descripcion) {
        jdbc.update("""
                INSERT INTO auditoria
                    (estudio_id, usuario_id, entidad, registro_id, expediente_id,
                     accion, descripcion, exito, fecha)
                VALUES
                    (:estudioId, :usuarioId, :entidad, :registroId, :expedienteId,
                     :accion, :descripcion, 1, NOW(3))
                """, new MapSqlParameterSource()
                .addValue("estudioId", estudioId)
                .addValue("usuarioId", usuarioAccionId, Types.BIGINT)
                .addValue("entidad", entidad)
                .addValue("registroId", expedienteId)
                .addValue("expedienteId", expedienteId)
                .addValue("accion", accion)
                .addValue("descripcion", descripcion));
    }

    private String nombreUsuario(Long usuarioId) {
        if (usuarioId == null) return "Sin responsable";
        List<String> nombres = jdbc.query("SELECT nombre_completo FROM usuarios WHERE id = :id",
                Map.of("id", usuarioId), (rs, n) -> rs.getString(1));
        return nombres.isEmpty() ? "Usuario #" + usuarioId : nombres.getFirst();
    }

    private List<PersonaRelacionada> contrapartesSeguras(Long expedienteId) {
        try {
            return contrapartes(expedienteId);
        } catch (DataAccessException ex) {
            log.warn("No se pudieron cargar las contrapartes del expediente {}: {}",
                    expedienteId, ex.getMostSpecificCause().getMessage());
            return List.of();
        }
    }

    private List<Equipo> equipoSeguro(Long expedienteId) {
        try {
            return equipo(expedienteId);
        } catch (DataAccessException ex) {
            log.warn("No se pudo cargar el equipo del expediente {}: {}",
                    expedienteId, ex.getMostSpecificCause().getMessage());
            return List.of();
        }
    }

    private List<PersonaRelacionada> contrapartes(Long expedienteId) {
        return jdbc.query("""
                SELECT c.id, c.nombre_completo nombre,
                       CONCAT(ec.rol_procesal, IF(c.abogado_contrario IS NULL, '', CONCAT(' · Abg. ', c.abogado_contrario))) detalle
                  FROM expediente_contrapartes ec
                  JOIN contrapartes c ON c.id = ec.contraparte_id
                 WHERE ec.expediente_id = :id
                 ORDER BY c.nombre_completo
                """, Map.of("id", expedienteId),
                (rs, n) -> new PersonaRelacionada(rs.getLong("id"), rs.getString("nombre"), rs.getString("detalle")));
    }

    private List<Equipo> equipo(Long expedienteId) {
        return jdbc.query("""
                SELECT u.id, u.nombre_completo, eq.rol_en_expediente, eq.puede_editar
                  FROM expediente_equipo eq
                  JOIN usuarios u ON u.id = eq.usuario_id
                 WHERE eq.expediente_id = :id AND eq.activo = 1
                 ORDER BY FIELD(eq.rol_en_expediente,'RESPONSABLE','SUPERVISOR','APOYO','ASISTENTE','OBSERVADOR'), u.nombre_completo
                """, Map.of("id", expedienteId),
                (rs, n) -> new Equipo(rs.getLong("id"), rs.getString("nombre_completo"),
                        rs.getString("rol_en_expediente"), rs.getBoolean("puede_editar")));
    }

    private List<Long> idsVisibles(
            Integer estudioId,
            Long usuarioId,
            boolean verTodos,
            boolean verConfidenciales) {

        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT e.id
                  FROM expedientes e
                """);

        if (!verTodos) {
            sql.append("""
                     LEFT JOIN expediente_equipo eq
                       ON eq.expediente_id = e.id
                      AND eq.usuario_id = :usuarioId
                      AND eq.activo = 1
                    """);
        }

        sql.append("""
                 WHERE e.estudio_id = :estudioId
                   AND e.activo = 1
                """);

        if (!verTodos) {
            sql.append(" AND (e.abogado_responsable_id = :usuarioId OR eq.usuario_id IS NOT NULL)");
        }
        // RF-02: los confidenciales se ocultan a los roles operativos "salvo
        // asignacion explicita". Sin verTodos, la consulta ya se limita a los
        // expedientes donde la persona es responsable o esta en el equipo, asi que
        // ahi el filtro solo servia para ocultarle su propio caso (EXP-0088 de la
        // semilla: su responsable recibia 404 al abrirlo). Se aplica solo al ver
        // todo el estudio.
        if (verTodos && !verConfidenciales) {
            sql.append(" AND e.es_confidencial = 0");
        }

        MapSqlParameterSource p = new MapSqlParameterSource().addValue("estudioId", estudioId);
        if (!verTodos) p.addValue("usuarioId", usuarioId);

        return jdbc.query(sql.toString(), p, (rs, rowNum) -> rs.getLong("id"));
    }

    private Long idVisible(
            String uuid,
            Integer estudioId,
            Long usuarioId,
            boolean verTodos,
            boolean verConfidenciales) {

        String sql = """
                SELECT e.id, e.abogado_responsable_id, e.es_confidencial
                  FROM expedientes e
                 WHERE e.uuid_publico = :uuid
                   AND e.estudio_id = :estudioId
                   AND e.activo = 1
                """;

        List<AccesoExpediente> encontrados = jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("uuid", uuid)
                        .addValue("estudioId", estudioId),
                (rs, rowNum) -> new AccesoExpediente(
                        rs.getLong("id"),
                        rs.getLong("abogado_responsable_id"),
                        rs.getBoolean("es_confidencial")));

        if (encontrados.isEmpty()) return null;

        AccesoExpediente fila = encontrados.getFirst();
        Long expedienteId = fila.expedienteId();
        Long responsableId = fila.responsableId();
        boolean confidencial = fila.confidencial();

        // Ver el comentario de idsVisibles: estar asignado basta (RF-02).
        if (responsableId.equals(usuarioId)) return expedienteId;
        if (verTodos) return confidencial && !verConfidenciales ? null : expedienteId;

        Integer miembro = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM expediente_equipo
                 WHERE expediente_id = :expedienteId
                   AND usuario_id = :usuarioId
                   AND activo = 1
                """,
                new MapSqlParameterSource()
                        .addValue("expedienteId", expedienteId)
                        .addValue("usuarioId", usuarioId),
                Integer.class);

        return miembro != null && miembro > 0 ? expedienteId : null;
    }

    /**
     * Cambia el responsable de un expediente desde FUERA de la edicion (la usa la
     * reasignacion de trabajo del modulo de usuarios).
     *
     * Hace lo mismo que la edicion en lo que toca al responsable: actualiza la
     * columna, deja al nuevo como RESPONSABLE en expediente_equipo, retira al
     * anterior del equipo y registra `expediente_responsable` en auditoria, que
     * es lo que pinta la linea de tiempo. A diferencia de la edicion, NO toca al
     * resto del equipo ni las contrapartes: los demas miembros conservan su rol.
     *
     * @return false si el expediente no existe en ese estudio.
     */
    public boolean cambiarResponsable(Integer estudioId, Long expedienteId, Long nuevoResponsableId,
                                      Long usuarioAccionId) {
        List<Long> actuales = jdbc.query("""
                SELECT abogado_responsable_id FROM expedientes
                 WHERE id = :id AND estudio_id = :estudioId
                   FOR UPDATE
                """, Map.of("id", expedienteId, "estudioId", estudioId),
                (rs, n) -> rs.getLong(1));
        if (actuales.isEmpty()) return false;

        Long anteriorId = actuales.getFirst();
        if (anteriorId.equals(nuevoResponsableId)) return true;

        jdbc.update("""
                UPDATE expedientes
                   SET abogado_responsable_id = :nuevo, usuario_modificacion_id = :accion
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("nuevo", nuevoResponsableId)
                .addValue("accion", usuarioAccionId)
                .addValue("id", expedienteId));

        jdbc.update("""
                UPDATE expediente_equipo SET activo = 0, fecha_desasignacion = NOW(3)
                 WHERE expediente_id = :id AND usuario_id = :anterior AND activo = 1
                """, Map.of("id", expedienteId, "anterior", anteriorId));

        jdbc.update("""
                INSERT INTO expediente_equipo
                    (expediente_id, usuario_id, rol_en_expediente, puede_editar,
                     fecha_asignacion, usuario_asignacion_id, activo)
                VALUES (:id, :nuevo, 'RESPONSABLE', 1, NOW(3), :accion, 1)
                ON DUPLICATE KEY UPDATE
                    rol_en_expediente = 'RESPONSABLE',
                    puede_editar = 1,
                    fecha_asignacion = IF(activo = 0, NOW(3), fecha_asignacion),
                    fecha_desasignacion = NULL,
                    usuario_asignacion_id = :accion,
                    activo = 1
                """, Map.of("id", expedienteId, "nuevo", nuevoResponsableId, "accion", usuarioAccionId));

        registrarAuditoriaEquipo(estudioId, expedienteId, usuarioAccionId,
                "expediente_responsable", "ACTUALIZAR",
                "Responsable anterior: " + nombreUsuario(anteriorId)
                        + ". Nuevo responsable: " + nombreUsuario(nuevoResponsableId) + ".");
        return true;
    }

    /**
     * Retira a alguien del equipo de un expediente (cualquier rol salvo
     * RESPONSABLE, que se cambia con cambiarResponsable) y lo registra en la
     * linea de tiempo. Devuelve cuantas filas retiro.
     */
    public int retirarDelEquipo(Integer estudioId, Long expedienteId, Long usuarioId, Long usuarioAccionId) {
        int filas = jdbc.update("""
                UPDATE expediente_equipo eq
                  JOIN expedientes e ON e.id = eq.expediente_id AND e.estudio_id = :estudioId
                   SET eq.activo = 0, eq.fecha_desasignacion = NOW(3)
                 WHERE eq.expediente_id = :id AND eq.usuario_id = :usuarioId
                   AND eq.activo = 1 AND eq.rol_en_expediente <> 'RESPONSABLE'
                """, Map.of("estudioId", estudioId, "id", expedienteId, "usuarioId", usuarioId));
        if (filas > 0) {
            registrarAuditoriaEquipo(estudioId, expedienteId, usuarioAccionId,
                    "expediente_equipo", "ELIMINAR",
                    nombreUsuario(usuarioId) + " fue retirado del equipo del expediente.");
        }
        return filas;
    }

    private MapSqlParameterSource parametrosGuardar(Integer estudioId, Long usuarioId, String codigo, GuardarRequest r) {
        return new MapSqlParameterSource()
                .addValue("estudioId", estudioId)
                .addValue("usuarioId", usuarioId)
                .addValue("codigo", codigo)
                .addValue("numeroExpediente", vacioANull(r.numeroExpediente()), Types.VARCHAR)
                .addValue("numeroCorrelativo", vacioANull(r.numeroCorrelativo()), Types.VARCHAR)
                .addValue("anio", r.anio())
                .addValue("clienteId", r.clienteId())
                .addValue("especialidadId", r.especialidadId())
                .addValue("materiaId", r.materiaId(), Types.BIGINT)
                .addValue("organoId", r.organoId(), Types.BIGINT)
                .addValue("estadoId", r.estadoId())
                .addValue("etapaProcesal", vacioANull(r.etapaProcesal()), Types.VARCHAR)
                .addValue("instancia", normalizar(r.instancia()), Types.VARCHAR)
                .addValue("abogadoId", r.abogadoResponsableId())
                .addValue("socioId", r.socioSupervisorId(), Types.BIGINT)
                .addValue("riesgo", normalizar(r.nivelRiesgo()))
                .addValue("cuantia", r.cuantia(), Types.DECIMAL)
                .addValue("moneda", r.moneda() == null || r.moneda().isBlank() ? "PEN" : r.moneda().trim().toUpperCase())
                .addValue("fechaInicio", r.fechaInicio())
                .addValue("fechaIngreso", r.fechaIngresoEstudio() == null ? LocalDate.now() : r.fechaIngresoEstudio())
                .addValue("fechaCierre", r.fechaCierre(), Types.DATE)
                .addValue("resultado", normalizar(r.resultado()), Types.VARCHAR)
                .addValue("confidencial", r.confidencial())
                .addValue("resumen", vacioANull(r.resumen()), Types.LONGVARCHAR);
    }

    private static Opcion opcion(ResultSet rs) throws SQLException {
        return new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getString("detalle"));
    }

    private static LocalDate getLocalDate(ResultSet rs, String columna) throws SQLException {
        java.sql.Date fecha = rs.getDate(columna);
        return fecha == null ? null : fecha.toLocalDate();
    }

    private static LocalDateTime getLocalDateTime(ResultSet rs, String columna) throws SQLException {
        java.sql.Timestamp fecha = rs.getTimestamp(columna);
        return fecha == null ? null : fecha.toLocalDateTime();
    }

    private static Long getNullableLong(ResultSet rs, String columna) throws SQLException {
        long value = rs.getLong(columna);
        return rs.wasNull() ? null : value;
    }

    private static String like(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim().toLowerCase() + "%";
    }

    private static String normalizar(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase();
    }

    private static String vacioANull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Resultado tipado para validar acceso a un expediente.
     * Igual que Documentos, se lee TINYINT(1) con ResultSet#getBoolean para
     * no depender de si el driver MySQL lo expone como Boolean o Number.
     */
    private record AccesoExpediente(Long expedienteId, Long responsableId, boolean confidencial) {
    }

    private record ExpedienteEdicion(Long id, Long responsableId) {
    }

}
