package com.sbss.lexia.dashboard.controller;

import com.sbss.lexia.dashboard.dto.DashboardDtos.ExpedienteAtencion;
import com.sbss.lexia.dashboard.dto.DashboardDtos.Graficos;
import com.sbss.lexia.dashboard.service.DashboardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos de los gráficos del panel.
 *
 * Se separa de los `/resumen` de cada módulo a propósito: los resúmenes son
 * un número por tarjeta, y esto son decenas de filas para dibujar. Mezclarlos
 * obligaría a los quince módulos a saber de gráficos, y a quien solo quiere la
 * cifra de "plazos vencidos" a pagar la consulta entera del panel.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/graficos")
    @PreAuthorize("hasAuthority('DASHBOARD_LEER')")
    public Graficos graficos() {
        return service.graficos();
    }

    /**
     * Pide los dos permisos. EXPEDIENTE_LEER porque son expedientes (recepcion
     * no lo tiene); DASHBOARD_LEER porque es una vista interna del estudio, con
     * riesgo y responsable: el portal del cliente tiene EXPEDIENTE_LEER para sus
     * casos, y con solo ese permiso recibia aqui su expediente marcado "critico".
     */
    @GetMapping("/expedientes-atencion")
    @PreAuthorize("hasAuthority('DASHBOARD_LEER') and hasAuthority('EXPEDIENTE_LEER')")
    public List<ExpedienteAtencion> expedientesAtencion() {
        return service.expedientesAtencion();
    }
}
