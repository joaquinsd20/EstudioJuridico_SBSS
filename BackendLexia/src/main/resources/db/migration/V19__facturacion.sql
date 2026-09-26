-- ============================================================
-- LEXIA
-- MÓDULO: FACTURACIÓN Y HONORARIOS
-- ============================================================

-- Sin USE: la base la elige la conexion (DB_NAME / LEXIA_DB_NAME). Un USE fijo
-- aqui escribiria en lexia_db aunque Flyway apunte a otra base.

-- ============================================================
-- 1. TABLA DE HONORARIOS
-- ============================================================

CREATE TABLE honorarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    cliente_id BIGINT UNSIGNED NOT NULL,
    expediente_id BIGINT UNSIGNED NOT NULL,
    modalidad_cobro VARCHAR(100) NOT NULL,
    estado_pago VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE',
    monto_pactado DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    monto_pagado DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    saldo_pendiente DECIMAL(10,2)
        GENERATED ALWAYS AS (
            monto_pactado - monto_pagado
        ) STORED,

    gastos DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_vencimiento DATE NULL,

    -- RELACIONES
    CONSTRAINT fk_honorarios_cliente
        FOREIGN KEY (cliente_id)
        REFERENCES clientes(id),

    CONSTRAINT fk_honorarios_expediente
        FOREIGN KEY (expediente_id)
        REFERENCES expedientes(id),

    -- ÍNDICES
    INDEX idx_honorarios_estado_pago (estado_pago),

    INDEX idx_honorarios_fecha_vencimiento (fecha_vencimiento)

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
-- utf8mb4_0900_ai_ci, la del resto del esquema. Con utf8mb4_unicode_ci el
-- check "Una sola collation" de scripts/verificar-bd.sh falla, y cualquier
-- comparacion futura entre honorarios.uuid y un CHAR(36) de otra tabla daria
-- "Illegal mix of collations".
COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 2. TABLA DE MOVIMIENTOS FINANCIEROS
-- ============================================================

CREATE TABLE movimientos_financieros (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cliente_id BIGINT UNSIGNED NOT NULL,
    expediente_id BIGINT UNSIGNED NULL,
    honorario_uuid VARCHAR(36) NULL,
    tipo_operacion VARCHAR(100) NOT NULL,
    modalidad VARCHAR(100) NULL,
    monto DECIMAL(10,2) NOT NULL,
    fecha DATE NOT NULL,
    etapa_procesal VARCHAR(100) NULL,
    concepto VARCHAR(200) NULL,
    observaciones TEXT NULL,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- RELACIONES
    CONSTRAINT fk_movimientos_cliente
        FOREIGN KEY (cliente_id)
        REFERENCES clientes(id),

    CONSTRAINT fk_movimientos_expediente
        FOREIGN KEY (expediente_id)
        REFERENCES expedientes(id),

    CONSTRAINT fk_movimientos_honorario
        FOREIGN KEY (honorario_uuid)
        REFERENCES honorarios(uuid),

    -- ÍNDICES
    INDEX idx_movimientos_tipo_operacion (tipo_operacion),
    INDEX idx_movimientos_fecha (fecha)

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
-- utf8mb4_0900_ai_ci, la del resto del esquema. Con utf8mb4_unicode_ci el
-- check "Una sola collation" de scripts/verificar-bd.sh falla, y cualquier
-- comparacion futura entre honorarios.uuid y un CHAR(36) de otra tabla daria
-- "Illegal mix of collations".
COLLATE=utf8mb4_0900_ai_ci;

