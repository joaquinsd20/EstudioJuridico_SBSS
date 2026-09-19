package com.sbss.lexia.usuarios.repository;

import com.sbss.lexia.usuarios.dto.UsuarioDtos.CargaTrabajo;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.RolOpcion;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.UsuarioDetalle;
import com.sbss.lexia.usuarios.dto.UsuarioDtos.UsuarioLista;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas del modulo de usuarios.
 *
 * JDBC y no JPA a proposito: aqui se leen agregados de seis tablas distintas
 * (la carga de trabajo) y se escriben filas de `usuarios_roles`; con entidades
 * saldria una consulta por usuario y por relacion. La entidad `Usuario` de
 * `seguridad` se sigue usando para el login, que si necesita el objeto.
 *
 * TODA consulta lleva `estudio_id`: es la frontera de multi-tenencia.
 */
@Repository
@RequiredArgsConstructor
public class UsuarioGestionRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Listado del personal del estudio. Solo INTERNO: los usuarios del portal
     * del cliente pertenecen a la ficha de su cliente, no a esta pantalla.
     */
    public List<UsuarioLista> listar(Integer estudioId, String texto, String rol, String estado) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.uuid_publico, u.codigo_usuario, u.nombre_completo, u.email, u.cargo,
                       u.estado, u.activo, u.debe_cambiar_password, u.ultimo_acceso,
                       GROUP_CONCAT(r.codigo ORDER BY r.nivel_acceso DESC) roles
                  FROM usuarios u
                  LEFT JOIN usuarios_roles ur ON ur.usuario_id = u.id
                  LEFT JOIN roles r ON r.id = ur.rol_id
                 WHERE u.estudio_id = :estudioId
                   AND u.tipo_usuario = 'INTERNO'
                """);

        MapSqlParameterSource p = new MapSqlParameterSource("estudioId", estudioId);

        if (texto != null && !texto.isBlank()) {
            // La colacion utf8mb4_0900_ai_ci ya ignora mayusculas y tildes, asi
            // que no hace falta lower() (que ademas anularia el indice).
            sql.append(" AND (u.nombre_completo LIKE :texto OR u.email LIKE :texto"
                    + " OR u.codigo_usuario LIKE :texto) ");
            p.addValue("texto", "%" + texto.trim() + "%");
        }
        if (rol != null && !rol.isBlank()) {
            // Subconsulta y no filtro sobre el JOIN: con el JOIN, filtrar por un
            // rol recortaria TAMBIEN la lista de roles que se muestra de cada
            // usuario, y alguien con Socio + Abogado apareceria con uno solo.
            sql.append(" AND EXISTS (SELECT 1 FROM usuarios_roles ur2 JOIN roles r2 ON r2.id = ur2.rol_id"
                    + " WHERE ur2.usuario_id = u.id AND r2.codigo = :rol) ");
            p.addValue("rol", rol.trim().toUpperCase());
        }
        if ("ACTIVOS".equalsIgnoreCase(estado)) {
            sql.append(" AND u.activo = 1 AND u.estado = 'ACTIVO' ");
        } else if ("INACTIVOS".equalsIgnoreCase(estado)) {
            sql.append(" AND (u.activo = 0 OR u.estado = 'INACTIVO') ");
        } else if ("BLOQUEADOS".equalsIgnoreCase(estado)) {
            sql.append(" AND u.estado = 'BLOQUEADO' ");
        }

        sql.append(" GROUP BY u.id ORDER BY u.activo DESC, u.nombre_completo ");

        return jdbc.query(sql.toString(), p, (rs, n) -> new UsuarioLista(
                rs.getString("uuid_publico"),
                rs.getString("codigo_usuario"),
                rs.getString("nombre_completo"),
                rs.getString("email"),
                rs.getString("cargo"),
                rs.getString("estado"),
                rs.getBoolean("activo"),
                rs.getBoolean("debe_cambiar_password"),
                fecha(rs, "ultimo_acceso"),
                roles(rs.getString("roles"))));
    }

    public UsuarioDetalle detalle(String uuid, Integer estudioId) {
        Long id = idDe(uuid, estudioId);
        if (id == null) {
            return null;
        }
        Map<String, Object> p = Map.of("id", id);
        return jdbc.queryForObject("""
                SELECT u.uuid_publico, u.codigo_usuario, u.nombres, u.apellidos, u.email, u.cargo,
                       u.telefono, u.tipo_documento, u.numero_documento, u.numero_colegiatura,
                       u.estado, u.activo, u.debe_cambiar_password, u.ultimo_acceso, u.fecha_creacion,
                       (SELECT GROUP_CONCAT(r.codigo ORDER BY r.nivel_acceso DESC)
                          FROM usuarios_roles ur JOIN roles r ON r.id = ur.rol_id
                         WHERE ur.usuario_id = u.id) roles
                  FROM usuarios u WHERE u.id = :id
                """, p, (rs, n) -> new UsuarioDetalle(
                rs.getString("uuid_publico"),
                rs.getString("codigo_usuario"),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("email"),
                rs.getString("cargo"),
                rs.getString("telefono"),
                rs.getString("tipo_documento"),
                rs.getString("numero_documento"),
                rs.getString("numero_colegiatura"),
                rs.getString("estado"),
                rs.getBoolean("activo"),
                rs.getBoolean("debe_cambiar_password"),
                fecha(rs, "ultimo_acceso"),
                fecha(rs, "fecha_creacion"),
                roles(rs.getString("roles")),
                cargaTrabajo(id)));
    }

    /**
     * De que es responsable esta persona AHORA MISMO.
     *
     * Cada cuenta sale de una FK NOT NULL hacia `usuarios`: expedientes,
     * plazos, tareas, participacion en agenda y versiones de documento. Son
     * justo los enlaces que hacen que dar de baja a alguien sin reasignar deje
     * compromisos a nombre de una cuenta que ya nadie mira.
     */
    public CargaTrabajo cargaTrabajo(Long usuarioId) {
        Map<String, Object> p = Map.of("id", usuarioId);
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM expedientes e
                    WHERE e.abogado_responsable_id = :id AND e.activo = 1)                       AS expedientes,
                  (SELECT COUNT(*) FROM plazos p
                    WHERE p.usuario_responsable_id = :id
                      AND p.estado NOT IN ('CUMPLIDO','CANCELADO'))                              AS plazos,
                  (SELECT COUNT(*) FROM tareas t
                    WHERE t.usuario_responsable_id = :id
                      AND t.estado NOT IN ('COMPLETADA','CANCELADA'))                            AS tareas,
                  (SELECT COUNT(*) FROM evento_participantes ep JOIN eventos_agenda ev ON ev.id = ep.evento_id
                    WHERE ep.usuario_id = :id AND ev.fecha_inicio >= NOW(3))                     AS eventos,
                  (SELECT COUNT(*) FROM documento_versiones dv WHERE dv.usuario_carga_id = :id)  AS versiones
                """, p, (rs, n) -> new CargaTrabajo(
                rs.getInt("expedientes"), rs.getInt("plazos"), rs.getInt("tareas"),
                rs.getInt("eventos"), rs.getInt("versiones")));
    }

    /** Roles asignables. El del portal se marca para que la UI no lo ofrezca aqui. */
    public List<RolOpcion> roles() {
        return jdbc.query("""
                SELECT codigo, nombre, nivel_acceso FROM roles WHERE activo = 1
                 ORDER BY nivel_acceso DESC
                """, Map.of(), (rs, n) -> new RolOpcion(
                rs.getString("codigo"), rs.getString("nombre"), rs.getInt("nivel_acceso"),
                "CLIENTE".equals(rs.getString("codigo"))));
    }

    /** El id interno, solo dentro del estudio de quien consulta. Null si no existe. */
    public Long idDe(String uuid, Integer estudioId) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM usuarios
                 WHERE uuid_publico = :uuid AND estudio_id = :estudioId AND tipo_usuario = 'INTERNO'
                """, Map.of("uuid", uuid, "estudioId", estudioId), (rs, n) -> rs.getLong("id"));
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean existeEmail(String email) {
        // Sin estudio_id: uq_usuarios_email es global, y un correo repetido en
        // otro estudio fallaria igual al insertar.
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email = :email",
                Map.of("email", email), Integer.class);
        return n != null && n > 0;
    }

    /** Siguiente codigo U-0001, U-0002... dentro del estudio (uq_usuarios_codigo). */
    public String siguienteCodigo(Integer estudioId) {
        Integer max = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(codigo_usuario, 3) AS UNSIGNED)), 0)
                  FROM usuarios
                 WHERE estudio_id = :estudioId AND codigo_usuario REGEXP '^U-[0-9]+$'
                """, Map.of("estudioId", estudioId), Integer.class);
        return String.format("U-%04d", (max == null ? 0 : max) + 1);
    }

    public List<String> codigosDeRolExistentes(List<String> codigos) {
        return jdbc.query("SELECT codigo FROM roles WHERE codigo IN (:codigos) AND activo = 1",
                new MapSqlParameterSource("codigos", codigos), (rs, n) -> rs.getString("codigo"));
    }

    /** Administradores activos del estudio, sin contar al usuario indicado. */
    public int administradoresActivosSalvo(Integer estudioId, Long usuarioId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT u.id)
                  FROM usuarios u
                  JOIN usuarios_roles ur ON ur.usuario_id = u.id
                  JOIN roles r ON r.id = ur.rol_id
                 WHERE u.estudio_id = :estudioId AND u.id <> :usuarioId
                   AND u.activo = 1 AND u.estado = 'ACTIVO' AND r.codigo = 'ADMINISTRADOR'
                """, Map.of("estudioId", estudioId, "usuarioId", usuarioId), Integer.class);
        return n == null ? 0 : n;
    }

    public String crear(Integer estudioId, String codigo, String hash, Long autorId,
            com.sbss.lexia.usuarios.dto.UsuarioDtos.GuardarUsuarioRequest r) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("estudioId", estudioId)
                .addValue("codigo", codigo)
                .addValue("nombres", r.nombres().trim())
                .addValue("apellidos", r.apellidos().trim())
                .addValue("email", r.email().trim().toLowerCase())
                .addValue("hash", hash)
                .addValue("cargo", vacioANulo(r.cargo()))
                .addValue("telefono", vacioANulo(r.telefono()))
                .addValue("tipoDocumento", vacioANulo(r.tipoDocumento()))
                .addValue("numeroDocumento", vacioANulo(r.numeroDocumento()))
                .addValue("colegiatura", vacioANulo(r.numeroColegiatura()))
                .addValue("autorId", autorId);

        jdbc.update("""
                INSERT INTO usuarios (estudio_id, tipo_usuario, codigo_usuario, nombres, apellidos,
                                      email, password_hash, cargo, telefono, tipo_documento,
                                      numero_documento, numero_colegiatura, estado,
                                      debe_cambiar_password, usuario_creacion_id)
                VALUES (:estudioId, 'INTERNO', :codigo, :nombres, :apellidos,
                        :email, :hash, :cargo, :telefono, :tipoDocumento,
                        :numeroDocumento, :colegiatura, 'ACTIVO', 1, :autorId)
                """, p);

        return jdbc.queryForObject("SELECT uuid_publico FROM usuarios WHERE id = LAST_INSERT_ID()",
                Map.of(), String.class);
    }

    public void actualizarDatos(Long id, Long autorId,
            com.sbss.lexia.usuarios.dto.UsuarioDtos.GuardarUsuarioRequest r) {
        jdbc.update("""
                UPDATE usuarios
                   SET nombres = :nombres, apellidos = :apellidos, cargo = :cargo,
                       telefono = :telefono, tipo_documento = :tipoDocumento,
                       numero_documento = :numeroDocumento, numero_colegiatura = :colegiatura,
                       usuario_modificacion_id = :autorId
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("nombres", r.nombres().trim())
                .addValue("apellidos", r.apellidos().trim())
                .addValue("cargo", vacioANulo(r.cargo()))
                .addValue("telefono", vacioANulo(r.telefono()))
                .addValue("tipoDocumento", vacioANulo(r.tipoDocumento()))
                .addValue("numeroDocumento", vacioANulo(r.numeroDocumento()))
                .addValue("colegiatura", vacioANulo(r.numeroColegiatura()))
                .addValue("autorId", autorId));
    }

    /** Reemplaza los roles del usuario por los indicados, emparejando por codigo. */
    public void reemplazarRoles(Long usuarioId, List<String> codigos, Long autorId) {
        jdbc.update("DELETE FROM usuarios_roles WHERE usuario_id = :id", Map.of("id", usuarioId));
        for (String codigo : codigos) {
            jdbc.update("""
                    INSERT INTO usuarios_roles (usuario_id, rol_id, usuario_asignacion_id)
                    SELECT :id, r.id, :autorId FROM roles r WHERE r.codigo = :codigo
                    """, new MapSqlParameterSource()
                    .addValue("id", usuarioId).addValue("codigo", codigo).addValue("autorId", autorId));
        }
    }

    public void cambiarActivo(Long id, boolean activo, Long autorId) {
        jdbc.update("""
                UPDATE usuarios
                   SET activo = :activo, estado = :estado, usuario_modificacion_id = :autorId
                 WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("activo", activo)
                .addValue("estado", activo ? "ACTIVO" : "INACTIVO")
                .addValue("autorId", autorId));
    }

    /** Devuelve a un usuario bloqueado por intentos fallidos a ACTIVO. */
    public void desbloquear(Long id, Long autorId) {
        jdbc.update("""
                UPDATE usuarios
                   SET estado = 'ACTIVO', intentos_fallidos = 0, fecha_bloqueo = NULL,
                       usuario_modificacion_id = :autorId
                 WHERE id = :id AND estado = 'BLOQUEADO'
                """, Map.of("id", id, "autorId", autorId));
    }

    /**
     * Contrasena temporal: obliga a cambiarla y, al mover
     * `fecha_cambio_password`, invalida los tokens ya emitidos (V13). Si se
     * restablece porque la cuenta esta comprometida, dejar viva la sesion del
     * intruso haria inutil el restablecimiento.
     */
    public void restablecerPassword(Long id, String hash, Long autorId) {
        jdbc.update("""
                UPDATE usuarios
                   SET password_hash = :hash, debe_cambiar_password = 1,
                       fecha_cambio_password = DATE_SUB(NOW(3), INTERVAL MICROSECOND(NOW(3)) MICROSECOND),
                       intentos_fallidos = 0, estado = IF(estado = 'BLOQUEADO', 'ACTIVO', estado),
                       fecha_bloqueo = NULL, usuario_modificacion_id = :autorId
                 WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", id).addValue("hash", hash)
                .addValue("autorId", autorId));
    }

    public String emailDe(Long id) {
        return jdbc.queryForObject("SELECT email FROM usuarios WHERE id = :id",
                Map.of("id", id), String.class);
    }

    public String uuidDe(Long id) {
        return jdbc.queryForObject("SELECT uuid_publico FROM usuarios WHERE id = :id",
                Map.of("id", id), String.class);
    }

    private static List<String> roles(String concatenados) {
        return concatenados == null || concatenados.isBlank()
                ? List.of()
                : new ArrayList<>(Arrays.asList(concatenados.split(",")));
    }

    private static java.time.LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
