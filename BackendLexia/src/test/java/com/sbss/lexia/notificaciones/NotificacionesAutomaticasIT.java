package com.sbss.lexia.notificaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sbss.lexia.notificaciones.service.GeneradorNotificaciones;
import com.sbss.lexia.seguridad.security.UsuarioPrincipal;
import com.sbss.lexia.seguridad.service.UsuarioDetailsService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las notificaciones se GENERAN solas (RF-10).
 *
 * Verificado antes del arreglo (2026-10-17): la bandeja leia, marcaba y borraba,
 * pero nada la llenaba. Las alertas quedaban PROGRAMADA para siempre y
 * NotificacionRepositoryJdbc.crear() no tenia ningun llamador; ademas no existia
 * @EnableScheduling, asi que ningun @Scheduled corria.
 *
 * Cada caso negativo (no avisa, no duplica) lleva su positivo en la misma prueba.
 *
 *   LEXIA_TEST_DB=1 ./mvnw test -Dtest=NotificacionesAutomaticasIT
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "LEXIA_TEST_DB", matches = ".+")
class NotificacionesAutomaticasIT {

    private static final String JUAN = "juan.perez@lexia.com";
    private static final String LUIS = "luis.torres@lexia.com";
    private static final String ADMIN = "carlos.mendoza@lexia.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UsuarioDetailsService usuarios;
    @Autowired private GeneradorNotificaciones generador;
    @Autowired private ScheduledAnnotationBeanPostProcessor planificador;

