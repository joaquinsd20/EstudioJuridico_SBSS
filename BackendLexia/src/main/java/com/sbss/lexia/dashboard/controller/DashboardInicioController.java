package com.sbss.lexia.dashboard.controller;

import com.sbss.lexia.actuaciones.controller.ActuacionController;
import com.sbss.lexia.agenda.controller.AgendaController;
import com.sbss.lexia.clientes.controller.ClienteController;
import com.sbss.lexia.documental.controller.DocumentoController;
import com.sbss.lexia.expedientes.controller.ExpedienteController;
import com.sbss.lexia.facturacion.controller.FacturacionController;
import com.sbss.lexia.monitor.controller.MonitorJudicialController;
import com.sbss.lexia.plazos.controller.PlazoController;
import com.sbss.lexia.tareas.controller.TareaController;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todo lo que pinta el Dashboard, en UNA sola peticion.
 *
 * POR QUE. Antes el panel hacia 14 peticiones al abrirse. Cada una repetia el
 * mismo trabajo fijo (validar el token, cargar el usuario y sus permisos de la
 * base, abrir transaccion, serializar) y en el servidor gratuito, con muy poca
 * CPU, hacian fila. Aqui ese trabajo se hace una vez.
 *
 * COMO. No se reescribe ninguna consulta: se llama a los MISMOS metodos de los
 * controladores de cada modulo. Al ser beans de Spring, cada llamada pasa por
 * su @PreAuthorize igual que si viniera por HTTP, y conserva sus reglas de
 * visibilidad (confidenciales, "solo lo mio", etc.). Si un rol no tiene permiso
 * para un modulo, esa parte sale con estado 403 y el resto se entrega igual.
 *
 * Cada parte trae {estado, datos}: 200 con datos, 403 sin permiso (el panel la
 * oculta) o 500 si fallo (el panel avisa que puede faltar informacion).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardInicioController {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final ExpedienteController expedientes;
    private final AgendaController agenda;
    private final PlazoController plazos;
    private final TareaController tareas;
    private final ClienteController clientes;
    private final ActuacionController actuaciones;
    private final DocumentoController documentos;
    private final MonitorJudicialController monitor;
    private final FacturacionController facturacion;
    private final DashboardController dashboard;

    public record Parte(int estado, Object datos) {
    }

    @GetMapping("/inicio")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Parte> inicio() {
        LocalDate hoy = LocalDate.now(LIMA);
        Map<String, Parte> r = new LinkedHashMap<>();

        r.put("expedientes", parte("expedientes", expedientes::resumen));
        r.put("agenda", parte("agenda", agenda::resumen));
        r.put("plazos", parte("plazos", plazos::resumen));
        r.put("tareas", parte("tareas", tareas::resumen));
        r.put("clientes", parte("clientes", () -> cuerpo(clientes.resumen())));
        r.put("actuaciones", parte("actuaciones", actuaciones::resumen));
        r.put("documentos", parte("documentos", documentos::estadisticas));
        r.put("monitor", parte("monitor", monitor::resumen));
        r.put("facturacion", parte("facturacion", facturacion::resumen));

        // Los mismos parametros que usaba el panel en el navegador.
        r.put("agendaHoy", parte("agendaHoy", () -> agenda.listar(
                hoy.atStartOfDay(), hoy.atTime(23, 59, 59), null, null, null, null, null, 0, 100)));
        r.put("tareasEquipo", parte("tareasEquipo", () -> tareas.listar(
                null, null, null, null, null, null, null, 0, 100)));
        r.put("expedientesAtencion", parte("expedientesAtencion", dashboard::expedientesAtencion));
        r.put("consultasMonitor", parte("consultasMonitor", () -> monitor.consultas(
                null, null, null, hoy.minusDays(30), null, 100, 0)));
        r.put("graficos", parte("graficos", dashboard::graficos));
        return r;
    }

    private static Parte parte(String nombre, Supplier<?> lectura) {
        try {
            return new Parte(200, lectura.get());
        } catch (AccessDeniedException e) {
            // Rol sin permiso para este modulo: no es un error, la tarjeta no se muestra.
            return new Parte(403, null);
        } catch (RuntimeException e) {
            log.warn("Dashboard: no se pudo leer '{}': {}", nombre, e.toString());
            return new Parte(500, null);
        }
    }

    private static Object cuerpo(ResponseEntity<?> respuesta) {
        return respuesta.getBody();
    }
}
