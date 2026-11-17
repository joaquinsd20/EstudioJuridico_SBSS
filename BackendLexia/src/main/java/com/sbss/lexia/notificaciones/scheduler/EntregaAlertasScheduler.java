package com.sbss.lexia.notificaciones.scheduler;

import com.sbss.lexia.notificaciones.service.GeneradorNotificaciones;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Cada pocos minutos entrega las alertas cuya hora ya llego (RF-10). La logica
 * vive en {@link GeneradorNotificaciones}; esto solo la dispara.
 *
 * La frecuencia se cambia con `lexia.alertas.entrega-cron` (por defecto cada
 * 5 minutos, en hora de Lima).
 */
// @Lazy(false): con spring.main.lazy-initialization (Dockerfile) un bean
// perezoso nunca registraria su @Scheduled y la tarea no correria jamas.
@Lazy(false)
@Component
@RequiredArgsConstructor
public class EntregaAlertasScheduler {

    private final GeneradorNotificaciones generador;

    @Scheduled(cron = "${lexia.alertas.entrega-cron:0 */5 * * * *}", zone = "America/Lima")
    public void entregar() {
        generador.entregarAlertasVencidas();
    }
}
