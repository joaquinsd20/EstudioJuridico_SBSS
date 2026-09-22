package com.sbss.lexia.agenda.service;

import com.sbss.lexia.agenda.dto.AgendaDtos.ActualizarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.Catalogos;
import com.sbss.lexia.agenda.dto.AgendaDtos.EventoDetalle;
import com.sbss.lexia.agenda.dto.AgendaDtos.EventoLista;
import com.sbss.lexia.agenda.dto.AgendaDtos.GuardarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.ReprogramarRequest;
import com.sbss.lexia.agenda.dto.AgendaDtos.Resumen;
import com.sbss.lexia.agenda.dto.AgendaDtos.ResultadoRequest;
import com.sbss.lexia.agenda.repository.AgendaRepositoryJdbc;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgendaService {

    private static final Set<String> TIPOS = Set.of(
            "AUDIENCIA", "REUNION_CLIENTE", "VENCIMIENTO_PLAZO", "LLAMADA", "DILIGENCIA",
            "PRESENTACION", "VIDEOCONFERENCIA", "REVISION", "INTERNO");
    private static final Set<String> MODALIDADES = Set.of("PRESENCIAL", "VIRTUAL", "HIBRIDA");
    private static final Set<String> ESTADOS = Set.of("PROGRAMADO", "CONFIRMADO", "REPROGRAMADO", "REALIZADO", "CANCELADO");
    private static final Set<String> ORIGENES = Set.of("MANUAL", "DESCARGADA", "CORREO", "CLIENTE", "IA");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Map<String, String> COLORES = Map.ofEntries(
            Map.entry("AUDIENCIA", "#448EA2"),
            Map.entry("REUNION_CLIENTE", "#7952A8"),
            Map.entry("VENCIMIENTO_PLAZO", "#CB5B54"),
            Map.entry("LLAMADA", "#D08A34"),
            Map.entry("DILIGENCIA", "#58A96D"),
            Map.entry("PRESENTACION", "#3976B8"),
            Map.entry("VIDEOCONFERENCIA", "#5B67C8"),
            Map.entry("REVISION", "#7D8795"),
            Map.entry("INTERNO", "#64748B"));

    private final AgendaRepositoryJdbc repository;

    @Transactional(readOnly = true)
    public List<EventoLista> listar(LocalDateTime desde, LocalDateTime hasta, String texto, String tipo,
            String estado, Long expedienteId, Long participanteId) {
        if (desde == null || hasta == null) {
            LocalDate hoy = LocalDate.now();
            desde = hoy.withDayOfMonth(1).atStartOfDay();
            hasta = hoy.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
        }
        if (hasta.isBefore(desde)) throw new ParametroInvalidoException("La fecha final no puede ser anterior a la inicial");
        if (tipo != null && !tipo.isBlank() && !TIPOS.contains(normalizar(tipo))) {
            throw new ParametroInvalidoException("Tipo de evento no válido");
        }
        if (estado != null && !estado.isBlank() && !ESTADOS.contains(normalizar(estado))) {
            throw new ParametroInvalidoException("Estado de evento no válido");
        }
        return repository.listar(desde, hasta, texto, tipo, estado, expedienteId, participanteId,
                verTodos(), verConfidenciales());
    }

    @Transactional(readOnly = true)
    public Resumen resumen() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = hoy.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
        List<EventoLista> mes = repository.listar(inicioMes, finMes, null, null, null, null, null,
                verTodos(), verConfidenciales());
        LocalDateTime ahora = LocalDateTime.now();
        List<EventoLista> proximos = repository.listar(ahora, ahora.plusDays(7), null, null, null, null, null,
                verTodos(), verConfidenciales());

        long hoyCount = mes.stream()
                .filter(e -> !"CANCELADO".equals(e.estado()))
                .filter(e -> !e.fechaInicio().toLocalDate().isAfter(hoy) && !e.fechaFin().toLocalDate().isBefore(hoy))
                .count();
        long proximosCount = proximos.stream().filter(e -> !"CANCELADO".equals(e.estado())).count();
        long audiencias = mes.stream().filter(e -> "AUDIENCIA".equals(e.tipo()) && !"CANCELADO".equals(e.estado())).count();
        long verificar = mes.stream().filter(EventoLista::requiereVerificacion).count();
        long total = mes.stream().filter(e -> !"CANCELADO".equals(e.estado())).count();
        return new Resumen(hoyCount, proximosCount, audiencias, verificar, total);
    }

    @Transactional(readOnly = true)
    public Catalogos catalogos() {
        return repository.catalogos(verTodos(), verConfidenciales());
    }

    @Transactional(readOnly = true)
    public EventoDetalle detalle(String uuid) {
        EventoLista evento = obtenerVisible(uuid);
        return new EventoDetalle(evento, repository.participantes(uuid), repository.reprogramaciones(uuid));
    }

    @Transactional
    public EventoDetalle crear(GuardarRequest request) {
        validarGuardar(request.expedienteId(), request.tipo(), request.titulo(), request.fechaInicio(), request.fechaFin(),
                request.modalidad(), request.recordatorioMinutos(), request.colorHex(), request.origenInformacion(),
                request.participanteIds());
        boolean requiere = request.requiereVerificacion() || "IA".equals(normalizar(request.origenInformacion()));
        GuardarRequest normalizado = new GuardarRequest(
                request.expedienteId(), normalizar(request.tipo()), request.titulo().trim(), limpiar(request.descripcion()),
                request.fechaInicio(), request.fechaFin(), request.todoElDia(), normalizar(request.modalidad()),
                limpiar(request.ubicacion()), limpiar(request.enlaceVirtual()), request.recordatorioMinutos(),
                color(request.tipo(), request.colorHex()), normalizar(request.origenInformacion()), requiere,
                request.participanteIds());
        String uuid = repository.crear(normalizado, normalizado.colorHex());
        return detalle(uuid);
    }

    @Transactional
    public EventoDetalle actualizar(String uuid, ActualizarRequest request) {
        EventoLista existente = obtenerVisible(uuid);
        validarGuardar(request.expedienteId(), request.tipo(), request.titulo(), request.fechaInicio(), request.fechaFin(),
                request.modalidad(), request.recordatorioMinutos(), request.colorHex(), request.origenInformacion(),
                request.participanteIds());
        if (!existente.fechaInicio().equals(request.fechaInicio()) || !existente.fechaFin().equals(request.fechaFin())) {
            throw new ParametroInvalidoException("Para cambiar la fecha u hora usa la opción Reprogramar, así se conserva el historial");
        }
        String estado = normalizar(request.estado());
        if (!ESTADOS.contains(estado)) throw new ParametroInvalidoException("Estado de evento no válido");
        if ("REALIZADO".equals(estado) && (request.resultado() == null || request.resultado().isBlank())) {
            throw new ParametroInvalidoException("Un evento realizado debe registrar su resultado");
        }
        boolean requiere = request.requiereVerificacion();
        ActualizarRequest normalizado = new ActualizarRequest(
                request.expedienteId(), normalizar(request.tipo()), request.titulo().trim(), limpiar(request.descripcion()),
                request.fechaInicio(), request.fechaFin(), request.todoElDia(), normalizar(request.modalidad()), estado,
                limpiar(request.resultado()), limpiar(request.ubicacion()), limpiar(request.enlaceVirtual()),
                request.recordatorioMinutos(), color(request.tipo(), request.colorHex()),
                normalizar(request.origenInformacion()), requiere, request.participanteIds());
        if (!repository.actualizar(uuid, normalizado, normalizado.colorHex())) {
            throw RecursoNoEncontradoException.de("Evento de agenda", uuid);
        }
        return detalle(uuid);
    }

    @Transactional
    public EventoDetalle reprogramar(String uuid, ReprogramarRequest request) {
        EventoLista existente = obtenerVisible(uuid);
        if ("CANCELADO".equals(existente.estado()) || "REALIZADO".equals(existente.estado())) {
            throw new ParametroInvalidoException("No se puede reprogramar un evento cancelado o realizado");
        }
        validarFechas(request.fechaInicio(), request.fechaFin());
        if (!repository.reprogramar(uuid, request.fechaInicio(), request.fechaFin(), request.motivo(), request.recordatorioMinutos())) {
            throw RecursoNoEncontradoException.de("Evento de agenda", uuid);
        }
        return detalle(uuid);
    }

    @Transactional
    public EventoDetalle registrarResultado(String uuid, ResultadoRequest request) {
        EventoLista existente = obtenerVisible(uuid);
        if ("CANCELADO".equals(existente.estado())) {
            throw new ParametroInvalidoException("No se puede registrar resultado en un evento cancelado");
        }
        if (!repository.registrarResultado(uuid, request.resultado(), request.verificado())) {
            throw RecursoNoEncontradoException.de("Evento de agenda", uuid);
        }
        return detalle(uuid);
    }

    @Transactional
    public EventoDetalle cancelar(String uuid) {
        EventoLista existente = obtenerVisible(uuid);
        if ("REALIZADO".equals(existente.estado())) {
            throw new ParametroInvalidoException("No se puede cancelar un evento que ya fue realizado");
        }
        if (!repository.cancelar(uuid)) throw RecursoNoEncontradoException.de("Evento de agenda", uuid);
        return detalle(uuid);
    }

    private EventoLista obtenerVisible(String uuid) {
        EventoLista evento = repository.obtener(uuid, verTodos(), verConfidenciales());
        if (evento == null) throw RecursoNoEncontradoException.de("Evento de agenda", uuid);
        return evento;
    }

    private void validarGuardar(Long expedienteId, String tipo, String titulo, LocalDateTime inicio, LocalDateTime fin,
            String modalidad, Integer recordatorio, String color, String origen, List<Long> participanteIds) {
        String tipoN = normalizar(tipo);
        String modalidadN = normalizar(modalidad);
        String origenN = normalizar(origen);
        if (!TIPOS.contains(tipoN)) throw new ParametroInvalidoException("Tipo de evento no válido");
        if (!MODALIDADES.contains(modalidadN)) throw new ParametroInvalidoException("Modalidad no válida");
        if (!ORIGENES.contains(origenN)) throw new ParametroInvalidoException("Origen de información no válido");
        if (titulo == null || titulo.isBlank()) throw new ParametroInvalidoException("El título es obligatorio");
        validarFechas(inicio, fin);
        if (recordatorio != null && recordatorio > 525600) {
            throw new ParametroInvalidoException("El recordatorio no puede superar un año");
        }
        if (color != null && !color.isBlank() && !COLOR.matcher(color).matches()) {
            throw new ParametroInvalidoException("El color debe usar el formato hexadecimal #RRGGBB");
        }
        if (!repository.expedienteVisible(expedienteId, verTodos(), verConfidenciales())) {
            throw new ParametroInvalidoException("El expediente seleccionado no existe o no está disponible para el usuario");
        }
        if (!repository.usuariosValidos(participanteIds)) {
            throw new ParametroInvalidoException("Uno de los participantes no pertenece al estudio o está inactivo");
        }
    }

    private static void validarFechas(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null) throw new ParametroInvalidoException("Indica fecha y hora de inicio y fin");
        if (fin.isBefore(inicio)) throw new ParametroInvalidoException("La fecha fin no puede ser anterior a la fecha inicio");
    }

    private static String color(String tipo, String color) {
        if (color != null && COLOR.matcher(color).matches()) return color.toUpperCase(Locale.ROOT);
        return COLORES.getOrDefault(normalizar(tipo), "#64748B");
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

    private static String normalizar(String v) {
        return v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
    }

    private static String limpiar(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
