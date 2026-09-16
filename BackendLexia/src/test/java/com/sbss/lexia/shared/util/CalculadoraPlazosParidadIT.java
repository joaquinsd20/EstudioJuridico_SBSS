package com.sbss.lexia.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sbss.lexia.catalogos.service.CalendarioJudicialService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * LA PRUEBA QUE PROTEGE AL RESTO (prueba obligatoria 5 de docs/05 §5).
 *
 * Las funciones SQL de la migracion V8 son la especificacion ejecutable del
 * computo de plazos. CalculadoraPlazos es la implementacion que usa la
 * aplicacion. Si las dos se separan, un plazo se calcula distinto segun quien
 * lo mire — la semilla por un lado, la pantalla por el otro — y nadie se
 * entera hasta que un plazo vence mal. Esta prueba las contrasta.
 *
 * Necesita el MySQL local con la semilla cargada: se salta si LEXIA_TEST_DB no
 * esta definida, para no exigirle Docker ni Testcontainers a nadie del equipo.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=CalculadoraPlazosParidadIT
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class CalculadoraPlazosParidadIT {

    @Autowired
    private EntityManager em;

    @Autowired
    private CalendarioJudicialService calendarioJudicialService;

    private CalculadoraPlazos calculadora;

    @BeforeAll
    void cargarCalendario() {
        calculadora = calendarioJudicialService.calculadora();
    }

    @Test
    @DisplayName("los plazos de la semilla dan el mismo vencimiento en Java y en SQL")
    void losPlazosDeLaSemillaCoincidenConLaFuncionSql() {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery("""
                SELECT id,
                       fecha_inicio_computo,
                       dias_plazo,
                       tipo_computo,
                       fn_calcular_vencimiento(fecha_inicio_computo, dias_plazo, tipo_computo, NULL)
                  FROM plazos
                 ORDER BY id
                """).getResultList();

        assertThat(filas)
                .as("la semilla debe traer plazos para comparar; si esto falla, "
                        + "la base no tiene R__seed_desarrollo.sql aplicada")
                .isNotEmpty();

        List<String> divergencias = new ArrayList<>();

        for (Object[] fila : filas) {
            long id = ((Number) fila[0]).longValue();
            LocalDate inicio = aLocalDate(fila[1]);
            int dias = ((Number) fila[2]).intValue();
            String tipo = (String) fila[3];
            LocalDate enSql = aLocalDate(fila[4]);

            LocalDate enJava = calculadora.calcularVencimiento(inicio, dias, tipo, null);

            if (!java.util.Objects.equals(enSql, enJava)) {
                divergencias.add("plazo %d (%s, %d dias %s): SQL=%s Java=%s"
                        .formatted(id, inicio, dias, tipo, enSql, enJava));
            }
        }

        assertThat(divergencias)
                .as("CalculadoraPlazos y fn_calcular_vencimiento deben dar exactamente lo mismo")
                .isEmpty();
    }

    @Test
    @DisplayName("esDiaHabil coincide dia por dia en una ventana de 400 dias")
    void esDiaHabilCoincideConLaFuncionSql() {
        LocalDate desde = LocalDate.now(CalculadoraPlazos.ZONA).minusDays(50);
        LocalDate hasta = desde.plusDays(400);

        // Una sola consulta en vez de 400: el CTE recursivo genera la serie de
        // fechas y devuelve las que SQL considera inhabiles. El limite por
        // defecto de cte_max_recursion_depth es 1000, asi que 400 entra de
        // sobra.
        @SuppressWarnings("unchecked")
        List<Object> inhabilesSql = em.createNativeQuery("""
                WITH RECURSIVE dias AS (
                    SELECT :desde AS d
                    UNION ALL
                    SELECT d + INTERVAL 1 DAY FROM dias WHERE d < :hasta
                )
                SELECT d FROM dias WHERE fn_es_dia_habil(d, NULL) = 0
                """)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();

        List<LocalDate> enSql = inhabilesSql.stream()
                .map(CalculadoraPlazosParidadIT::aLocalDate)
                .sorted()
                .toList();

        List<LocalDate> enJava = desde.datesUntil(hasta.plusDays(1))
                .filter(fecha -> !calculadora.esDiaHabil(fecha, null))
                .toList();

        assertThat(enJava)
                .as("los dias inhabiles que ve Java deben ser los mismos que ve fn_es_dia_habil")
                .containsExactlyElementsOf(enSql);
    }

    @Test
    @DisplayName("siguienteDiaHabil coincide para cada dia de un mes completo")
    void siguienteDiaHabilCoincideConLaFuncionSql() {
        LocalDate desde = LocalDate.now(CalculadoraPlazos.ZONA).minusDays(15);
        List<String> divergencias = new ArrayList<>();

        for (LocalDate fecha = desde; fecha.isBefore(desde.plusDays(45)); fecha = fecha.plusDays(1)) {
            LocalDate enSql = aLocalDate(em
                    .createNativeQuery("SELECT fn_siguiente_dia_habil(:f, NULL)")
                    .setParameter("f", fecha)
                    .getSingleResult());

            LocalDate enJava = calculadora.siguienteDiaHabil(fecha, null);

            if (!java.util.Objects.equals(enSql, enJava)) {
                divergencias.add("desde %s: SQL=%s Java=%s".formatted(fecha, enSql, enJava));
            }
        }

        assertThat(divergencias)
                .as("siguienteDiaHabil debe coincidir con fn_siguiente_dia_habil")
                .isEmpty();
    }

    /**
     * El driver devuelve las columnas DATE como java.sql.Date o como LocalDate
     * segun la version; se aceptan las dos para que la prueba no dependa de ese
     * detalle.
     */
    private static LocalDate aLocalDate(Object valor) {
        return switch (valor) {
            case null -> null;
            case LocalDate fecha -> fecha;
            case java.sql.Date fecha -> fecha.toLocalDate();
            default -> LocalDate.parse(valor.toString());
        };
    }
}
