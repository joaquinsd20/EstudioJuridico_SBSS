-- =============================================================================
-- V30 — Recuperacion de contrasena ("¿Olvidaste tu contrasena?")
-- =============================================================================
-- Cada solicitud crea un enlace de un solo uso que vence a los 30 minutos.
-- Se guarda SOLO el hash SHA-256 del token: quien lea esta tabla (un backup,
-- un DBA) no puede usarla para entrar a ninguna cuenta.
-- =============================================================================

CREATE TABLE recuperaciones_password (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    usuario_id      BIGINT UNSIGNED NOT NULL,
    token_hash      CHAR(64)        NOT NULL,
    expira_en       DATETIME(3)     NOT NULL,
    usado_en        DATETIME(3)     NULL,
    ip_solicitud    VARCHAR(45)     NULL,
    creado_en       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recuperacion_token (token_hash),
    KEY idx_recuperacion_usuario_fecha (usuario_id, creado_en),
    CONSTRAINT fk_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
