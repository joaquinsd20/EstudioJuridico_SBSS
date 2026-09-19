package com.sbss.lexia.clientes.service;

import com.sbss.lexia.clientes.dto.ClienteDetalleDto;
import com.sbss.lexia.clientes.dto.ClienteFiltro;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.Catalogos;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.CoincidenciaConflicto;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.FichaCliente;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.GuardarClienteRequest;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.RegistrarComunicacionRequest;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.RelacionConflicto;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.VerificarConflictoRequest;
import com.sbss.lexia.clientes.dto.ClienteGestionDtos.VerificarConflictoResponse;
import com.sbss.lexia.clientes.dto.ClienteResumenDto;
import com.sbss.lexia.clientes.dto.ClientesResumenDto;
import com.sbss.lexia.clientes.entity.Cliente;
import com.sbss.lexia.clientes.mapper.ClienteMapper;
import com.sbss.lexia.clientes.repository.ClienteGestionRepositoryJdbc;
import com.sbss.lexia.clientes.repository.ClienteRepository;
import com.sbss.lexia.clientes.spec.ClienteSpecification;
import com.sbss.lexia.seguridad.entity.Usuario;
import com.sbss.lexia.seguridad.repository.UsuarioRepository;
import com.sbss.lexia.shared.dto.PaginaDto;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final Set<String> ORDENAMIENTOS_PERMITIDOS = Set.of(
            "nombreBusqueda", "numeroDocumento", "estado", "fechaUltimoContacto", "fechaCreacion");
    private static final Set<String> TIPOS_PERSONA = Set.of("NATURAL", "JURIDICA", "ENTIDAD_PUBLICA");
    private static final Set<String> TIPOS_DOCUMENTO = Set.of("DNI", "RUC", "CE", "PASAPORTE", "PARTIDA_REGISTRAL");
    private static final Set<String> ESTADOS = Set.of("PROSPECTO", "ACTIVO", "INACTIVO", "ANTIGUO");
    private static final Set<String> SITUACIONES = Set.of("SOLIDA", "ESTABLE", "REGULAR", "RIESGO", "MOROSA");
    private static final Set<String> TIPOS_COMUNICACION = Set.of("LLAMADA", "EMAIL", "WHATSAPP", "REUNION", "OTRO");

    private final ClienteRepository clienteRepository;
    private final ClienteGestionRepositoryJdbc gestionRepository;
    private final UsuarioRepository usuarioRepository;

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
                .map(cliente -> ClienteMapper.aResumenDto(cliente, conteos.getOrDefault(cliente.getId(), 0L)))
                .toList();
        return PaginaDto.desde(pagina, contenido);
    }

    @Transactional(readOnly = true)
    public ClienteDetalleDto obtener(String uuid) {
        Cliente cliente = buscarCliente(uuid);
        long total = contarExpedientes(SesionActual.estudioId(), List.of(cliente)).getOrDefault(cliente.getId(), 0L);
        return ClienteMapper.aDetalleDto(cliente, total);
    }

    @Transactional(readOnly = true)
    public FichaCliente ficha(String uuid) {
        Integer estudioId = SesionActual.estudioId();
        Cliente cliente = buscarCliente(uuid);
        long total = contarExpedientes(estudioId, List.of(cliente)).getOrDefault(cliente.getId(), 0L);
        return new FichaCliente(
                ClienteMapper.aDetalleDto(cliente, total),
                gestionRepository.contactos(cliente.getId()),
                gestionRepository.vinculados(cliente.getId(), estudioId),
                gestionRepository.expedientes(cliente.getId(), estudioId),
                gestionRepository.comunicaciones(cliente.getId(), estudioId));
    }

    @Transactional(readOnly = true)
    public ClientesResumenDto resumen() {
        Integer estudioId = SesionActual.estudioId();
        ClienteRepository.ResumenProjection p = clienteRepository.resumen(estudioId);
        return new ClientesResumenDto(
                p.getTotal(), p.getActivos(), p.getNaturales(), p.getJuridicas(),
                p.getEntidadesPublicas(), p.getInactivos(), p.getNuevosEsteMes(), p.getConExpedientes());
    }

    @Transactional(readOnly = true)
    public Catalogos catalogos() {
        return new Catalogos(gestionRepository.abogados(SesionActual.estudioId()));
    }

    @Transactional
    public String crear(GuardarClienteRequest request) {
        validarRequest(request);
        Integer estudioId = SesionActual.estudioId();
        validarDocumentoDuplicado(estudioId, request.tipoDocumento(), request.numeroDocumento(), null);

        Cliente cliente = new Cliente();
        cliente.setEstudioId(estudioId);
        aplicar(cliente, request, resolverAbogado(request.abogadoResponsableUuid(), estudioId));
        clienteRepository.saveAndFlush(cliente);

        gestionRepository.reemplazarContactos(cliente.getId(), SesionActual.usuarioId(), request.contactos());
        gestionRepository.reemplazarVinculos(cliente.getId(), estudioId, SesionActual.usuarioId(), request.vinculados());
        return cliente.getUuidPublico();
    }

    @Transactional
    public void actualizar(String uuid, GuardarClienteRequest request) {
        validarRequest(request);
        Integer estudioId = SesionActual.estudioId();
        Cliente cliente = buscarCliente(uuid);
        validarDocumentoDuplicado(estudioId, request.tipoDocumento(), request.numeroDocumento(), uuid);

        aplicar(cliente, request, resolverAbogado(request.abogadoResponsableUuid(), estudioId));
        clienteRepository.saveAndFlush(cliente);
        gestionRepository.reemplazarContactos(cliente.getId(), SesionActual.usuarioId(), request.contactos());
        gestionRepository.reemplazarVinculos(cliente.getId(), estudioId, SesionActual.usuarioId(), request.vinculados());
    }

    @Transactional
    public String registrarComunicacion(String uuid, RegistrarComunicacionRequest request) {
        String tipo = normalizar(request.tipo());
        if (!TIPOS_COMUNICACION.contains(tipo)) {
            throw new ParametroInvalidoException("El tipo de comunicación no es válido");
        }
        Cliente cliente = buscarCliente(uuid);
        LocalDateTime fecha = request.fecha() == null ? LocalDateTime.now() : request.fecha();
        String comunicacionUuid = gestionRepository.registrarComunicacion(
                cliente.getId(), SesionActual.estudioId(), SesionActual.usuarioId(),
                tipo, request.asunto(), request.detalle(), request.canal(), fecha);
        cliente.setFechaUltimoContacto(fecha);
        clienteRepository.save(cliente);
        return comunicacionUuid;
    }

    @Transactional(readOnly = true)
    public VerificarConflictoResponse verificarConflicto(VerificarConflictoRequest request) {
        List<CoincidenciaConflicto> encontrados = new ArrayList<>();
        agregarCoincidencias(encontrados, request.nombre(), request.numeroDocumento(),
                request.excluirClienteUuid(), etiqueta(request.nombre(), request.numeroDocumento()));

        if (request.relacionados() != null) {
            for (RelacionConflicto r : request.relacionados()) {
                agregarCoincidencias(encontrados, r.nombre(), r.numeroDocumento(),
                        request.excluirClienteUuid(), etiqueta(r.nombre(), r.numeroDocumento()));
            }
        }

        // Un mismo registro puede coincidir por el nombre del cliente y por una
        // persona relacionada. Se conserva una sola alerta por origen/referencia/nombre/documento.
        Map<String, CoincidenciaConflicto> unicos = new LinkedHashMap<>();
        for (CoincidenciaConflicto c : encontrados) {
            String clave = String.join("|", n(c.origen()), n(c.referenciaUuid()), n(c.nombre()), n(c.documento()));
            CoincidenciaConflicto previo = unicos.get(clave);
            if (previo == null || "DOCUMENTO_EXACTO".equals(c.tipoCoincidencia())) {
                unicos.put(clave, c);
            }
        }
        List<CoincidenciaConflicto> coincidencias = new ArrayList<>(unicos.values());
        coincidencias.sort((a, b) -> Boolean.compare(
                "DOCUMENTO_EXACTO".equals(b.tipoCoincidencia()),
                "DOCUMENTO_EXACTO".equals(a.tipoCoincidencia())));
        return new VerificarConflictoResponse(!coincidencias.isEmpty(), coincidencias.size(), coincidencias);
    }

    private void agregarCoincidencias(
            List<CoincidenciaConflicto> destino, String nombre, String documento,
            String excluirUuid, String termino) {
        String nombreLimpio = nuloSiVacio(nombre);
        String documentoLimpio = nuloSiVacio(documento);
        if (nombreLimpio != null && nombreLimpio.length() < 3) nombreLimpio = null;
        if (nombreLimpio == null && documentoLimpio == null) return;
        destino.addAll(gestionRepository.buscarConflictos(
                SesionActual.estudioId(), nombreLimpio, documentoLimpio, excluirUuid, termino));
    }

    private void aplicar(Cliente cliente, GuardarClienteRequest r, Usuario abogado) {
        String tipoPersona = normalizar(r.tipoPersona());
        cliente.setTipoPersona(tipoPersona);
        cliente.setTipoDocumento(normalizar(r.tipoDocumento()));
        cliente.setNumeroDocumento(r.numeroDocumento().trim());
        cliente.setNombres(tipoPersona.equals("NATURAL") ? nuloSiVacio(r.nombres()) : null);
        cliente.setApellidoPaterno(tipoPersona.equals("NATURAL") ? nuloSiVacio(r.apellidoPaterno()) : null);
        cliente.setApellidoMaterno(tipoPersona.equals("NATURAL") ? nuloSiVacio(r.apellidoMaterno()) : null);
        cliente.setRazonSocial(tipoPersona.equals("NATURAL") ? null : nuloSiVacio(r.razonSocial()));
        cliente.setNombreComercial(tipoPersona.equals("NATURAL") ? null : nuloSiVacio(r.nombreComercial()));
        cliente.setEmail(nuloSiVacio(r.email()));
        cliente.setTelefonoPrincipal(nuloSiVacio(r.telefonoPrincipal()));
        cliente.setTelefonoSecundario(nuloSiVacio(r.telefonoSecundario()));
        cliente.setDireccion(nuloSiVacio(r.direccion()));
        cliente.setDistrito(nuloSiVacio(r.distrito()));
        cliente.setProvincia(nuloSiVacio(r.provincia()));
        cliente.setDepartamento(nuloSiVacio(r.departamento()));
        cliente.setEstado(normalizar(r.estado()));
        cliente.setOrigenCaptacion(nuloSiVacio(r.origenCaptacion()));
        cliente.setSituacionFinanciera(nuloSiVacio(r.situacionFinanciera()) == null
                ? null : normalizar(r.situacionFinanciera()));
        cliente.setAbogadoResponsable(abogado);
        cliente.setObservaciones(nuloSiVacio(r.observaciones()));
    }

    private Usuario resolverAbogado(String uuid, Integer estudioId) {
        if (uuid == null || uuid.isBlank()) return null;
        return usuarioRepository.findByUuidPublicoAndEstudio_IdAndActivoTrue(uuid.trim(), estudioId)
                .filter(u -> "INTERNO".equals(u.getTipoUsuario()) && "ACTIVO".equals(u.getEstado()))
                .orElseThrow(() -> new ParametroInvalidoException("El abogado responsable seleccionado no es válido"));
    }

    private void validarRequest(GuardarClienteRequest r) {
        String tipoPersona = normalizar(r.tipoPersona());
        String tipoDocumento = normalizar(r.tipoDocumento());
        String estado = normalizar(r.estado());
        if (!TIPOS_PERSONA.contains(tipoPersona)) throw new ParametroInvalidoException("Tipo de persona inválido");
        if (!TIPOS_DOCUMENTO.contains(tipoDocumento)) throw new ParametroInvalidoException("Tipo de documento inválido");
        if (!ESTADOS.contains(estado)) throw new ParametroInvalidoException("Estado de cliente inválido");

        if (tipoPersona.equals("NATURAL")) {
            if (esVacio(r.nombres()) || esVacio(r.apellidoPaterno())) {
                throw new ParametroInvalidoException("Para una persona natural se requieren nombres y apellido paterno");
            }
        } else if (esVacio(r.razonSocial())) {
            throw new ParametroInvalidoException("Para una persona jurídica o entidad pública se requiere razón social");
        }

        String doc = r.numeroDocumento().trim();
        if (tipoDocumento.equals("DNI") && !doc.matches("\\d{8}")) {
            throw new ParametroInvalidoException("El DNI debe contener exactamente 8 dígitos");
        }
        if (tipoDocumento.equals("RUC") && !doc.matches("\\d{11}")) {
            throw new ParametroInvalidoException("El RUC debe contener exactamente 11 dígitos");
        }
        String situacion = nuloSiVacio(r.situacionFinanciera());
        if (situacion != null && !SITUACIONES.contains(normalizar(situacion))) {
            throw new ParametroInvalidoException("Situación financiera inválida");
        }

        if (r.contactos() != null) {
            r.contactos().forEach(c -> {
                if (esVacio(c.nombreCompleto())) throw new ParametroInvalidoException("Todo contacto debe tener nombre");
            });
        }
        if (r.vinculados() != null) {
            r.vinculados().forEach(v -> {
                if (!TIPOS_PERSONA.contains(normalizar(v.tipoPersona()))) {
                    throw new ParametroInvalidoException("Tipo de persona inválido en persona/empresa vinculada");
                }
            });
        }
    }

    private void validarDocumentoDuplicado(Integer estudioId, String tipo, String numero, String uuidExcluir) {
        clienteRepository.findByEstudioIdAndTipoDocumentoAndNumeroDocumento(
                        estudioId, normalizar(tipo), numero.trim())
                .filter(c -> uuidExcluir == null || !uuidExcluir.equals(c.getUuidPublico()))
                .ifPresent(c -> {
                    throw new ParametroInvalidoException(
                            "Ya existe un cliente con " + normalizar(tipo) + " " + numero.trim());
                });
    }

    private Cliente buscarCliente(String uuid) {
        return clienteRepository.findByUuidPublicoAndEstudioId(uuid, SesionActual.estudioId())
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", uuid));
    }

    private void validarOrdenamiento(Pageable pageable) {
        boolean todosPermitidos = pageable.getSort().stream()
                .map(Sort.Order::getProperty)
                .allMatch(ORDENAMIENTOS_PERMITIDOS::contains);
        if (!todosPermitidos) {
            throw new ParametroInvalidoException("El campo de ordenamiento debe ser uno de: " + ORDENAMIENTOS_PERMITIDOS);
        }
    }

    private Map<Long, Long> contarExpedientes(Integer estudioId, List<Cliente> clientes) {
        if (clientes.isEmpty()) return Map.of();
        List<Long> ids = clientes.stream().map(Cliente::getId).toList();
        return clienteRepository.contarExpedientesPorCliente(estudioId, ids).stream()
                .collect(Collectors.toMap(
                        ClienteRepository.ConteoExpedientes::getClienteId,
                        ClienteRepository.ConteoExpedientes::getTotal));
    }

    private static String etiqueta(String nombre, String documento) {
        String n = nuloSiVacio(nombre);
        String d = nuloSiVacio(documento);
        if (n != null && d != null) return n + " · " + d;
        return n != null ? n : (d != null ? d : "");
    }

    private static String n(String v) { return v == null ? "" : v; }
    private static boolean esVacio(String v) { return v == null || v.isBlank(); }
    private static String nuloSiVacio(String v) { return esVacio(v) ? null : v.trim(); }
    private static String normalizar(String v) { return v == null ? "" : v.trim().toUpperCase(); }
}
