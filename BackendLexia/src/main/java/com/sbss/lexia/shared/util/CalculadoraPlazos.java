package com.sbss.lexia.shared.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Computo de plazos procesales. La clase mas critica del sistema: si se
 * equivoca, un plazo vence antes de lo que el abogado cree.
 *
 * ES UNA TRADUCCION LITERAL de las funciones SQL de la migracion V8
 * (fn_es_dia_habil, fn_siguiente_dia_habil, fn_calcular_vencimiento,
 * fn_dias_restantes). Esas funciones son la ESPECIFICACION EJECUTABLE de la
 * regla, y CalculadoraPlazosParidadIT comprueba que ambas den exactamente lo
 * mismo. Por eso varias decisiones de aqui abajo parecen raras vistas en
 * aislamiento: no son criterio propio, son fidelidad al SQL. Cada una lleva
 * su comentario.
 *
 * NO ES UN BEAN DE SPRING NI CONOCE JPA, a proposito: recibe los rangos
 * inhabiles ya cargados y hace aritmetica pura. Eso permite probarla entera
 * sin base de datos (ver CalculadoraPlazosTest) y evita el error de consultar
 * la base una vez por dia dentro del bucle — que es justo lo que hace la
 * version SQL, y por lo que existe el indice ix_calendario_vigente.
 * Quien la construye desde la base es CalendarioJudicialService.
 */
public final class CalculadoraPlazos {

    /** Tope de fn_siguiente_dia_habil. Al agotarse devuelve la ultima fecha alcanzada. */
    private static final int TOPE_SIGUIENTE_HABIL = 400;

    /** Tope de fn_calcular_vencimiento. Protege de una suspension sin fecha de fin razonable. */
    private static final int TOPE_VENCIMIENTO = 2000;

    /** RNF-11: el estudio opera en Lima y los plazos judiciales son fechas locales. */
    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final List<RangoInhabil> rangos;

    /**
     * @param rangosAprobados SOLO las filas de calendario_judicial con
     *                        estado = 'APROBADO'. Filtrar por estado es
     *                        responsabilidad de quien construye: una
     *                        suspension PROPUESTA no altera ningun calculo
     *                        hasta que un Socio la apruebe, y esta clase no
     *                        tiene forma de comprobarlo.
     */
    public CalculadoraPlazos(List<RangoInhabil> rangosAprobados) {
        this.rangos = List.copyOf(rangosAprobados);
    }

    /**
     * Un dia inhabil del calendario judicial: feriado, dia no laborable,
     * suspension de plazos o huelga.
     *
     * @param ambito NULL = nacional, aplica a todos los distritos judiciales.
     *               Con valor, solo aplica a ese distrito.
     */
    public record RangoInhabil(LocalDate inicio, LocalDate fin, String ambito) {

        /** BETWEEN de SQL: inclusivo en AMBOS extremos. */
        boolean cubre(LocalDate fecha, String ambitoConsultado) {
            if (fecha.isBefore(inicio) || fecha.isAfter(fin)) {
                return false;
            }
            // Replica `(c.ambito IS NULL OR c.ambito = p_ambito)`.
            //
            // Ojo con la semantica de NULL de SQL: cuando p_ambito es NULL,
            // `c.ambito = NULL` nunca es verdadero, asi que solo aplican los
            // rangos nacionales. Con equals() de Java sale igual — pero solo
            // si se respeta el orden: primero el IS NULL.
            if (ambito == null) {
                return true;
            }
            // equalsIgnoreCase y no equals porque la colacion de la tabla es
            // utf8mb4_0900_ai_ci, insensible a mayusculas. Lo que NO se
            // replica es la insensibilidad a TILDES de esa colacion: en SQL
            // 'Ancash' y 'Áncash' son el mismo ambito, aqui no. Mientras los
            // ambitos se guarden normalizados no importa; si algun dia se
            // cargan a mano desde una pantalla, hay que normalizarlos al
            // escribir.
            return ambito.equalsIgnoreCase(ambitoConsultado);
        }
    }

