package com.sbss.lexia.actuaciones.repository;

import com.sbss.lexia.actuaciones.dto.ActuacionDtos.*;
import com.sbss.lexia.shared.security.SesionActual;
import com.sbss.lexia.shared.security.VisibilidadExpedientes;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ActuacionRepositoryJdbc {

    private final NamedParameterJdbcTemplate jdbc;

    public List<ActuacionLista> listar(String texto, Long tipoId, Long expedienteId, Long responsableId,
            String estado, LocalDate desde, LocalDate hasta, int limite) {
        MapSqlParameterSource p = base();
        StringBuilder sql = new StringBuilder("""
                SELECT a.uuid_publico, a.fecha_hora, ta.id tipo_id, ta.codigo tipo_codigo,
                       ta.nombre tipo_nombre, ta.categoria, e.id expediente_id,
                       e.codigo_interno expediente_codigo, e.numero_expediente,
                       c.nombre_busqueda cliente_nombre, a.asunto,
                       u.id responsable_id, u.nombre_completo responsable, a.estado,
                       a.resultado, a.proxima_accion, a.tiempo_minutos, a.es_facturable,
                       a.costo, a.moneda,
                       (SELECT COUNT(*) FROM documentos d WHERE d.actuacion_id=a.id AND d.activo=1 AND d.en_papelera=0) documentos,
                       (SELECT COUNT(*) FROM tareas t WHERE t.actuacion_origen_id=a.id AND t.activo=1) tareas,
                       (SELECT COUNT(*) FROM plazos pz WHERE pz.actuacion_origen_id=a.id AND pz.activo=1) plazos,
                       (SELECT COUNT(*) FROM eventos_agenda ev WHERE ev.actuacion_id=a.id AND ev.activo=1) eventos
                  FROM actuaciones a
                  JOIN tipos_actuacion ta ON ta.id=a.tipo_actuacion_id
                  JOIN expedientes e ON e.id=a.expediente_id
                  JOIN clientes c ON c.id=e.cliente_id
                  JOIN usuarios u ON u.id=a.usuario_responsable_id
                 WHERE a.activo=1 AND e.activo=1 AND e.estudio_id=:estudioId
                """);
        aplicarVisibilidad(sql, p);
        if (texto != null && !texto.isBlank()) {
            sql.append(" AND LOWER(CONCAT_WS(' ',a.asunto,a.descripcion,a.resultado,a.proxima_accion,e.codigo_interno,e.numero_expediente,c.nombre_busqueda,u.nombre_completo,ta.nombre)) LIKE :texto");
            p.addValue("texto", "%" + texto.trim().toLowerCase() + "%");
        }
        if (tipoId != null) { sql.append(" AND a.tipo_actuacion_id=:tipoId"); p.addValue("tipoId", tipoId); }
        if (expedienteId != null) { sql.append(" AND a.expediente_id=:expedienteId"); p.addValue("expedienteId", expedienteId); }
        if (responsableId != null) { sql.append(" AND a.usuario_responsable_id=:responsableId"); p.addValue("responsableId", responsableId); }
        if (estado != null && !estado.isBlank()) { sql.append(" AND a.estado=:estado"); p.addValue("estado", estado.trim().toUpperCase()); }
        if (desde != null) { sql.append(" AND DATE(a.fecha_hora)>=:desde"); p.addValue("desde", desde); }
        if (hasta != null) { sql.append(" AND DATE(a.fecha_hora)<=:hasta"); p.addValue("hasta", hasta); }
        sql.append(" ORDER BY a.fecha_hora DESC, a.id DESC LIMIT :limite");
        p.addValue("limite", Math.max(1, Math.min(limite, 500)));
        return jdbc.query(sql.toString(), p, this::mapLista);
    }

    public Resumen resumen() {
        MapSqlParameterSource p = base();
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) total,
                       SUM(a.estado='PENDIENTE') pendientes,
                       SUM(a.estado='EN_TRAMITE') en_tramite,
                       SUM(a.estado='COMPLETADO') completadas,
                       COALESCE(SUM(CASE WHEN a.es_facturable=1 THEN a.tiempo_minutos ELSE 0 END),0) minutos_facturables,
                       COALESCE(SUM(a.costo),0) costo_total
                  FROM actuaciones a
                  JOIN expedientes e ON e.id=a.expediente_id
                 WHERE a.activo=1 AND e.activo=1 AND e.estudio_id=:estudioId
                """);
        aplicarVisibilidad(sql, p);
        return jdbc.queryForObject(sql.toString(), p, (rs, n) -> new Resumen(
                rs.getLong("total"), rs.getLong("pendientes"), rs.getLong("en_tramite"),
                rs.getLong("completadas"), rs.getLong("minutos_facturables"), rs.getBigDecimal("costo_total")));
    }

    public ActuacionDetalle detalle(String uuid) {
        MapSqlParameterSource p = base().addValue("uuid", uuid);
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.uuid_publico, a.fecha_hora, ta.id tipo_id, ta.codigo tipo_codigo,
                       ta.nombre tipo_nombre, ta.categoria, e.id expediente_id,
                       e.codigo_interno expediente_codigo, e.numero_expediente,
                       c.nombre_busqueda cliente_nombre, a.asunto,
                       u.id responsable_id, u.nombre_completo responsable, a.estado,
                       a.resultado, a.proxima_accion, a.tiempo_minutos, a.es_facturable,
                       a.costo, a.moneda, a.descripcion, a.origen, a.requiere_verificacion,
                       (SELECT COUNT(*) FROM documentos d WHERE d.actuacion_id=a.id AND d.activo=1 AND d.en_papelera=0) documentos,
                       (SELECT COUNT(*) FROM tareas t WHERE t.actuacion_origen_id=a.id AND t.activo=1) tareas,
                       (SELECT COUNT(*) FROM plazos pz WHERE pz.actuacion_origen_id=a.id AND pz.activo=1) plazos,
                       (SELECT COUNT(*) FROM eventos_agenda ev WHERE ev.actuacion_id=a.id AND ev.activo=1) eventos
                  FROM actuaciones a
                  JOIN tipos_actuacion ta ON ta.id=a.tipo_actuacion_id
                  JOIN expedientes e ON e.id=a.expediente_id
                  JOIN clientes c ON c.id=e.cliente_id
                  JOIN usuarios u ON u.id=a.usuario_responsable_id
                 WHERE a.uuid_publico=:uuid AND a.activo=1 AND e.activo=1 AND e.estudio_id=:estudioId
                """);
        aplicarVisibilidad(sql, p);
        List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(), p);
        if (rows.isEmpty()) return null;
        long id = ((Number) rows.getFirst().get("id")).longValue();
        ActuacionLista lista = jdbc.query(sql.toString(), p, this::mapListaConExtras).getFirst();
        String descripcion = (String) rows.getFirst().get("descripcion");
        String origen = (String) rows.getFirst().get("origen");
        boolean requiere = bool(rows.getFirst().get("requiere_verificacion"));
        return new ActuacionDetalle(lista, descripcion, origen, requiere,
                documentos(id), tareas(id), plazos(id), eventos(id), alertas(id));
    }

    public Catalogos catalogos() {
        return new Catalogos(expedientes(), tiposActuacion(), usuarios(), tiposPlazo());
    }

    public List<DocumentoOpcion> documentosDisponibles(Long expedienteId) {
        MapSqlParameterSource p = base().addValue("expedienteId", expedienteId);
        StringBuilder sql = new StringBuilder("""
                SELECT d.id,d.uuid_publico,d.nombre,d.categoria,d.estado,dv.nombre_archivo
                  FROM documentos d
                  JOIN expedientes e ON e.id=d.expediente_id
                  LEFT JOIN documento_versiones dv ON dv.id=d.version_vigente_id
                 WHERE d.expediente_id=:expedienteId AND d.activo=1 AND d.en_papelera=0
                   AND d.actuacion_id IS NULL AND e.estudio_id=:estudioId
                """);
        aplicarVisibilidad(sql, p);
        sql.append(" ORDER BY d.fecha_creacion DESC");
        return jdbc.query(sql.toString(), p, (rs,n)->new DocumentoOpcion(rs.getLong("id"),rs.getString("uuid_publico"),
                rs.getString("nombre"),rs.getString("categoria"),rs.getString("estado"),rs.getString("nombre_archivo")));
    }

    public TipoActuacionOpcion tipoActuacion(Long id) {
        List<TipoActuacionOpcion> l = jdbc.query("""
                SELECT id,codigo,nombre,categoria,genera_plazo,genera_tarea,genera_evento_agenda,requiere_documento
                  FROM tipos_actuacion WHERE id=:id AND estudio_id=:estudioId AND activo=1
                """, Map.of("id", id, "estudioId", SesionActual.estudioId()), (rs,n)->mapTipo(rs));
        return l.isEmpty()?null:l.getFirst();
    }

    public TipoPlazoOpcion tipoPlazo(Long id) {
        if (id == null) return null;
        List<TipoPlazoOpcion> l = jdbc.query("""
                SELECT id,codigo,nombre,dias_por_defecto,tipo_computo_defecto,nivel_riesgo_defecto
                  FROM tipos_plazo WHERE id=:id AND estudio_id=:estudioId AND activo=1
                """, Map.of("id",id,"estudioId",SesionActual.estudioId()), (rs,n)->mapTipoPlazo(rs));
        return l.isEmpty()?null:l.getFirst();
    }

    public boolean expedienteExiste(Long id) {
        MapSqlParameterSource p = base().addValue("id",id);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM expedientes e WHERE e.id=:id AND e.activo=1 AND e.estudio_id=:estudioId");
        aplicarVisibilidad(sql, p);
        Integer n=jdbc.queryForObject(sql.toString(),p,Integer.class); return n!=null&&n>0;
    }

    public boolean usuarioValido(Long id) {
        Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id=:id AND estudio_id=:estudioId AND activo=1 AND estado='ACTIVO' AND tipo_usuario='INTERNO'",
                Map.of("id",id,"estudioId",SesionActual.estudioId()),Integer.class); return n!=null&&n>0;
    }

    public boolean documentoDisponible(Long id, Long expedienteId) {
        if(id==null)return true;
        Integer n=jdbc.queryForObject("""
                SELECT COUNT(*) FROM documentos d JOIN expedientes e ON e.id=d.expediente_id
                 WHERE d.id=:id AND d.expediente_id=:expedienteId AND d.activo=1 AND d.en_papelera=0
                   AND d.actuacion_id IS NULL AND e.estudio_id=:estudioId
                """,Map.of("id",id,"expedienteId",expedienteId,"estudioId",SesionActual.estudioId()),Integer.class);
        return n!=null&&n>0;
    }

    public Long insertarActuacion(Long expedienteId, Long tipoId, LocalDateTime fechaHora, String asunto, String descripcion,
            Long responsableId, String estado, String resultado, String proximaAccion, Integer tiempoMinutos,
            boolean facturable, BigDecimal costo, String moneda, Long creadorId) {
        MapSqlParameterSource p=new MapSqlParameterSource()
                .addValue("expedienteId",expedienteId).addValue("tipoId",tipoId).addValue("fechaHora",fechaHora)
                .addValue("asunto",asunto).addValue("descripcion",descripcion).addValue("responsableId",responsableId)
                .addValue("estado",estado).addValue("resultado",resultado).addValue("proximaAccion",proximaAccion)
                .addValue("tiempo",tiempoMinutos).addValue("facturable",facturable).addValue("costo",costo)
                .addValue("moneda",moneda).addValue("creadorId",creadorId);
        return insertar("""
                INSERT INTO actuaciones(expediente_id,tipo_actuacion_id,fecha_hora,asunto,descripcion,usuario_responsable_id,
                  estado,resultado,proxima_accion,tiempo_minutos,es_facturable,costo,moneda,origen,requiere_verificacion,usuario_creacion_id)
                VALUES(:expedienteId,:tipoId,:fechaHora,:asunto,:descripcion,:responsableId,:estado,:resultado,:proximaAccion,
                  :tiempo,:facturable,:costo,:moneda,'MANUAL',0,:creadorId)
                """,p);
    }

    public Long insertarPlazo(Long actuacionId, Long expedienteId, Long tipoPlazoId, String descripcion,
            LocalDate fechaNotificacion, int dias, String tipoComputo, String riesgo, Long responsableId,
            Long revisorId, boolean requiereRevision, String motivoRevision, String actuacionRequerida, Long creadorId) {
        String ambito = jdbc.queryForObject("""
                SELECT o.distrito_judicial FROM expedientes e LEFT JOIN organos_jurisdiccionales o ON o.id=e.organo_id WHERE e.id=:id
                """,Map.of("id",expedienteId),String.class);
        LocalDate inicio = jdbc.queryForObject("SELECT fn_siguiente_dia_habil(:fecha,:ambito)",
                new MapSqlParameterSource().addValue("fecha",fechaNotificacion).addValue("ambito",ambito),LocalDate.class);
        LocalDate venc = jdbc.queryForObject("SELECT fn_calcular_vencimiento(:inicio,:dias,:tipo,:ambito)",
                new MapSqlParameterSource().addValue("inicio",inicio).addValue("dias",dias).addValue("tipo",tipoComputo).addValue("ambito",ambito),LocalDate.class);
        Integer colchon = valorConfigNumero("plazo_colchon_interno_dias",2);
        LocalDate interna=venc==null?null:venc.minusDays(colchon==null?2:colchon);
        MapSqlParameterSource p=new MapSqlParameterSource()
                .addValue("expedienteId",expedienteId).addValue("actuacionId",actuacionId).addValue("tipoPlazoId",tipoPlazoId)
                .addValue("descripcion",descripcion).addValue("fechaNotif",fechaNotificacion).addValue("inicio",inicio)
                .addValue("dias",dias).addValue("tipo",tipoComputo).addValue("venc",venc).addValue("interna",interna)
                .addValue("responsableId",responsableId).addValue("revisorId",revisorId).addValue("riesgo",riesgo)
                .addValue("requiere",requiereRevision).addValue("motivo",motivoRevision).addValue("actReq",actuacionRequerida)
                .addValue("creadorId",creadorId);
        return insertar("""
                INSERT INTO plazos(expediente_id,actuacion_origen_id,tipo_plazo_id,descripcion,origen,fecha_notificacion,fecha_inicio_computo,
                  dias_plazo,tipo_computo,fecha_vencimiento,fecha_vencimiento_interna,usuario_responsable_id,usuario_revisor_id,
                  estado,nivel_riesgo,requiere_revision_profesional,motivo_revision,actuacion_requerida,usuario_creacion_id)
                VALUES(:expedienteId,:actuacionId,:tipoPlazoId,:descripcion,'ACTUACION',:fechaNotif,:inicio,:dias,:tipo,:venc,:interna,
                  :responsableId,:revisorId,'CALCULADO',:riesgo,:requiere,:motivo,:actReq,:creadorId)
                """,p);
    }

    public Long insertarTarea(Long actuacionId, Long expedienteId, Long plazoId, String titulo, String descripcion,
            Long responsableId, Long supervisorId, String prioridad, LocalDateTime fechaLimite, Integer estimado, Long creadorId) {
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("expedienteId",expedienteId).addValue("actuacionId",actuacionId)
                .addValue("plazoId",plazoId).addValue("titulo",titulo).addValue("descripcion",descripcion).addValue("responsableId",responsableId)
                .addValue("supervisorId",supervisorId).addValue("prioridad",prioridad).addValue("fechaLimite",fechaLimite)
                .addValue("estimado",estimado).addValue("creadorId",creadorId);
        return insertar("""
                INSERT INTO tareas(expediente_id,actuacion_origen_id,plazo_id,titulo,descripcion,usuario_responsable_id,usuario_supervisor_id,
                  prioridad,estado,fecha_limite,tiempo_estimado_minutos,usuario_creacion_id)
                VALUES(:expedienteId,:actuacionId,:plazoId,:titulo,:descripcion,:responsableId,:supervisorId,:prioridad,'PENDIENTE',
                  :fechaLimite,:estimado,:creadorId)
                """,p);
    }

    public Long insertarEvento(Long actuacionId, Long expedienteId, Long plazoId, String tipo, String titulo, String descripcion,
            LocalDateTime inicio, LocalDateTime fin, String modalidad, String ubicacion, String enlace, Integer recordatorio, Long creadorId) {
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("estudioId",SesionActual.estudioId()).addValue("expedienteId",expedienteId)
                .addValue("actuacionId",actuacionId).addValue("plazoId",plazoId).addValue("tipo",tipo).addValue("titulo",titulo)
                .addValue("descripcion",descripcion).addValue("inicio",inicio).addValue("fin",fin).addValue("modalidad",modalidad)
                .addValue("ubicacion",ubicacion).addValue("enlace",enlace).addValue("recordatorio",recordatorio).addValue("creadorId",creadorId);
        return insertar("""
                INSERT INTO eventos_agenda(estudio_id,expediente_id,actuacion_id,plazo_id,tipo,titulo,descripcion,fecha_inicio,fecha_fin,
                  modalidad,ubicacion,enlace_virtual,estado,recordatorio_minutos,usuario_creacion_id)
                VALUES(:estudioId,:expedienteId,:actuacionId,:plazoId,:tipo,:titulo,:descripcion,:inicio,:fin,:modalidad,:ubicacion,:enlace,
                  'PROGRAMADO',:recordatorio,:creadorId)
                """,p);
    }

    public Long insertarDocumentoPendiente(Long actuacionId, Long expedienteId, Long tareaId, String nombre, String descripcion,
            String categoria, boolean confidencial, Long creadorId) {
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("expedienteId",expedienteId).addValue("actuacionId",actuacionId)
                .addValue("tareaId",tareaId).addValue("nombre",nombre).addValue("descripcion",descripcion).addValue("categoria",categoria)
                .addValue("confidencial",confidencial).addValue("creadorId",creadorId);
        return insertar("""
                INSERT INTO documentos(expediente_id,actuacion_id,tarea_id,nombre,descripcion,categoria,estado,es_confidencial,usuario_creacion_id)
                VALUES(:expedienteId,:actuacionId,:tareaId,:nombre,:descripcion,:categoria,'BORRADOR',:confidencial,:creadorId)
                """,p);
    }

    public void vincularDocumento(Long documentoId, Long actuacionId) {
        jdbc.update("UPDATE documentos SET actuacion_id=:actuacionId, usuario_modificacion_id=:usuarioId WHERE id=:id AND actuacion_id IS NULL",
                Map.of("actuacionId",actuacionId,"usuarioId",SesionActual.usuarioId(),"id",documentoId));
    }

    public void actualizarUltimaActuacion(Long expedienteId, LocalDateTime fecha) {
        jdbc.update("""
                UPDATE expedientes SET fecha_ultima_actuacion=GREATEST(COALESCE(fecha_ultima_actuacion,:fecha),:fecha),
                  usuario_modificacion_id=:usuarioId WHERE id=:id
                """,Map.of("fecha",fecha,"usuarioId",SesionActual.usuarioId(),"id",expedienteId));
    }

    public Long insertarAlerta(Long actuacionId, Long plazoId, Long expedienteId, Long usuarioId, String tipo, Integer diasAntes,
            String mensaje, String severidad, LocalDateTime fechaProgramada) {
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("estudioId",SesionActual.estudioId()).addValue("plazoId",plazoId)
                .addValue("actuacionId",actuacionId).addValue("expedienteId",expedienteId).addValue("usuarioId",usuarioId)
                .addValue("tipo",tipo).addValue("dias",diasAntes).addValue("mensaje",mensaje).addValue("severidad",severidad)
                .addValue("fecha",fechaProgramada);
        return insertar("""
                INSERT INTO alertas(estudio_id,plazo_id,actuacion_id,expediente_id,usuario_destino_id,tipo,dias_antes,mensaje,severidad,
                  fecha_programada,canal,estado)
                VALUES(:estudioId,:plazoId,:actuacionId,:expedienteId,:usuarioId,:tipo,:dias,:mensaje,:severidad,:fecha,'SISTEMA','PROGRAMADA')
                """,p);
    }

    public String uuid(String tabla, Long id) {
        if (!List.of("actuaciones","tareas","plazos","eventos_agenda","documentos").contains(tabla)) throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT uuid_publico FROM "+tabla+" WHERE id=:id",Map.of("id",id),String.class);
    }

    public String valorConfig(String clave) {
        List<String> l=jdbc.query("SELECT valor FROM configuracion WHERE estudio_id=:estudioId AND clave=:clave",
                Map.of("estudioId",SesionActual.estudioId(),"clave",clave),(rs,n)->rs.getString(1));
        return l.isEmpty()?null:l.getFirst();
    }

    public LocalDate fechaVencimientoPlazo(Long plazoId) {
        return jdbc.queryForObject("SELECT fecha_vencimiento FROM plazos WHERE id=:id",Map.of("id",plazoId),LocalDate.class);
    }

    private Integer valorConfigNumero(String clave, Integer defecto) {
        try { String v=valorConfig(clave); return v==null?defecto:Integer.valueOf(v.trim()); } catch(Exception e){return defecto;}
    }

    private List<ExpedienteOpcion> expedientes() {
        MapSqlParameterSource p=base();
        StringBuilder sql=new StringBuilder("""
                SELECT e.id,e.codigo_interno,e.numero_expediente,c.nombre_busqueda,u.id responsable_id,u.nombre_completo responsable
                  FROM expedientes e JOIN clientes c ON c.id=e.cliente_id JOIN usuarios u ON u.id=e.abogado_responsable_id
                 WHERE e.estudio_id=:estudioId AND e.activo=1
                """);
        aplicarVisibilidad(sql, p); sql.append(" ORDER BY e.codigo_interno DESC");
        return jdbc.query(sql.toString(),p,(rs,n)->new ExpedienteOpcion(rs.getLong("id"),rs.getString("codigo_interno"),
                rs.getString("numero_expediente"),rs.getString("nombre_busqueda"),rs.getLong("responsable_id"),rs.getString("responsable")));
    }

    private List<TipoActuacionOpcion> tiposActuacion(){return jdbc.query("""
            SELECT id,codigo,nombre,categoria,genera_plazo,genera_tarea,genera_evento_agenda,requiere_documento
            FROM tipos_actuacion WHERE estudio_id=:estudioId AND activo=1 ORDER BY categoria,nombre
            """,Map.of("estudioId",SesionActual.estudioId()),(rs,n)->mapTipo(rs));}
    private List<UsuarioOpcion> usuarios(){return jdbc.query("""
            SELECT id,nombre_completo,cargo FROM usuarios WHERE estudio_id=:estudioId AND activo=1 AND estado='ACTIVO' AND tipo_usuario='INTERNO' ORDER BY nombre_completo
            """,Map.of("estudioId",SesionActual.estudioId()),(rs,n)->new UsuarioOpcion(rs.getLong("id"),rs.getString("nombre_completo"),rs.getString("cargo")));}
    private List<TipoPlazoOpcion> tiposPlazo(){return jdbc.query("""
            SELECT id,codigo,nombre,dias_por_defecto,tipo_computo_defecto,nivel_riesgo_defecto FROM tipos_plazo WHERE estudio_id=:estudioId AND activo=1 ORDER BY nombre
            """,Map.of("estudioId",SesionActual.estudioId()),(rs,n)->mapTipoPlazo(rs));}

    private List<DocumentoRelacionado> documentos(long id){return jdbc.query("""
            SELECT d.uuid_publico,d.nombre,d.categoria,d.estado,dv.nombre_archivo,dv.numero_version
            FROM documentos d LEFT JOIN documento_versiones dv ON dv.id=d.version_vigente_id
            WHERE d.actuacion_id=:id AND d.activo=1 AND d.en_papelera=0 ORDER BY d.fecha_creacion DESC
            """,Map.of("id",id),(rs,n)->new DocumentoRelacionado(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),getInt(rs,6)));}
    private List<TareaRelacionada> tareas(long id){return jdbc.query("""
            SELECT t.uuid_publico,t.titulo,t.estado,t.prioridad,t.fecha_limite,u.nombre_completo FROM tareas t JOIN usuarios u ON u.id=t.usuario_responsable_id
            WHERE t.actuacion_origen_id=:id AND t.activo=1 ORDER BY t.fecha_creacion DESC
            """,Map.of("id",id),(rs,n)->new TareaRelacionada(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toLocalDateTime(),rs.getString(6)));}
    private List<PlazoRelacionado> plazos(long id){return jdbc.query("""
            SELECT uuid_publico,descripcion,estado,nivel_riesgo,fecha_vencimiento,fecha_vencimiento_interna,requiere_revision_profesional
            FROM plazos WHERE actuacion_origen_id=:id AND activo=1 ORDER BY fecha_creacion DESC
            """,Map.of("id",id),(rs,n)->new PlazoRelacionado(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getDate(5).toLocalDate(),rs.getDate(6)==null?null:rs.getDate(6).toLocalDate(),rs.getBoolean(7)));}
    private List<EventoRelacionado> eventos(long id){return jdbc.query("""
            SELECT uuid_publico,tipo,titulo,fecha_inicio,fecha_fin,estado,modalidad FROM eventos_agenda WHERE actuacion_id=:id AND activo=1 ORDER BY fecha_inicio
            """,Map.of("id",id),(rs,n)->new EventoRelacionado(rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toLocalDateTime(),rs.getTimestamp(5).toLocalDateTime(),rs.getString(6),rs.getString(7)));}
    private List<AlertaRelacionada> alertas(long id){return jdbc.query("""
            SELECT id,tipo,mensaje,severidad,fecha_programada,estado FROM alertas WHERE actuacion_id=:id ORDER BY fecha_programada
            """,Map.of("id",id),(rs,n)->new AlertaRelacionada(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toLocalDateTime(),rs.getString(6)));}

    private ActuacionLista mapLista(ResultSet rs,int n)throws SQLException{return new ActuacionLista(rs.getString("uuid_publico"),rs.getTimestamp("fecha_hora").toLocalDateTime(),
            rs.getLong("tipo_id"),rs.getString("tipo_codigo"),rs.getString("tipo_nombre"),rs.getString("categoria"),rs.getLong("expediente_id"),
            rs.getString("expediente_codigo"),rs.getString("numero_expediente"),rs.getString("cliente_nombre"),rs.getString("asunto"),rs.getLong("responsable_id"),
            rs.getString("responsable"),rs.getString("estado"),rs.getString("resultado"),rs.getString("proxima_accion"),getInt(rs,"tiempo_minutos"),rs.getBoolean("es_facturable"),
            rs.getBigDecimal("costo"),rs.getString("moneda"),rs.getInt("documentos"),rs.getInt("tareas"),rs.getInt("plazos"),rs.getInt("eventos"));}
    private ActuacionLista mapListaConExtras(ResultSet rs,int n)throws SQLException{return mapLista(rs,n);}
    private TipoActuacionOpcion mapTipo(ResultSet rs)throws SQLException{return new TipoActuacionOpcion(rs.getLong("id"),rs.getString("codigo"),rs.getString("nombre"),rs.getString("categoria"),rs.getBoolean("genera_plazo"),rs.getBoolean("genera_tarea"),rs.getBoolean("genera_evento_agenda"),rs.getBoolean("requiere_documento"));}
    private TipoPlazoOpcion mapTipoPlazo(ResultSet rs)throws SQLException{return new TipoPlazoOpcion(rs.getLong("id"),rs.getString("codigo"),rs.getString("nombre"),getInt(rs,"dias_por_defecto"),rs.getString("tipo_computo_defecto"),rs.getString("nivel_riesgo_defecto"));}
    private Long insertar(String sql,MapSqlParameterSource p){KeyHolder k=new GeneratedKeyHolder();jdbc.update(sql,p,k,new String[]{"id"});Number n=k.getKey();if(n==null)throw new IllegalStateException("No se obtuvo id generado");return n.longValue();}
    private MapSqlParameterSource base(){return new MapSqlParameterSource().addValue("estudioId",SesionActual.estudioId());}
    /**
     * Acota a lo que el usuario puede ver: su estudio y, salvo Administrador o
     * Socio, los expedientes donde es responsable o esta en el equipo. Antes
     * solo filtraba el portal del cliente, asi que cualquiera del estudio veia
     * las actuaciones de un expediente confidencial ajeno (RF-02).
     */
    private void aplicarVisibilidad(StringBuilder sql, MapSqlParameterSource p) {
        sql.append(VisibilidadExpedientes.sql("e"));
        p.addValues(VisibilidadExpedientes.parametros());
    }
    private static Integer getInt(ResultSet rs,String c)throws SQLException{return rs.getObject(c)==null?null:rs.getInt(c);} private static Integer getInt(ResultSet rs,int c)throws SQLException{return rs.getObject(c)==null?null:rs.getInt(c);} private static boolean bool(Object o){return o instanceof Boolean b?b:o instanceof Number n&&n.intValue()!=0;}
}
