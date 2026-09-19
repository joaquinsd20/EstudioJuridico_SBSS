package com.sbss.lexia.expedientes.service;

import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Catalogos;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.EventoLineaTiempo;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteDetalle;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.ExpedienteLista;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.GuardarRequest;
import com.sbss.lexia.expedientes.dto.ExpedienteDtos.Resumen;
import com.sbss.lexia.expedientes.repository.ExpedienteRepositoryJdbc;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpedienteService {

    private final ExpedienteRepositoryJdbc repository;

    @Transactional(readOnly = true)
    public List<ExpedienteLista> listar(String texto, String cliente, String estado, Long materiaId, String organo) {
        return repository.listar(
                SesionActual.estudioId(), SesionActual.usuarioId(), verTodos(), verConfidenciales(),
                texto, cliente, estado, materiaId, organo);
    }

    @Transactional(readOnly = true)
    public Resumen resumen() {
        return repository.resumen(
                SesionActual.estudioId(), SesionActual.usuarioId(), verTodos(), verConfidenciales());
    }

    @Transactional(readOnly = true)
    public ExpedienteDetalle obtener(String uuid) {
        ExpedienteDetalle detalle = repository.detalle(
                uuid, SesionActual.estudioId(), SesionActual.usuarioId(), verTodos(), verConfidenciales());
        if (detalle == null) throw RecursoNoEncontradoException.de("Expediente", uuid);
        return detalle;
    }

    @Transactional(readOnly = true)
    public List<EventoLineaTiempo> lineaTiempo(String uuid) {
        List<EventoLineaTiempo> eventos = repository.lineaTiempo(
                uuid, SesionActual.estudioId(), SesionActual.usuarioId(), verTodos(), verConfidenciales());
        if (eventos.isEmpty()) {
            // Diferencia entre "expediente sin eventos" y "expediente inexistente/no visible".
            obtener(uuid);
        }
        return eventos;
    }

    @Transactional(readOnly = true)
    public Catalogos catalogos() {
        return repository.catalogos(SesionActual.estudioId());
    }

    @Transactional
    public String crear(GuardarRequest request) {
        validarRequest(request);
        validarReferencias(request);
        validarNumeroExpedienteUnico(request.numeroExpediente(), null);
        return repository.crear(SesionActual.estudioId(), SesionActual.usuarioId(), request);
    }

    @Transactional
    public void actualizar(String uuid, GuardarRequest request) {
        validarRequest(request);
        validarReferencias(request);
        validarNumeroExpedienteUnico(request.numeroExpediente(), uuid);
        // Verifica visibilidad y frontera de tenant antes de permitir la escritura.
        obtener(uuid);
        if (!repository.actualizar(uuid, SesionActual.estudioId(), SesionActual.usuarioId(), request)) {
            throw RecursoNoEncontradoException.de("Expediente", uuid);
        }
    }


    private void validarNumeroExpedienteUnico(String numeroExpediente, String uuidExcluir) {
        if (numeroExpediente == null || numeroExpediente.isBlank()) return;
        if (repository.existeNumeroExpediente(SesionActual.estudioId(), numeroExpediente, uuidExcluir)) {
            throw new ParametroInvalidoException(
                    "El N.º de expediente '" + numeroExpediente.trim() + "' ya está registrado en el estudio");
        }
    }

    private void validarReferencias(GuardarRequest request) {
        if (!repository.referenciasValidas(SesionActual.estudioId(), request)) {
            throw new ParametroInvalidoException(
                    "Uno de los clientes, catálogos, responsables o contrapartes seleccionados no pertenece al estudio o está inactivo");
        }
    }

    private void validarRequest(GuardarRequest r) {
        if (!List.of("BAJO", "MEDIO", "ALTO", "CRITICO").contains(r.nivelRiesgo().toUpperCase())) {
            throw new ParametroInvalidoException("El riesgo debe ser BAJO, MEDIO, ALTO o CRITICO");
        }
        if (r.fechaCierre() != null && r.fechaCierre().isBefore(r.fechaInicio())) {
            throw new ParametroInvalidoException("La fecha de cierre no puede ser anterior a la fecha de inicio");
        }
    }

    private boolean verTodos() {
        return tieneAutoridad("ADMINISTRADOR") || tieneAutoridad("SOCIO");
    }

    private boolean verConfidenciales() {
        return tieneAutoridad("EXPEDIENTE_CONFIDENCIAL") || tieneAutoridad("ADMINISTRADOR") || tieneAutoridad("SOCIO");
    }

    private boolean tieneAutoridad(String codigo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> codigo.equals(a.getAuthority()));
    }
}
