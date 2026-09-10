package com.sbss.lexia.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa el llenado automatico de las columnas de auditoria heredadas de
 * {@code EntidadAuditable}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
public class AuditoriaConfig {
}
