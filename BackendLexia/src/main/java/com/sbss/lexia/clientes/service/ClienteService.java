package com.sbss.lexia.clientes.service;

import com.sbss.lexia.clientes.dto.ClienteDetalleDto;
import com.sbss.lexia.clientes.dto.ClienteFiltro;
import com.sbss.lexia.clientes.dto.ClienteResumenDto;
import com.sbss.lexia.clientes.dto.ClientesResumenDto;
import com.sbss.lexia.clientes.entity.Cliente;
import com.sbss.lexia.clientes.mapper.ClienteMapper;
import com.sbss.lexia.clientes.repository.ClienteRepository;
import com.sbss.lexia.clientes.spec.ClienteSpecification;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClienteService {

    /**
     * Campos por los que se permite ordenar el listado. No es lo mismo que
     * "existe en la entidad": observaciones, por ejemplo, es un campo real de
     * Cliente pero no esta indexado, y ordenar por el seria una consulta
     * lenta sin ningun valor de negocio. Un campo fuera de esta lista
     * devuelve 400 ANTES de tocar la base, en vez de una consulta costosa o
     * (si el campo ni siquiera existe) el 500 que evita PropertyReferenceException.
     */
    private static final Set<String> ORDENAMIENTOS_PERMITIDOS = Set.of(
            "nombreBusqueda", "numeroDocumento", "estado", "fechaUltimoContacto", "fechaCreacion");

    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public PaginaDto<ClienteResumenDto> listar(ClienteFiltro filtro, Pageable pageable) {
        validarOrdenamiento(pageable);

        Integer estudioId = SesionActual.estudioId();
        Specification<Cliente> spec = Specification.allOf(
                ClienteSpecification.delEstudio(estudioId),
                ClienteSpecification.soloActivos(),
                ClienteSpecification.documentoEmpiezaPor(filtro.documento()),
                ClienteSpecification.nombreContiene(filtro.nombre()),
                ClienteSpecification.tipoPersona(filtro.tipoPersona()),
                ClienteSpecification.estado(filtro.estado()));

        Page<Cliente> pagina = clienteRepository.findAll(spec, pageable);

        Map<Long, Long> conteos = contarExpedientes(estudioId, pagina.getContent());

        List<ClienteResumenDto> contenido = pagina.getContent().stream()
                .map(cliente -> ClienteMapper.aResumenDto(
                        cliente, conteos.getOrDefault(cliente.getId(), 0L)))
                .toList();

        return PaginaDto.desde(pagina, contenido);
    }

    @Transactional(readOnly = true)
    public ClienteDetalleDto obtener(String uuid) {
        Integer estudioId = SesionActual.estudioId();

        // findByUuidPublicoAndEstudioId, no findByUuidPublico: un uuid de otro
        // estudio debe verse igual que un uuid que no existe. Devolver 403 en
        // ese caso confirmaria que el recurso existe en otro lado, que ya es
        // una fuga de informacion entre inquilinos.
        Cliente cliente = clienteRepository.findByUuidPublicoAndEstudioId(uuid, estudioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", uuid));

        long total = contarExpedientes(estudioId, List.of(cliente))
                .getOrDefault(cliente.getId(), 0L);

        return ClienteMapper.aDetalleDto(cliente, total);
    }

    @Transactional(readOnly = true)
    public ClientesResumenDto resumen() {
        Integer estudioId = SesionActual.estudioId();
        ClienteRepository.ResumenProjection p = clienteRepository.resumen(estudioId);
        return new ClientesResumenDto(
                p.getTotal(), p.getActivos(), p.getNaturales(), p.getJuridicas(),
                p.getEntidadesPublicas(), p.getInactivos(), p.getNuevosEsteMes(),
                p.getConExpedientes());
    }

    private void validarOrdenamiento(Pageable pageable) {
        boolean todosPermitidos = pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .allMatch(ORDENAMIENTOS_PERMITIDOS::contains);
        if (!todosPermitidos) {
            throw new ParametroInvalidoException(
                    "El campo de ordenamiento debe ser uno de: " + ORDENAMIENTOS_PERMITIDOS);
        }
    }

    private Map<Long, Long> contarExpedientes(Integer estudioId, List<Cliente> clientes) {
        if (clientes.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = clientes.stream().map(Cliente::getId).toList();
        return clienteRepository.contarExpedientesPorCliente(estudioId, ids).stream()
                .collect(Collectors.toMap(
                        ClienteRepository.ConteoExpedientes::getClienteId,
                        ClienteRepository.ConteoExpedientes::getTotal));
    }
}
