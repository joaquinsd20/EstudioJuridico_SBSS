-- =============================================================================
-- LEXIA — V13: fecha del ultimo cambio de contrasena
-- =============================================================================
-- El token JWT es sin estado y dura 8 horas. Sin esta columna, cambiar la
-- contrasena NO cerraria las sesiones abiertas con la anterior: si alguien
-- robo un token (o dejo la sesion abierta en otra maquina), seguiria dentro
-- hasta que expirara, aunque el dueño ya hubiera cambiado la clave justamente
-- por eso.
--
-- Esta columna es la "version" de la contrasena. Cada token lleva la version
-- con la que se emitio (claim `pv`), y JwtAuthenticationFilter —que ya recarga
-- el usuario en cada peticion, asi que no cuesta ninguna consulta extra— exige
-- que coincida con la de aqui. Al cambiar la contrasena la version avanza y
-- todo token anterior deja de valer; el que se entrega en ese momento ya lleva
-- la nueva. Se guarda truncada al segundo (ver AuthService.cambiarPassword).
--
-- NULL = la contrasena nunca se ha cambiado desde el alta (version 0).
-- =============================================================================

ALTER TABLE usuarios
    ADD COLUMN fecha_cambio_password DATETIME(3) NULL
        COMMENT 'Version de la contrasena: los tokens con otra version dejan de valer'
        AFTER debe_cambiar_password;

-- =============================================================================
-- VERIFICACION (esperado: 1)
-- =============================================================================
SELECT COUNT(*) AS columna_esperado_1
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios'
   AND COLUMN_NAME = 'fecha_cambio_password';
