-- =============================================================================
-- LEXIA — V8: Funciones de computo de plazos
-- =============================================================================
-- Estas funciones son la ESPECIFICACION EJECUTABLE de la regla de dias habiles.
-- La implementacion real vive en Java (CalculadoraPlazos), pero tener la regla
-- tambien en SQL permite:
--   1. Que la semilla de desarrollo genere plazos correctos por construccion.
--   2. Contrastar el resultado de Java contra el de la base en las pruebas.
--      Si difieren, uno de los dos esta mal y el test lo detecta.
--
-- REGLA: solo cuentan las filas de calendario_judicial con estado = 'APROBADO'.
-- Una suspension PROPUESTA y no aprobada por un Socio no altera ningun calculo.
--
-- NOTA DE DESPLIEGUE: si MySQL responde ERROR 1418 al crear estas funciones,
-- el servidor tiene el log binario activo y exige declarar el determinismo.
-- Solucion (una sola vez, como administrador):
--     SET GLOBAL log_bin_trust_function_creators = 1;
-- =============================================================================

DELIMITER $$

-- -----------------------------------------------------------------------------
-- fn_es_dia_habil — ¿la fecha es habil para el ambito indicado?
-- p_ambito NULL = solo se consideran los feriados nacionales.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_es_dia_habil$$
CREATE FUNCTION fn_es_dia_habil(p_fecha DATE, p_ambito VARCHAR(80))
RETURNS TINYINT(1)
READS SQL DATA
BEGIN
    -- DAYOFWEEK: 1 = domingo, 7 = sabado
    IF DAYOFWEEK(p_fecha) IN (1, 7) THEN
        RETURN 0;
    END IF;

    IF EXISTS (
        SELECT 1 FROM calendario_judicial c
         WHERE c.estado = 'APROBADO'
           AND p_fecha BETWEEN c.fecha_inicio AND c.fecha_fin
           AND (c.ambito IS NULL OR c.ambito = p_ambito)
    ) THEN
        RETURN 0;
    END IF;

    RETURN 1;
END$$

-- -----------------------------------------------------------------------------
-- fn_siguiente_dia_habil — primer dia habil ESTRICTAMENTE posterior a la fecha.
-- Es lo que se usa para obtener fecha_inicio_computo desde fecha_notificacion.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_siguiente_dia_habil$$
CREATE FUNCTION fn_siguiente_dia_habil(p_fecha DATE, p_ambito VARCHAR(80))
RETURNS DATE
READS SQL DATA
BEGIN
    DECLARE v_fecha DATE DEFAULT DATE_ADD(p_fecha, INTERVAL 1 DAY);
    DECLARE v_guarda INT DEFAULT 0;

    WHILE fn_es_dia_habil(v_fecha, p_ambito) = 0 AND v_guarda < 400 DO
        SET v_fecha = DATE_ADD(v_fecha, INTERVAL 1 DAY);
        SET v_guarda = v_guarda + 1;
    END WHILE;

    RETURN v_fecha;
END$$

-- -----------------------------------------------------------------------------
-- fn_calcular_vencimiento — la funcion central del sistema.
--
-- p_tipo = 'CALENDARIO': el dia de inicio cuenta como dia 1.
--          vencimiento = inicio + (dias - 1)
-- p_tipo = 'HABILES'   : el dia de inicio cuenta como dia 1 si es habil.
--          Se avanza dia habil por dia habil hasta completar p_dias.
--
-- El tope de 2000 iteraciones evita un bucle infinito si alguien carga una
-- suspension de plazos sin fecha de fin razonable.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_calcular_vencimiento$$
CREATE FUNCTION fn_calcular_vencimiento(
    p_inicio DATE,
    p_dias   SMALLINT UNSIGNED,
    p_tipo   VARCHAR(15),
    p_ambito VARCHAR(80)
)
RETURNS DATE
READS SQL DATA
BEGIN
    DECLARE v_fecha     DATE DEFAULT p_inicio;
    DECLARE v_contados  INT  DEFAULT 0;
    DECLARE v_guarda    INT  DEFAULT 0;

    IF p_dias IS NULL OR p_dias < 1 THEN
        RETURN NULL;
    END IF;

    IF p_tipo = 'CALENDARIO' THEN
        RETURN DATE_ADD(p_inicio, INTERVAL (p_dias - 1) DAY);
    END IF;

    -- HABILES
    IF fn_es_dia_habil(v_fecha, p_ambito) = 1 THEN
        SET v_contados = 1;
    END IF;

    WHILE v_contados < p_dias AND v_guarda < 2000 DO
        SET v_fecha = DATE_ADD(v_fecha, INTERVAL 1 DAY);
        SET v_guarda = v_guarda + 1;
        IF fn_es_dia_habil(v_fecha, p_ambito) = 1 THEN
            SET v_contados = v_contados + 1;
        END IF;
    END WHILE;

    RETURN v_fecha;
END$$

-- -----------------------------------------------------------------------------
-- fn_dias_restantes — lo que muestra la columna "Dias Restantes".
-- NO es una columna almacenada: se calcula siempre.
-- Negativo = plazo vencido.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_dias_restantes$$
CREATE FUNCTION fn_dias_restantes(p_vencimiento DATE)
RETURNS INT
DETERMINISTIC
BEGIN
    RETURN DATEDIFF(p_vencimiento, CURRENT_DATE);
END$$

DELIMITER ;
