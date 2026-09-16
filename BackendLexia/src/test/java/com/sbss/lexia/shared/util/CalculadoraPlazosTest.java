package com.sbss.lexia.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sbss.lexia.shared.util.CalculadoraPlazos.RangoInhabil;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la aritmetica del computo, con fechas fijas y elegidas a mano.
 * No necesitan base de datos ni contexto de Spring: corren siempre, en
 * cualquier maquina del equipo.
 *
 * La comprobacion de que esta clase coincide con las funciones SQL de la V8
 * vive aparte, en CalculadoraPlazosParidadIT, porque esa si necesita MySQL.
 * Las dos se complementan: esta fija QUE debe pasar con casos legibles,
 * aquella comprueba que Java y SQL no se hayan separado.
 *
 * Calendario de referencia usado abajo (2026):
 *   lunes 27 de julio · martes 28 · miercoles 29 · jueves 30 · viernes 31
 *   28 y 29 de julio son Fiestas Patrias (feriado nacional).
 */
class CalculadoraPlazosTest {

    private static final LocalDate LUNES_27_JUL = LocalDate.of(2026, 7, 27);
    private static final LocalDate MARTES_28_JUL = LocalDate.of(2026, 7, 28);
    private static final LocalDate MIERCOLES_29_JUL = LocalDate.of(2026, 7, 29);
    private static final LocalDate JUEVES_30_JUL = LocalDate.of(2026, 7, 30);

    /** Fiestas Patrias: 28 y 29 de julio, nacional (ambito null). */
    private static final CalculadoraPlazos CON_FIESTAS_PATRIAS = new CalculadoraPlazos(List.of(
            new RangoInhabil(MARTES_28_JUL, MIERCOLES_29_JUL, null)));

    private static final CalculadoraPlazos SIN_FERIADOS = new CalculadoraPlazos(List.of());

    @Nested
    @DisplayName("esDiaHabil")
    class EsDiaHabil {

        @Test
        void sabadoYDomingoSonInhabiles() {
            assertThat(SIN_FERIADOS.esDiaHabil(LocalDate.of(2026, 7, 25), null)).isFalse(); // sabado
            assertThat(SIN_FERIADOS.esDiaHabil(LocalDate.of(2026, 7, 26), null)).isFalse(); // domingo
        }

        @Test
        void unLunesCorrienteEsHabil() {
            assertThat(SIN_FERIADOS.esDiaHabil(LUNES_27_JUL, null)).isTrue();
        }

        @Test
        void unFeriadoEntreSemanaEsInhabil() {
            assertThat(CON_FIESTAS_PATRIAS.esDiaHabil(MARTES_28_JUL, null)).isFalse();
            assertThat(CON_FIESTAS_PATRIAS.esDiaHabil(MIERCOLES_29_JUL, null)).isFalse();
        }

        @Test
        void elRangoEsInclusivoEnAmbosExtremos() {
            // 28 y 29 son los extremos del rango: los dos deben quedar dentro.
            assertThat(CON_FIESTAS_PATRIAS.esDiaHabil(MARTES_28_JUL, null)).isFalse();
            assertThat(CON_FIESTAS_PATRIAS.esDiaHabil(MIERCOLES_29_JUL, null)).isFalse();
            // El 30 ya esta fuera.
            assertThat(CON_FIESTAS_PATRIAS.esDiaHabil(JUEVES_30_JUL, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("ambito")
    class Ambito {

        private final CalculadoraPlazos conHuelgaRegional = new CalculadoraPlazos(List.of(
                new RangoInhabil(LUNES_27_JUL, LUNES_27_JUL, "La Libertad")));

        @Test
        void unaSuspensionRegionalNoAfectaAOtroDistrito() {
            assertThat(conHuelgaRegional.esDiaHabil(LUNES_27_JUL, "Lima")).isTrue();
        }

        @Test
        void unaSuspensionRegionalSiAfectaASuPropioDistrito() {
            assertThat(conHuelgaRegional.esDiaHabil(LUNES_27_JUL, "La Libertad")).isFalse();
        }

        @Test
        void consultarSinAmbitoIgnoraLoRegionalYSoloMiraLoNacional() {
            // Replica la semantica de NULL de SQL: con p_ambito NULL, la
            // comparacion `c.ambito = p_ambito` nunca es verdadera, asi que
            // solo aplican los rangos nacionales.
            assertThat(conHuelgaRegional.esDiaHabil(LUNES_27_JUL, null)).isTrue();
            assertThat(CON_FIESTAS_PATRIAS.esDiaHabil(MARTES_28_JUL, null)).isFalse();
        }

        @Test
        void elAmbitoNoDistingueMayusculas() {
            // La colacion de la tabla es utf8mb4_0900_ai_ci.
            assertThat(conHuelgaRegional.esDiaHabil(LUNES_27_JUL, "LA LIBERTAD")).isFalse();
        }
    }

    @Nested
    @DisplayName("siguienteDiaHabil")
    class SiguienteDiaHabil {

        @Test
        void desdeUnViernesSaltaAlLunes() {
            LocalDate viernes = LocalDate.of(2026, 7, 24);
            assertThat(SIN_FERIADOS.siguienteDiaHabil(viernes, null))
                    .isEqualTo(LocalDate.of(2026, 7, 27));
        }

        @Test
        void esEstrictamentePosteriorAunqueElDiaDadoSeaHabil() {
            assertThat(SIN_FERIADOS.siguienteDiaHabil(LUNES_27_JUL, null))
                    .isEqualTo(MARTES_28_JUL);
        }

        @Test
        void saltaElFeriado() {
            // Desde el lunes 27: el 28 y 29 son feriado, cae el jueves 30.
            assertThat(CON_FIESTAS_PATRIAS.siguienteDiaHabil(LUNES_27_JUL, null))
                    .isEqualTo(JUEVES_30_JUL);
        }
    }

    @Nested
    @DisplayName("calcularVencimiento")
    class CalcularVencimiento {

        @Test
        void calendarioNoAjustaADiaHabil() {
            // 3 dias calendario desde el viernes 24: 24, 25, 26 -> domingo 26.
            // Un plazo en dias calendario vence el dia que vence.
            LocalDate viernes = LocalDate.of(2026, 7, 24);
            assertThat(SIN_FERIADOS.calcularVencimiento(viernes, 3, "CALENDARIO", null))
                    .isEqualTo(LocalDate.of(2026, 7, 26));
        }

        @Test
        void calendarioCuentaElDiaDeInicioComoDia1() {
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 1, "CALENDARIO", null))
                    .isEqualTo(LUNES_27_JUL);
        }

        @Test
        void habilesCuentaElDiaDeInicioSiEsHabil() {
            // Lunes 27 habil = dia 1; martes 28 = dia 2 (sin feriados).
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 2, "HABILES", null))
                    .isEqualTo(MARTES_28_JUL);
        }