    /**
     * Traduccion de fn_es_dia_habil.
     *
     * Inhabil = sabado, domingo, o cubierto por un rango aprobado del
     * calendario judicial.
     */
    public boolean esDiaHabil(LocalDate fecha, String ambito) {
        DayOfWeek dia = fecha.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            return false;
        }
        return rangos.stream().noneMatch(rango -> rango.cubre(fecha, ambito));
    }

    /**
     * Traduccion de fn_siguiente_dia_habil: primer dia habil ESTRICTAMENTE
     * posterior a la fecha dada. Es lo que convierte fecha_notificacion en
     * fecha_inicio_computo.
     *
     * Si se agota el tope de 400 devuelve la ultima fecha alcanzada aunque sea
     * inhabil, igual que el SQL. Parece un descuido pero es deliberado: con
     * mas de 400 dias inhabiles seguidos el calendario esta mal cargado, y
     * devolver una fecha visiblemente absurda es preferible a lanzar una
     * excepcion que tumbe el registro de la actuacion.
     */
    public LocalDate siguienteDiaHabil(LocalDate fecha, String ambito) {
        if (fecha == null) {
            return null;
        }
        LocalDate actual = fecha.plusDays(1);
        int guarda = 0;
        while (!esDiaHabil(actual, ambito) && guarda < TOPE_SIGUIENTE_HABIL) {
            actual = actual.plusDays(1);
            guarda++;
        }
        return actual;
    }

    /**
     * Traduccion de fn_calcular_vencimiento. La funcion central del sistema.
     *
     * CALENDARIO: el dia de inicio cuenta como dia 1 y el vencimiento es
     * inicio + (dias - 1) dias naturales. NO se ajusta a dia habil: puede caer
     * sabado o domingo, y asi debe ser — un plazo en dias calendario vence el
     * dia que vence.
     *
     * HABILES: el dia de inicio cuenta como dia 1 SOLO SI es habil; si no,
     * el primer habil que aparezca sera el dia 1. Luego se avanza dia por dia
     * contando solo los habiles.
     *
     * @param dias nulo o menor que 1 devuelve null, no lanza excepcion —
     *             fidelidad al SQL, que hace `IF p_dias IS NULL OR p_dias < 1
     *             THEN RETURN NULL`.
     * @param tipoComputo cualquier valor que no sea CALENDARIO se trata como
     *             HABILES, incluido null. Tambien es del SQL: el IF compara
     *             contra 'CALENDARIO' y todo lo demas cae en la rama de
     *             habiles. El CHECK ck_plazos_computo de la tabla ya garantiza
     *             que en la practica solo llegue HABILES o CALENDARIO.
     */
    public LocalDate calcularVencimiento(
            LocalDate inicio, Integer dias, String tipoComputo, String ambito) {

        if (inicio == null || dias == null || dias < 1) {
            return null;
        }

        if ("CALENDARIO".equalsIgnoreCase(tipoComputo)) {
            return inicio.plusDays(dias - 1L);
        }

        LocalDate actual = inicio;
        int contados = esDiaHabil(actual, ambito) ? 1 : 0;
        int guarda = 0;

        while (contados < dias && guarda < TOPE_VENCIMIENTO) {
            actual = actual.plusDays(1);
            guarda++;
            if (esDiaHabil(actual, ambito)) {
                contados++;
            }
        }
        return actual;
    }

    /**
     * Traduccion de fn_dias_restantes: DATEDIFF(vencimiento, CURRENT_DATE).
     * Negativo = plazo vencido. NO se almacena en ninguna columna, se calcula
     * siempre (regla 6 de CLAUDE.md).
     *
     * Usa la zona de Lima explicitamente y no la del sistema: en un servidor
     * en UTC, "hoy" puede ser el dia siguiente durante las ultimas cinco horas
     * de cada jornada peruana, y eso restaria un dia a TODOS los plazos del
     * estudio en el peor momento posible — al final de la tarde.
     */
    public static int diasRestantes(LocalDate vencimiento) {
        return (int) ChronoUnit.DAYS.between(LocalDate.now(ZONA), vencimiento);
    }
}
