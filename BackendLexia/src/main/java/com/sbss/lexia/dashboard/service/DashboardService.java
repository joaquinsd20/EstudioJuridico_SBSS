package com.sbss.lexia.dashboard.service;

import com.sbss.lexia.dashboard.dto.DashboardDtos.EstadoCobranza;
import com.sbss.lexia.dashboard.dto.DashboardDtos.ExpedienteAtencion;
import com.sbss.lexia.dashboard.dto.DashboardDtos.Graficos;
import com.sbss.lexia.dashboard.repository.DashboardRepositoryJdbc;
import com.sbss.lexia.shared.security.SesionActual;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepositoryJdbc repo;

    /**
     * Reúne los datos de los seis gráficos en una llamada.
     *
     * Va marcado como transaccional de solo lectura para que las seis
     * consultas vean el mismo instante. Sin ello, un gráfico que se dibuja
     * mientras alguien registra una actuación puede no cuadrar con los otros
     * cinco, y el usuario ve números que se contradicen entre sí.
     */
    @Transactional(readOnly = true)
    public Graficos graficos() {
        // El dashboard solo exige DASHBOARD_LEER, que tienen siete de los ocho
        // roles. Los honorarios son de facturacion: sin su permiso, las dos
        // consultas ni se ejecutan. Antes un abogado recibia aqui el facturado y
        // el pendiente de todo el estudio, mientras /facturacion le daba 403.
        boolean facturacion = SesionActual.tieneAutoridad("FACTURACION_LEER");
        return new Graficos(
                repo.expedientesPorEstado(),
                repo.expedientesPorEspecialidad(),
                repo.expedientesPorAbogado(),
                repo.cargaPorUsuario(),
                repo.tareasPorUsuario(),
                facturacion ? repo.ingresosPorMes() : List.of(),
                facturacion ? repo.cobranza() : EstadoCobranza.sinAcceso(),
                facturacion);
    }

    @Transactional(readOnly = true)
    public List<ExpedienteAtencion> expedientesAtencion() {
        return repo.expedientesAtencion();
    }
}