        @Test
        void habilesNoCuentaElDiaDeInicioSiEsInhabil() {
            // Empezando un sabado, el dia 1 es el lunes siguiente.
            LocalDate sabado = LocalDate.of(2026, 7, 25);
            assertThat(SIN_FERIADOS.calcularVencimiento(sabado, 1, "HABILES", null))
                    .isEqualTo(LUNES_27_JUL);
        }

        @Test
        @DisplayName("prueba obligatoria 1 de docs/05: habiles vence DESPUES que calendario")
        void unPlazoQueAtraviesaFiestasPatriasVenceDespuesEnHabiles() {
            LocalDate inicio = LUNES_27_JUL;

            LocalDate enCalendario =
                    CON_FIESTAS_PATRIAS.calcularVencimiento(inicio, 5, "CALENDARIO", null);
            LocalDate enHabiles =
                    CON_FIESTAS_PATRIAS.calcularVencimiento(inicio, 5, "HABILES", null);

            // Calendario: 27 + 4 = viernes 31 de julio.
            assertThat(enCalendario).isEqualTo(LocalDate.of(2026, 7, 31));
            // Habiles: 27(1), 28 y 29 feriado, 30(2), 31(3), 3 ago(4), 4 ago(5).
            assertThat(enHabiles).isEqualTo(LocalDate.of(2026, 8, 4));

            assertThat(enHabiles)
                    .as("un plazo en dias habiles que cruza un feriado debe vencer despues "
                            + "que el mismo plazo contado en dias calendario")
                    .isAfter(enCalendario);
        }

        @Test
        void diasNuloOMenorQueUnoDevuelveNullSinLanzar() {
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, null, "HABILES", null)).isNull();
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 0, "HABILES", null)).isNull();
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, -3, "HABILES", null)).isNull();
        }

        @Test
        void inicioNuloDevuelveNull() {
            assertThat(SIN_FERIADOS.calcularVencimiento(null, 5, "HABILES", null)).isNull();
        }

        @Test
        void cualquierTipoQueNoSeaCalendarioSeTrataComoHabiles() {
            LocalDate esperado = SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 3, "HABILES", null);
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 3, null, null))
                    .isEqualTo(esperado);
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 3, "cualquier-cosa", null))
                    .isEqualTo(esperado);
        }

        @Test
        void elTipoNoDistingueMayusculas() {
            assertThat(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 3, "calendario", null))
                    .isEqualTo(SIN_FERIADOS.calcularVencimiento(LUNES_27_JUL, 3, "CALENDARIO", null));
        }
    }

    @Nested
    @DisplayName("suspension PROPUESTA vs APROBADA")
    class SuspensionPropuesta {

        @Test
        @DisplayName("prueba obligatoria 2 de docs/05: PROPUESTO no cambia nada, APROBADO si")
        void soloLasEntradasAprobadasAlteranElComputo() {
            LocalDate inicio = LUNES_27_JUL;

            // Sin la suspension cargada (equivale a tenerla en PROPUESTO: quien
            // construye la calculadora solo le pasa las APROBADAS).
            CalculadoraPlazos sinSuspension = new CalculadoraPlazos(List.of());
            LocalDate antes = sinSuspension.calcularVencimiento(inicio, 3, "HABILES", null);

            // La misma suspension, ya aprobada.
            CalculadoraPlazos conSuspension = new CalculadoraPlazos(List.of(
                    new RangoInhabil(MARTES_28_JUL, MIERCOLES_29_JUL, null)));
            LocalDate despues = conSuspension.calcularVencimiento(inicio, 3, "HABILES", null);

            assertThat(antes).isEqualTo(MIERCOLES_29_JUL);       // 27, 28, 29
            assertThat(despues).isEqualTo(LocalDate.of(2026, 7, 31)); // 27, 30, 31
            assertThat(despues)
                    .as("aprobar la suspension debe correr el vencimiento hacia adelante")
                    .isAfter(antes);
        }
    }

    @Nested
    @DisplayName("diasRestantes")
    class DiasRestantes {

        @Test
        void unVencimientoFuturoDaPositivoYUnoPasadoNegativo() {
            LocalDate hoy = LocalDate.now(CalculadoraPlazos.ZONA);
            assertThat(CalculadoraPlazos.diasRestantes(hoy.plusDays(5))).isEqualTo(5);
            assertThat(CalculadoraPlazos.diasRestantes(hoy.minusDays(2))).isEqualTo(-2);
            assertThat(CalculadoraPlazos.diasRestantes(hoy)).isZero();
        }
    }
}
