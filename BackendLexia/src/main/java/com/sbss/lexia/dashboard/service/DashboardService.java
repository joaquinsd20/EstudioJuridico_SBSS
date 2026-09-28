package com.sbss.lexia.dashboard.service;

import com.sbss.lexia.dashboard.dto.DashboardDtos.Graficos;
import com.sbss.lexia.dashboard.repository.DashboardRepositoryJdbc;
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
        return new Graficos(
                repo.expedientesPorEstado(),
                repo.expedientesPorEspecialidad(),
                repo.expedientesPorAbogado(),
                repo.cargaPorUsuario(),
                repo.tareasPorUsuario(),
                repo.ingresosPorMes(),
                repo.cobranza());
    }
}