    private Authentication como(String email) {
        UsuarioPrincipal p = usuarios.loadUserByUsername(email);
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    private long id(String email) {
        return jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
    }

    private long notificaciones(String email, String mensajeLike) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notificaciones WHERE usuario_id = ? AND mensaje LIKE ?",
                Long.class, id(email), mensajeLike);
    }

    /** Alerta de prueba con mensaje unico: la semilla trae otras PROGRAMADA que tambien se entregan. */
    private long alerta(String email, String mensaje, String desfase, String canal, Long plazoId) {
        jdbc.update("""
                INSERT INTO alertas (estudio_id, plazo_id, usuario_destino_id, tipo, dias_antes, mensaje,
                                     severidad, fecha_programada, canal, estado)
                VALUES (1, ?, ?, 'PLAZO_PROXIMO', 1, ?, 'ALTA', NOW(3) + INTERVAL %s, ?, 'PROGRAMADA')
                """.formatted(desfase), plazoId, id(email), mensaje, canal);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String estadoAlerta(long id) {
        return jdbc.queryForObject("SELECT estado FROM alertas WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("una alerta vencida se convierte en UNA notificacion del destinatario, y no se repite")
    void laAlertaVencidaSeEntregaUnaVez() throws Exception {
        long a = alerta(JUAN, "prueba-entrega-1", "-1 MINUTE", "SISTEMA", null);

        generador.entregarAlertasVencidas();

        assertEquals("ENVIADA", estadoAlerta(a));
        assertEquals(1, notificaciones(JUAN, "prueba-entrega-1"));
        // aparece en SU bandeja, con la forma de la campanita, y en la de nadie mas
        mockMvc.perform(get("/api/v1/notificaciones").with(authentication(como(JUAN))))
                .andExpect(jsonPath("$.items[?(@.mensaje == 'prueba-entrega-1')].tipo").value("PLAZO"))
                .andExpect(jsonPath("$.items[?(@.mensaje == 'prueba-entrega-1')].severidad").value("ADVERTENCIA"))
                .andExpect(jsonPath("$.items[?(@.mensaje == 'prueba-entrega-1')].leida").value(false));
        mockMvc.perform(get("/api/v1/notificaciones").with(authentication(como(LUIS))))
                .andExpect(jsonPath("$.items[?(@.mensaje == 'prueba-entrega-1')]").isEmpty());

        // Una segunda pasada no la duplica
        generador.entregarAlertasVencidas();
        assertEquals(1, notificaciones(JUAN, "prueba-entrega-1"));
    }

    @Test
    @DisplayName("lo que todavia no toca, o es de correo, no se entrega")
    void soloSeEntregaLoQueToca() {
        long futura = alerta(JUAN, "prueba-futura", "+1 DAY", "SISTEMA", null);
        long correo = alerta(JUAN, "prueba-correo", "-1 MINUTE", "EMAIL", null);
        long ahora = alerta(JUAN, "prueba-ahora", "-1 MINUTE", "AMBOS", null);

        generador.entregarAlertasVencidas();

        // control: la que si toca llego
        assertEquals(1, notificaciones(JUAN, "prueba-ahora"));
        assertEquals("ENVIADA", estadoAlerta(ahora));
        // la futura sigue esperando su hora
        assertEquals("PROGRAMADA", estadoAlerta(futura));
        assertEquals(0, notificaciones(JUAN, "prueba-futura"));
        // sin servicio de correo, marcarla ENVIADA seria mentir
        assertEquals("PROGRAMADA", estadoAlerta(correo));
        assertEquals(0, notificaciones(JUAN, "prueba-correo"));
    }

    @Test
    @DisplayName("el aviso de un plazo ya cumplido se cancela sin molestar a nadie")
    void unPlazoResueltoNoAvisa() {
        long plazo = jdbc.queryForObject("SELECT id FROM plazos ORDER BY id LIMIT 1", Long.class);
        long viva = alerta(JUAN, "prueba-plazo-vivo", "-1 MINUTE", "SISTEMA", plazo);
        generador.entregarAlertasVencidas();
        assertEquals(1, notificaciones(JUAN, "prueba-plazo-vivo"));   // control: con el plazo abierto, avisa
        assertEquals("ENVIADA", estadoAlerta(viva));

        jdbc.update("UPDATE plazos SET estado = 'CUMPLIDO' WHERE id = ?", plazo);
        long resuelta = alerta(JUAN, "prueba-plazo-cumplido", "-1 MINUTE", "SISTEMA", plazo);
        generador.entregarAlertasVencidas();

        assertEquals("CANCELADA", estadoAlerta(resuelta));
        assertEquals(0, notificaciones(JUAN, "prueba-plazo-cumplido"));
    }

    @Test
    @DisplayName("una resolucion nueva avisa al responsable y al equipo, no a quien la registro")
    void laResolucionNuevaAvisaAlEquipo() throws Exception {
        long exp = jdbc.queryForObject("SELECT id FROM expedientes WHERE codigo_interno = 'EXP-0452'", Long.class);
        long rosa = id("rosa.jimenez@lexia.com");   // equipo de EXP-0452
        long antesJuan = notificaciones(JUAN, "%Resolucion N.o 7777%");

        String cuerpo = """
                {"expedienteId":%d,"fuentePlataforma":"CEJ","huboMovimiento":true,"tipoMovimiento":"RESOLUCION",
                 "nuevaResolucion":true,"numeroResolucion":"Resolucion N.o 7777","tipoResolucion":"AUTO",
                 "fechaNotificacion":"2026-10-17","origenInformacion":"MANUAL","estadoVerificacion":"VERIFICADA"}"""
                .formatted(exp);
        mockMvc.perform(post("/api/v1/monitor-judicial/consultas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo).with(authentication(como(ADMIN))))
                .andExpect(status().is2xxSuccessful());

        assertEquals(antesJuan + 1, notificaciones(JUAN, "%Resolucion N.o 7777%"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notificaciones WHERE usuario_id = ? AND mensaje LIKE ?",
                Long.class, rosa, "%Resolucion N.o 7777%"));
        // quien la registro no se avisa a si mismo
        assertEquals(0, notificaciones(ADMIN, "%Resolucion N.o 7777%"));
        // alguien ajeno al expediente tampoco
        assertEquals(0, notificaciones(LUIS, "%Resolucion N.o 7777%"));

        // Sin nueva resolucion no hay aviso
        mockMvc.perform(post("/api/v1/monitor-judicial/consultas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.replace("\"nuevaResolucion\":true", "\"nuevaResolucion\":false")
                                .replace("Resolucion N.o 7777", "Resolucion N.o 8888"))
                        .with(authentication(como(ADMIN))))
                .andExpect(status().is2xxSuccessful());
        assertEquals(0, notificaciones(JUAN, "%Resolucion N.o 8888%"));
    }

    @Test
    @DisplayName("una tarea asignada avisa a su responsable; autoasignarse o editar sin cambiar de manos, no")
    void laTareaAsignadaAvisa() throws Exception {
        long exp = jdbc.queryForObject("SELECT id FROM expedientes WHERE codigo_interno = 'EXP-0452'", Long.class);
        String cuerpo = """
                {"titulo":"%s","expedienteId":%d,"responsableId":%d,"prioridad":"BAJA",
                 "fechaLimite":"2026-12-01T12:00:00","subtareas":[]}""";

        // El administrador se la asigna a Juan: aviso
        String respuesta = mockMvc.perform(post("/api/v1/tareas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted("tarea-notif-A", exp, id(JUAN))).with(authentication(como(ADMIN))))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        assertEquals(1, notificaciones(JUAN, "%tarea-notif-A%"));
        String uuid = respuesta.replaceAll(".*\"uuid\":\"([^\"]+)\".*", "$1");

        // El administrador se la asigna a si mismo: sin aviso
        mockMvc.perform(post("/api/v1/tareas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted("tarea-notif-B", exp, id(ADMIN))).with(authentication(como(ADMIN))))
                .andExpect(status().is2xxSuccessful());
        assertEquals(0, notificaciones(ADMIN, "%tarea-notif-B%"));

        // Editar sin cambiar el responsable: no repite el aviso
        mockMvc.perform(put("/api/v1/tareas/" + uuid).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted("tarea-notif-A", exp, id(JUAN))).with(authentication(como(ADMIN))))
                .andExpect(status().isOk());
        assertEquals(1, notificaciones(JUAN, "%tarea-notif-A%"));

        // Reasignar a Rosa: ella recibe el aviso
        mockMvc.perform(put("/api/v1/tareas/" + uuid).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.formatted("tarea-notif-A", exp, id("rosa.jimenez@lexia.com")))
                        .with(authentication(como(ADMIN))))
                .andExpect(status().isOk());
        assertEquals(1, notificaciones("rosa.jimenez@lexia.com", "%tarea-notif-A%"));
    }

    @Test
    @DisplayName("la bandeja sigue siendo de cada uno: no se lee ni se marca la de otro")
    void cadaQuienSuBandeja() throws Exception {
        alerta(JUAN, "prueba-aislamiento", "-1 MINUTE", "SISTEMA", null);
        generador.entregarAlertasVencidas();
        long idDeJuan = jdbc.queryForObject("SELECT id FROM notificaciones WHERE usuario_id = ? AND mensaje = ?",
                Long.class, id(JUAN), "prueba-aislamiento");

        mockMvc.perform(patch("/api/v1/notificaciones/" + idDeJuan + "/leer").with(authentication(como(LUIS))))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT leida FROM notificaciones WHERE id = ?", Integer.class, idDeJuan));
        // control: su dueno si puede
        mockMvc.perform(patch("/api/v1/notificaciones/" + idDeJuan + "/leer").with(authentication(como(JUAN))))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT leida FROM notificaciones WHERE id = ?", Integer.class, idDeJuan));
    }

    @Test
    @DisplayName("el planificador esta activo: antes @Scheduled no corria y nadie lo noto")
    void elPlanificadorCorre() {
        long tareas = planificador.getScheduledTasks().size();
        assertTrue(tareas >= 2, "Se esperaban la entrega de alertas y la limpieza de papelera; hay " + tareas);
        assertTrue(planificador.getScheduledTasks().stream().anyMatch(t -> t.toString().contains("EntregaAlertasScheduler")),
                "Falta la entrega de alertas en el planificador");
        assertTrue(Matchers.notNullValue().matches(planificador));
    }
}
