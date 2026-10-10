package com.sbss.lexia.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * El panel y los permisos de lo que agrega.
 *
 * Verificado contra el backend real el 2026-10-10, antes del arreglo: Juan
 * Perez (ABOGADO, sin FACTURACION_LEER, 403 en /facturacion/resumen) recibia en
 * /dashboard/graficos S/ 220.500 facturados y 12 cuentas pendientes: lo mismo
 * que el administrador. Y la carga y las tareas por persona contaban
 * expedientes que el usuario no puede abrir.
 *
 * La semilla trae 12 honorarios, asi que la comparacion entre roles prueba algo:
 * con la tabla vacia, "Juan recibe 0" pasaria aunque no hubiera filtro.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=DashboardIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class DashboardIT {

    /** ABOGADO: sin FACTURACION_LEER; solo ve EXP-0452 (responsable). */
    private static final String JUAN = "juan.perez@lexia.com";
    /** Responsable de EXP-0088 (confidencial), sin el permiso de confidenciales. */
    private static final String MARIA = "maria.lopez@lexia.com";
    private static final String ADMIN = "carlos.mendoza@lexia.com";
    /** CONTABILIDAD: SI tiene FACTURACION_LEER, aunque no vea expedientes. */
    private static final String ROBERTO = "roberto.diaz@lexia.com";
    /** RECEPCIONISTA: tiene DASHBOARD_LEER pero no EXPEDIENTE_LEER. */
    private static final String LUCIA = "lucia.castillo@lexia.com";
    /** CLIENTE (portal): EXPEDIENTE_LEER para lo suyo, sin DASHBOARD_LEER. */
    private static final String PORTAL = "anamaria.fsalas@correo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    private Authentication como(String email) {
        UsuarioPrincipal principal = usuarioDetailsService.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private ResultActions graficos(String usuario) throws Exception {
        return mockMvc.perform(get("/api/v1/dashboard/graficos").with(authentication(como(usuario))))
                .andExpect(status().isOk());
    }

    private ResultActions atencion(String usuario) throws Exception {
        return mockMvc.perform(get("/api/v1/dashboard/expedientes-atencion").with(authentication(como(usuario))));
    }

    @Test
    @DisplayName("sin FACTURACION_LEER el panel no trae dinero, y lo dice")
    void elDineroSoloConPermiso() throws Exception {
        graficos(JUAN)
                .andExpect(jsonPath("$.puedeVerFacturacion").value(false))
                .andExpect(jsonPath("$.cobranza.facturado").value(0))
                .andExpect(jsonPath("$.cobranza.cuentasPendientes").value(0))
                .andExpect(jsonPath("$.ingresosPorMes", hasSize(0)));

        // Positivos: el administrador y contabilidad si lo ven, con datos de verdad
        for (String usuario : List.of(ADMIN, ROBERTO)) {
            graficos(usuario)
                    .andExpect(jsonPath("$.puedeVerFacturacion").value(true))
                    .andExpect(jsonPath("$.cobranza.facturado").value(greaterThan(0.0)))
                    .andExpect(jsonPath("$.cobranza.cuentasPendientes").value(greaterThan(0)));
        }
    }

    @Test
    @DisplayName("la carga y las tareas por persona solo cuentan expedientes visibles")
    void cargaYTareasSiguenLaVisibilidad() throws Exception {
        // Maria es responsable de EXP-0088 (confidencial) y tiene ahi una tarea
        // abierta. Juan no lo ve, asi que Maria no debe aparecerle.
        graficos(ADMIN)
                .andExpect(jsonPath("$.cargaPorUsuario[*].nombre", hasItem(containsString("Maria Lopez"))))
                .andExpect(jsonPath("$.tareasPorUsuario[*].nombre", hasItem(containsString("Maria Lopez"))));
        graficos(JUAN)
                .andExpect(jsonPath("$.cargaPorUsuario[*].nombre", not(hasItem(containsString("Maria Lopez")))))
                .andExpect(jsonPath("$.tareasPorUsuario[*].nombre", not(hasItem(containsString("Maria Lopez")))))
                // control: lo suyo si aparece
                .andExpect(jsonPath("$.cargaPorUsuario[*].nombre", hasItem(containsString("Juan Perez"))));
    }

    @Test
    @DisplayName("expedientes que piden atencion: criticos y parados, abiertos y visibles")
    void expedientesQuePidenAtencion() throws Exception {
        // En la semilla los criticos abiertos son EXP-0033 y EXP-0142
        atencion(ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigoInterno", hasItem("EXP-0033")))
                .andExpect(jsonPath("$[*].codigoInterno", hasItem("EXP-0142")));

        // El confidencial pasa a critico: lo ven su responsable y el administrador, Juan no
        jdbc.update("UPDATE expedientes SET nivel_riesgo = 'CRITICO' WHERE codigo_interno = 'EXP-0088'");
        atencion(ADMIN).andExpect(jsonPath("$[*].codigoInterno", hasItem("EXP-0088")));
        atencion(MARIA).andExpect(jsonPath("$[*].codigoInterno", hasItem("EXP-0088")));
        atencion(JUAN).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigoInterno", not(hasItem("EXP-0088"))));

        // Uno parado mas de 30 dias entra aunque su riesgo sea medio
        jdbc.update("""
                UPDATE expedientes SET fecha_ultima_actuacion = NOW() - INTERVAL 40 DAY
                 WHERE codigo_interno = 'EXP-0452'
                """);
        atencion(JUAN).andExpect(jsonPath("$[*].codigoInterno", hasItem("EXP-0452")));

        // Uno en estado final no entra, aunque sea critico
        jdbc.update("UPDATE expedientes SET nivel_riesgo = 'CRITICO' WHERE codigo_interno = 'EXP-0052'");
        atencion(ADMIN).andExpect(jsonPath("$[*].codigoInterno", not(hasItem("EXP-0052"))));
    }

    @Test
    @DisplayName("la lista de atencion son expedientes: sin EXPEDIENTE_LEER responde 403")
    void atencionPideElPermisoDeExpedientes() throws Exception {
        // Lucia tiene el cambio de contrasena obligatorio a proposito, y
        // CambioPasswordObligatorioFilter le da 403 en TODO: asi la prueba pasaba
        // aunque el endpoint pidiera el permiso equivocado. Se le quita dentro de
        // la transaccion (vuelve sola al terminar).
        jdbc.update("UPDATE usuarios SET debe_cambiar_password = 0 WHERE email = ?", LUCIA);

        // Control: el panel si le responde, el 403 de abajo es por el permiso
        graficos(LUCIA);
        atencion(LUCIA).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el portal del cliente no recibe la lista interna, aunque el expediente sea suyo")
    void elPortalNoVeLaListaInterna() throws Exception {
        // Tiene EXPEDIENTE_LEER (para ver sus casos) y EXP-0142, que es suyo, es
        // critico: con solo ese permiso lo recibia aqui con riesgo y responsable.
        atencion(PORTAL).andExpect(status().isForbidden());
    }
}
