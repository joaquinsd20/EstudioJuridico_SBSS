package com.sbss.lexia.monitor.service;

import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.AgregarExpedienteRequest;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Catalogos;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Consulta;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.ExpedienteMonitor;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.RegistrarConsultaRequest;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.Resumen;
import com.sbss.lexia.monitor.dto.MonitorJudicialDtos.VerificarRequest;
import com.sbss.lexia.monitor.repository.MonitorJudicialRepositoryJdbc;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonitorJudicialService {

    private static final int RANGO_MAXIMO_DIAS = 370;
    private static final Set<String> ORIGENES = Set.of("MANUAL", "DESCARGADA", "CORREO", "CLIENTE", "IA");
    private static final Set<String> VERIFICACIONES = Set.of("PENDIENTE", "VERIFICADA", "RECHAZADA");
    private static final Set<String> TIPOS_RESOLUCION = Set.of(
            "AUTO", "SENTENCIA", "DECRETO", "PROVIDENCIA", "CASACION", "ACUERDO", "RESOLUCION_ADMIN");

    private final MonitorJudicialRepositoryJdbc repository;

    public List<ExpedienteMonitor> listar(String expediente, String cliente, String abogado,
            String organo, String estadoProcesal, LocalDate desde, LocalDate hasta,
            int limite, int desplazamiento) {
        validarRango(desde, hasta);
        return repository.listar(expediente, cliente, abogado, organo, estadoProcesal,
                desde, hasta, limite, desplazamiento);
    }

    public PaginaDto<ExpedienteMonitor> listarPagina(String expediente, String cliente, String abogado,
            String organo, String estadoProcesal, LocalDate desde, LocalDate hasta,
            int pagina, int tamano) {
        validarRango(desde, hasta);
        validarPaginacion(pagina, tamano);
        return repository.listarPagina(expediente, cliente, abogado, organo, estadoProcesal,
                desde, hasta, pagina, tamano);
    }

    public List<Consulta> consultas(Long expedienteId, String tipoMovimiento, String estadoVerificacion,
            LocalDate desde, LocalDate hasta, int limite, int desplazamiento) {
        validarRango(desde, hasta);
        validarEstadoVerificacionFiltro(estadoVerificacion);
        return repository.listarConsultas(expedienteId, tipoMovimiento, estadoVerificacion,
                desde, hasta, limite, desplazamiento);
    }

    public PaginaDto<Consulta> consultasPagina(Long expedienteId, String tipoMovimiento,
            String estadoVerificacion, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        validarRango(desde, hasta);
        validarEstadoVerificacionFiltro(estadoVerificacion);
        validarPaginacion(pagina, tamano);
        return repository.listarConsultasPagina(expedienteId, tipoMovimiento, estadoVerificacion,
                desde, hasta, pagina, tamano);
    }

    public Resumen resumen() {
        return repository.resumen();
    }

    public Catalogos catalogos(String busqueda, int limite) {
        if (limite < 1) {
            throw new ParametroInvalidoException("El límite del catálogo debe ser mayor que cero");
        }
        return repository.catalogos(busqueda, limite);
    }

    @Transactional
    public String agregar(AgregarExpedienteRequest request) {
        validarExpediente(request.expedienteId());
        return repository.agregarExpediente(request.expedienteId(), request.fuentePlataforma());
    }

    @Transactional
    public String registrarConsulta(RegistrarConsultaRequest request) {
        validarExpediente(request.expedienteId());
        String origen = normalizar(request.origenInformacion());
        if (!ORIGENES.contains(origen)) {
            throw new ParametroInvalidoException("Origen de información no válido");
        }

        String verificacion = normalizar(request.estadoVerificacion());
        if (!VERIFICACIONES.contains(verificacion)) {
            throw new ParametroInvalidoException("Estado de verificación no válido");
        }
        // Toda información generada por IA debe quedar pendiente hasta revisión humana.
        if ("IA".equals(origen)) {
            verificacion = "PENDIENTE";
        }

        if (request.nuevaResolucion()) {
            if (request.numeroResolucion() == null || request.numeroResolucion().isBlank()) {
                throw new ParametroInvalidoException("Indica el número de la nueva resolución");
            }
            if (request.fechaNotificacion() == null) {
                throw new ParametroInvalidoException("La fecha de notificación es obligatoria para una nueva resolución");
            }
            if (request.tipoResolucion() != null && !request.tipoResolucion().isBlank()
                    && !TIPOS_RESOLUCION.contains(normalizar(request.tipoResolucion()))) {
                throw new ParametroInvalidoException("Tipo de resolución no válido");
            }
        }
        if (request.huboMovimiento() && (request.tipoMovimiento() == null || request.tipoMovimiento().isBlank())
                && !request.nuevaResolucion()) {
            throw new ParametroInvalidoException("Indica el tipo de movimiento detectado");
        }
        return repository.registrarConsulta(request, origen, verificacion);
    }

    @Transactional
    public void verificar(String uuid, VerificarRequest request) {
        String estado = normalizar(request.estadoVerificacion());
        if (!("VERIFICADA".equals(estado) || "RECHAZADA".equals(estado))) {
            throw new ParametroInvalidoException("La revisión debe terminar como VERIFICADA o RECHAZADA");
        }
        if (!repository.verificarConsulta(uuid, estado)) {
            throw RecursoNoEncontradoException.de("Consulta del monitor", uuid);
        }
    }

    @Transactional
    public void quitar(String uuid) {
        if (!repository.desactivar(uuid)) {
            throw RecursoNoEncontradoException.de("Expediente monitoreado", uuid);
        }
    }

    private void validarExpediente(Long expedienteId) {
        if (!repository.expedienteVisible(expedienteId)) {
            throw new ParametroInvalidoException("El expediente no existe o no está disponible para el usuario");
        }
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ParametroInvalidoException("La fecha hasta no puede ser anterior a la fecha desde");
        }
        if (desde != null && hasta != null && ChronoUnit.DAYS.between(desde, hasta) > RANGO_MAXIMO_DIAS) {
            throw new ParametroInvalidoException(
                    "El rango máximo de consulta del Monitor Judicial es de " + RANGO_MAXIMO_DIAS + " días");
        }
    }

    private static void validarPaginacion(int pagina, int tamano) {
        if (pagina < 0) {
            throw new ParametroInvalidoException("La página no puede ser negativa");
        }
        if (tamano < 1 || tamano > 100) {
            throw new ParametroInvalidoException("El tamaño de página debe estar entre 1 y 100");
        }
    }

    private static void validarEstadoVerificacionFiltro(String estadoVerificacion) {
        if (estadoVerificacion != null && !estadoVerificacion.isBlank()
                && !VERIFICACIONES.contains(normalizar(estadoVerificacion))) {
            throw new ParametroInvalidoException("Estado de verificación no válido");
        }
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
