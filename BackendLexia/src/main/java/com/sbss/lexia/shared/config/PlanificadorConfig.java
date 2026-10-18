package com.sbss.lexia.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas. Sin esta anotacion, los `@Scheduled` se
 * compilan pero NO corren: PapeleraDocumentoScheduler llevaba semanas sin
 * ejecutarse (ScheduledTaskHolder con 0 tareas) y nadie lo noto.
 *
 * Al activarla corren dos tareas:
 *  - EntregaAlertasScheduler: convierte las alertas vencidas en notificaciones.
 *  - PapeleraDocumentoScheduler: BORRA DEFINITIVAMENTE, a las 2:15, los documentos
 *    que llevan mas de `lexia.documentos.papelera-dias` (7) en la papelera.
 */
@Configuration
@EnableScheduling
public class PlanificadorConfig {
}
