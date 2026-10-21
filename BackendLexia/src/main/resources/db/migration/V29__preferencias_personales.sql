-- =============================================================================
-- LEXIA — V29: preferencias personales y aislamiento por usuario/estudio
-- =============================================================================
-- Las preferencias de presentación pertenecen al usuario autenticado. No se
-- guardan en la tabla `configuracion`, que representa parámetros institucionales.
-- Los valores están en texto para mantener las preferencias simples y acotadas.

CREATE TABLE preferencias_usuario (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    estudio_id          INT UNSIGNED NOT NULL,
    usuario_id          BIGINT UNSIGNED NOT NULL,
    clave               VARCHAR(40) NOT NULL,
    valor               VARCHAR(255) NOT NULL,
    fecha_modificacion  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_preferencias_usuario_clave (estudio_id, usuario_id, clave),
    KEY ix_preferencias_usuario (estudio_id, usuario_id),
    CONSTRAINT fk_preferencias_estudio FOREIGN KEY (estudio_id) REFERENCES estudios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_preferencias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_preferencias_clave CHECK (clave IN ('tema', 'sidebar_colapsado')),
    CONSTRAINT ck_preferencias_tema CHECK (clave <> 'tema' OR valor IN ('CLARO', 'OSCURO')),
    CONSTRAINT ck_preferencias_sidebar CHECK (clave <> 'sidebar_colapsado' OR valor IN ('true', 'false'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
