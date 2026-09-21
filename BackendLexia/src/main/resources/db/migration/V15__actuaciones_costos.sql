-- =============================================================================
-- LEXIA — V15: completar trazabilidad del modulo Actuaciones
-- El requisito funcional pide tiempo/costos y que una actuacion pueda generar
-- alertas. La FK en alertas permite conservar ese origen de forma auditable.
-- =============================================================================
ALTER TABLE actuaciones
    ADD COLUMN costo DECIMAL(18,2) NULL AFTER es_facturable,
    ADD COLUMN moneda CHAR(3) NOT NULL DEFAULT 'PEN' AFTER costo,
    ADD CONSTRAINT ck_actuaciones_costo CHECK (costo IS NULL OR costo >= 0);

ALTER TABLE alertas
    ADD COLUMN actuacion_id BIGINT UNSIGNED NULL AFTER plazo_id,
    ADD KEY ix_alertas_actuacion (actuacion_id),
    ADD CONSTRAINT fk_alertas_actuacion
        FOREIGN KEY (actuacion_id) REFERENCES actuaciones (id)
        ON DELETE CASCADE ON UPDATE CASCADE;

-- El requisito funcional incluye INFORMES como tipo de actuacion. Se agrega a
-- todos los estudios existentes sin duplicarlo si un estudio ya lo configuro.
INSERT INTO tipos_actuacion (
    estudio_id, codigo, nombre, categoria,
    genera_plazo, genera_tarea, genera_evento_agenda, requiere_documento
)
SELECT e.id, 'INFORME', 'Informe', 'ESCRITO', 0, 0, 0, 1
  FROM estudios e
 WHERE e.activo = 1
   AND NOT EXISTS (
       SELECT 1
         FROM tipos_actuacion ta
        WHERE ta.estudio_id = e.id
          AND ta.codigo = 'INFORME'
   );
