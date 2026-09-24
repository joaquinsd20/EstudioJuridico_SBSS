-- =============================================================================
-- LEXIA — V18: Gestion integral del modulo Plazos.
-- =============================================================================
-- El esquema original ya contenia el nucleo critico del plazo. Esta migracion
-- agrega el origen explicito para que cada plazo indique de donde nacio sin
-- inferirlo de relaciones opcionales.
-- =============================================================================
ALTER TABLE plazos
    ADD COLUMN origen VARCHAR(25) NOT NULL DEFAULT 'MANUAL' AFTER descripcion,
    ADD KEY ix_plazos_origen (origen),
    ADD CONSTRAINT ck_plazos_origen CHECK (origen IN
        ('MANUAL','RESOLUCION','NOTIFICACION','ACTUACION','MONITOR_JUDICIAL'));

-- Los plazos creados desde una actuacion antes de esta migracion tienen una
-- relacion inequívoca mediante actuacion_origen_id: se corrige su origen.
UPDATE plazos
   SET origen = 'ACTUACION'
 WHERE actuacion_origen_id IS NOT NULL
   AND resolucion_id IS NULL;

-- Si existe resolucion asociada, esa es la fuente mas precisa.
UPDATE plazos
   SET origen = 'RESOLUCION'
 WHERE resolucion_id IS NOT NULL;
