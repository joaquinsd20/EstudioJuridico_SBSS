-- =============================================================================
-- LEXIA — V11: historial real de responsables y equipo por expediente
-- =============================================================================
-- expediente_equipo conserva el estado actual (PK expediente_id + usuario_id).
-- Por ello un cambio de rol puede sobrescribir el valor anterior. Desde esta
-- version, la trazabilidad se registra de forma append-only en auditoria.
-- Este backfill crea los eventos que todavía pueden reconstruirse de los datos
-- existentes. Los cambios futuros son escritos por ExpedienteRepositoryJdbc.
-- =============================================================================

INSERT INTO auditoria
    (estudio_id, usuario_id, entidad, registro_id, expediente_id, accion, descripcion, exito, fecha)
SELECT e.estudio_id,
       eq.usuario_asignacion_id,
       CASE WHEN eq.rol_en_expediente = 'RESPONSABLE'
            THEN 'expediente_responsable' ELSE 'expediente_equipo' END,
       eq.expediente_id,
       eq.expediente_id,
       'CREAR',
       CASE WHEN eq.rol_en_expediente = 'RESPONSABLE'
            THEN CONCAT(u.nombre_completo, ' fue asignado como responsable del expediente.')
            ELSE CONCAT(u.nombre_completo, ' fue añadido al equipo de apoyo.') END,
       1,
       eq.fecha_asignacion
  FROM expediente_equipo eq
  JOIN expedientes e ON e.id = eq.expediente_id
  JOIN usuarios u ON u.id = eq.usuario_id
 WHERE eq.fecha_asignacion IS NOT NULL;

INSERT INTO auditoria
    (estudio_id, usuario_id, entidad, registro_id, expediente_id, accion, descripcion, exito, fecha)
SELECT e.estudio_id,
       eq.usuario_asignacion_id,
       CASE WHEN eq.rol_en_expediente = 'RESPONSABLE'
            THEN 'expediente_responsable' ELSE 'expediente_equipo' END,
       eq.expediente_id,
       eq.expediente_id,
       'ELIMINAR',
       CASE WHEN eq.rol_en_expediente = 'RESPONSABLE'
            THEN CONCAT(u.nombre_completo, ' dejó de estar asignado como responsable del expediente.')
            ELSE CONCAT(u.nombre_completo, ' fue retirado del equipo de apoyo.') END,
       1,
       eq.fecha_desasignacion
  FROM expediente_equipo eq
  JOIN expedientes e ON e.id = eq.expediente_id
  JOIN usuarios u ON u.id = eq.usuario_id
 WHERE eq.fecha_desasignacion IS NOT NULL;
