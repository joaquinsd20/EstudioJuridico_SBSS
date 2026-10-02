package com.sbss.lexia.actuaciones.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sbss.lexia.actuaciones.dto.ActuacionDtos.*;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.ActuacionDetalle;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.ActuacionLista;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.AlertaAutomatica;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.Catalogos;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.CrearRequest;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.CrearResponse;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.DocumentoOpcion;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.DocumentoPendienteAutomatico;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.EventoAutomatico;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.PlazoAutomatico;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.Resumen;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.TareaAutomatica;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.TipoActuacionOpcion;
import com.sbss.lexia.actuaciones.dto.ActuacionDtos.TipoPlazoOpcion;
import com.sbss.lexia.actuaciones.repository.ActuacionRepositoryJdbc;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActuacionService {

    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "EN_TRAMITE", "COMPLETADO", "CANCELADO");
    private static final Set<String> PRIORIDADES = Set.of("BAJA", "MEDIA", "ALTA", "URGENTE");
    private static final Set<String> COMPUTOS = Set.of("HABILES", "CALENDARIO");
    private static final Set<String> RIESGOS = Set.of("BAJO", "MEDIO", "ALTO", "CRITICO");
    private static final Set<String> EVENTOS = Set.of("AUDIENCIA", "REUNION_CLIENTE", "VENCIMIENTO_PLAZO", "LLAMADA",
            "DILIGENCIA", "PRESENTACION", "VIDEOCONFERENCIA", "REVISION", "INTERNO");
    private static final Set<String> MODALIDADES = Set.of("PRESENCIAL", "VIRTUAL", "HIBRIDA");
    private static final Set<String> SEVERIDADES = Set.of("BAJA", "MEDIA", "ALTA", "CRITICA");
    private static final Set<String> CATEGORIAS_DOC = Set.of("DEMANDA", "CONTESTACION", "RECURSO", "ESCRITO", "RESOLUCION",
            "NOTIFICACION", "CARGO", "CONTRATO", "PODER", "PRUEBA", "INFORME", "MULTIMEDIA", "CORREO", "CLIENTE", "OTRO");

    private final ActuacionRepositoryJdbc repository;

    @Transactional(readOnly = true)
    public List<ActuacionLista> listar(String texto, Long tipoId, Long expedienteId, Long responsableId,
            String estado, LocalDate desde, LocalDate hasta, int limite, int desplazamiento) {
        String e = vacioANull(estado);
        if (e != null && !ESTADOS.contains(normalizar(e))) throw new ParametroInvalidoException("Estado de actuacion invalido");
        if (desde != null && hasta != null && hasta.isBefore(desde)) throw new ParametroInvalidoException("La fecha hasta no puede ser anterior a la fecha desde");
        return repository.listar(texto, tipoId, expedienteId, responsableId, e == null ? null : normalizar(e),
                desde, hasta, limite, desplazamiento);
    }

    @Transactional(readOnly = true)
    public Resumen resumen() {
        return repository.resumen();
    }

    @Transactional(readOnly = true)
    public ActuacionDetalle detalle(String uuid) {
        ActuacionDetalle d = repository.detalle(uuid);
        if (d == null) throw new RecursoNoEncontradoException("Actuacion no encontrada");
        return d;
    }

    @Transactional(readOnly = true)
    public Catalogos catalogos() {
        return repository.catalogos();
    }

    @Transactional(readOnly = true)
    public List<DocumentoOpcion> documentosDisponibles(Long expedienteId) {
        if (!repository.expedienteExiste(expedienteId)) throw new ParametroInvalidoException("El expediente no existe o no esta disponible");
        return repository.documentosDisponibles(expedienteId);
    }

    @Transactional
    public CrearResponse crear(CrearRequest r) {
        validarBase(r);
        TipoActuacionOpcion tipo = repository.tipoActuacion(r.tipoActuacionId());
        if (tipo == null) throw new ParametroInvalidoException("El tipo de actuacion no existe o esta inactivo");

        String estado = r.estado() == null || r.estado().isBlank() ? "PENDIENTE" : normalizar(r.estado());
        if (!ESTADOS.contains(estado)) throw new ParametroInvalidoException("Estado de actuacion invalido");
        String moneda = r.moneda() == null || r.moneda().isBlank() ? "PEN" : normalizar(r.moneda());
        if (moneda.length() != 3) throw new ParametroInvalidoException("La moneda debe tener 3 caracteres");

        long usuarioId = SesionActual.usuarioId();
        Long actuacionId = repository.insertarActuacion(
                r.expedienteId(), r.tipoActuacionId(), r.fechaHora(), limpiar(r.asunto()), limpiar(r.descripcion()),
                r.responsableId(), estado, limpiar(r.resultado()), limpiar(r.proximaAccion()), r.tiempoMinutos(),
                r.facturable(), r.costo(), moneda, usuarioId);

        Long plazoId = null;
        Long tareaId = null;
        Long eventoId = null;
        Long documentoPendienteId = null;
        int alertas = 0;

        boolean generarPlazo = tipo.generaPlazo() || generar(r.plazo());
        if (generarPlazo) {
            PlazoAutomatico p = exigirPlazo(r.plazo());
            TipoPlazoOpcion tp = repository.tipoPlazo(p.tipoPlazoId());
            if (tp == null) throw new ParametroInvalidoException("Selecciona un tipo de plazo valido");
            int dias = p.diasPlazo() != null ? p.diasPlazo() : (tp.diasPorDefecto() == null ? 0 : tp.diasPorDefecto());
            if (dias < 1) throw new ParametroInvalidoException("Indica la cantidad de dias del plazo");
            String computo = valorO(p.tipoComputo(), tp.tipoComputoDefecto());
            String riesgo = valorO(p.nivelRiesgo(), tp.nivelRiesgoDefecto());
            if (!COMPUTOS.contains(computo)) throw new ParametroInvalidoException("Tipo de computo invalido");
            if (!RIESGOS.contains(riesgo)) throw new ParametroInvalidoException("Nivel de riesgo invalido");
            if (p.requiereRevisionProfesional() && limpiar(p.motivoRevision()) == null) {
                throw new ParametroInvalidoException("Indica el motivo de la revision profesional del plazo");
            }
            LocalDate notificacion = p.fechaNotificacion() != null ? p.fechaNotificacion() : r.fechaHora().toLocalDate();
            String descPlazo = limpiar(p.actuacionRequerida()) != null ? limpiar(p.actuacionRequerida())
                    : (limpiar(r.proximaAccion()) != null ? limpiar(r.proximaAccion()) : "Seguimiento de " + limpiar(r.asunto()));
            plazoId = repository.insertarPlazo(actuacionId, r.expedienteId(), tp.id(), descPlazo,
                    notificacion, dias, computo, riesgo, r.responsableId(), p.revisorId(),
                    p.requiereRevisionProfesional(), limpiar(p.motivoRevision()),
                    limpiar(p.actuacionRequerida()), usuarioId);
            alertas += crearAlertasPlazo(actuacionId, plazoId, r.expedienteId(), r.responsableId(), descPlazo, riesgo);
        }

        boolean generarTarea = tipo.generaTarea() || generar(r.tarea());
        if (generarTarea) {
            TareaAutomatica t = r.tarea();
            String titulo = t == null ? null : limpiar(t.titulo());
            if (titulo == null) titulo = limpiar(r.proximaAccion());
            if (titulo == null) titulo = "Seguimiento: " + limpiar(r.asunto());
            String prioridad = t == null ? null : vacioANull(t.prioridad());
            prioridad = prioridad == null ? (plazoId != null ? "ALTA" : "MEDIA") : normalizar(prioridad);
            if (!PRIORIDADES.contains(prioridad)) throw new ParametroInvalidoException("Prioridad de tarea invalida");
            LocalDateTime fechaLimite = t == null ? null : t.fechaLimite();
            if (fechaLimite == null && plazoId != null) {
                fechaLimite = repository.fechaVencimientoPlazo(plazoId).atTime(17, 0);
            }
            if (fechaLimite == null) fechaLimite = r.fechaHora().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0);
            Long supervisor = t == null ? null : t.supervisorId();
            if (supervisor != null && !repository.usuarioValido(supervisor)) throw new ParametroInvalidoException("Supervisor no valido");
            tareaId = repository.insertarTarea(actuacionId, r.expedienteId(), plazoId, titulo,
                    t == null ? limpiar(r.descripcion()) : limpiar(t.descripcion()), r.responsableId(), supervisor,
                    prioridad, fechaLimite, t == null ? null : t.tiempoEstimadoMinutos(), usuarioId);
        }

        boolean generarEvento = tipo.generaEventoAgenda() || generar(r.evento());
        if (generarEvento) {
            EventoAutomatico ev = r.evento();
            String tipoEvento = ev == null || vacioANull(ev.tipo()) == null ? tipoEvento(tipo) : normalizar(ev.tipo());
            if (!EVENTOS.contains(tipoEvento)) throw new ParametroInvalidoException("Tipo de evento de agenda invalido");
            LocalDateTime inicio = ev == null ? null : ev.fechaInicio();
            if (inicio == null) inicio = r.fechaHora();
            LocalDateTime fin = ev == null ? null : ev.fechaFin();
            if (fin == null) fin = inicio.plusHours(1);
            if (fin.isBefore(inicio)) throw new ParametroInvalidoException("La fecha fin del evento no puede ser anterior a la fecha inicio");
            String modalidad = ev == null || vacioANull(ev.modalidad()) == null ? "PRESENCIAL" : normalizar(ev.modalidad());
            if (!MODALIDADES.contains(modalidad)) throw new ParametroInvalidoException("Modalidad de evento invalida");
            String titulo = ev == null ? null : limpiar(ev.titulo());
            if (titulo == null) titulo = tipo.nombre() + ": " + limpiar(r.asunto());
            eventoId = repository.insertarEvento(actuacionId, r.expedienteId(), plazoId, tipoEvento, titulo,
                    ev == null ? limpiar(r.descripcion()) : limpiar(ev.descripcion()), inicio, fin, modalidad,
                    ev == null ? null : limpiar(ev.ubicacion()), ev == null ? null : limpiar(ev.enlaceVirtual()),
                    ev == null ? 60 : ev.recordatorioMinutos(), usuarioId);
        }

        if (r.documentoExistenteId() != null) repository.vincularDocumento(r.documentoExistenteId(), actuacionId);

        boolean generarDocumento = tipo.requiereDocumento() || generar(r.documentoPendiente());
        if (generarDocumento) {
            DocumentoPendienteAutomatico d = r.documentoPendiente();
            String nombre = d == null ? null : limpiar(d.nombre());
            if (nombre == null) nombre = "Pendiente - " + tipo.nombre() + " - " + limpiar(r.asunto());
            String categoria = d == null || vacioANull(d.categoria()) == null ? categoriaDocumento(tipo) : normalizar(d.categoria());
            if (!CATEGORIAS_DOC.contains(categoria)) throw new ParametroInvalidoException("Categoria de documento invalida");
            documentoPendienteId = repository.insertarDocumentoPendiente(actuacionId, r.expedienteId(), tareaId, nombre,
                    d == null ? "Documento pendiente generado desde la actuacion." : limpiar(d.descripcion()), categoria,
                    d != null && d.confidencial(), usuarioId);
        }

        if (generar(r.alerta())) {
            AlertaAutomatica a = r.alerta();
            if (a.fechaProgramada() == null) throw new ParametroInvalidoException("Indica la fecha de la alerta");
            String severidad = a.severidad() == null || a.severidad().isBlank() ? "MEDIA" : normalizar(a.severidad());
            if (!SEVERIDADES.contains(severidad)) throw new ParametroInvalidoException("Severidad de alerta invalida");
            String mensaje = limpiar(a.mensaje());
            if (mensaje == null) mensaje = "Seguimiento de actuacion: " + limpiar(r.asunto());
            repository.insertarAlerta(actuacionId, null, r.expedienteId(), r.responsableId(), "GENERICA", null,
                    mensaje, severidad, a.fechaProgramada());
            alertas++;
        }

        repository.actualizarUltimaActuacion(r.expedienteId(), r.fechaHora());

        return new CrearResponse(repository.uuid("actuaciones", actuacionId),
                tareaId == null ? null : repository.uuid("tareas", tareaId),
                plazoId == null ? null : repository.uuid("plazos", plazoId),
                eventoId == null ? null : repository.uuid("eventos_agenda", eventoId),
                documentoPendienteId == null ? null : repository.uuid("documentos", documentoPendienteId),
                alertas);
    }

    private void validarBase(CrearRequest r) {
        if (!repository.expedienteExiste(r.expedienteId())) throw new ParametroInvalidoException("El expediente no existe o no esta disponible");
        if (!repository.usuarioValido(r.responsableId())) throw new ParametroInvalidoException("El responsable no existe o esta inactivo");
        if (r.documentoExistenteId() != null && !repository.documentoDisponible(r.documentoExistenteId(), r.expedienteId())) {
            throw new ParametroInvalidoException("El documento seleccionado no pertenece al expediente, ya esta vinculado o no esta disponible");
        }
    }

    private int crearAlertasPlazo(Long actuacionId, Long plazoId, Long expedienteId, Long responsableId,
            String descripcion, String riesgo) {
        LocalDate vencimiento = repository.fechaVencimientoPlazo(plazoId);
        if (vencimiento == null) return 0;
        List<Integer> dias = new ArrayList<>();
        dias.addAll(parseEnteros(repository.valorConfig("alertas_dias_previos"), List.of(15, 10, 7, 5, 3, 1, 0)));
        dias.addAll(parseEnteros(repository.valorConfig("alertas_dias_posteriores"), List.of(-1, -3)));
        int creadas = 0;
        LocalDateTime ahora = LocalDateTime.now();
        for (Integer d : dias.stream().distinct().toList()) {
            LocalDateTime fecha = vencimiento.minusDays(d).atTime(LocalTime.of(9, 0));
            if (fecha.isBefore(ahora.minusMinutes(1))) continue;
            String tipo = d < 0 ? "PLAZO_VENCIDO" : "PLAZO_PROXIMO";
            String mensaje = d == 0
                    ? descripcion + " vence hoy."
                    : d > 0 ? descripcion + " vence en " + d + " dia(s)."
                    : descripcion + " vencio hace " + Math.abs(d) + " dia(s).";
            String severidad = "CRITICO".equals(riesgo) ? "CRITICA" : "ALTO".equals(riesgo) ? "ALTA" : "MEDIA";
            repository.insertarAlerta(actuacionId, plazoId, expedienteId, responsableId, tipo, d, mensaje, severidad, fecha);
            creadas++;
        }
        return creadas;
    }

    private static List<Integer> parseEnteros(String texto, List<Integer> defecto) {
        if (texto == null || texto.isBlank()) return defecto;
        List<Integer> r = new ArrayList<>();
        Matcher m = Pattern.compile("-?\\d+").matcher(texto);
        while (m.find()) r.add(Integer.parseInt(m.group()));
        return r.isEmpty() ? defecto : r;
    }

    private static boolean generar(TareaAutomatica a) { return a != null && a.generar(); }
    private static boolean generar(PlazoAutomatico a) { return a != null && a.generar(); }
    private static boolean generar(EventoAutomatico a) { return a != null && a.generar(); }
    private static boolean generar(AlertaAutomatica a) { return a != null && a.generar(); }
    private static boolean generar(DocumentoPendienteAutomatico a) { return a != null && a.generar(); }
    private static PlazoAutomatico exigirPlazo(PlazoAutomatico p) {
        if (p == null || p.tipoPlazoId() == null) throw new ParametroInvalidoException("Este tipo de actuacion genera plazo: selecciona el tipo de plazo");
        return p;
    }

    private static String tipoEvento(TipoActuacionOpcion t) {
        return switch (t.categoria()) {
            case "AUDIENCIA" -> "AUDIENCIA";
            case "DILIGENCIA" -> "DILIGENCIA";
            case "COMUNICACION" -> t.codigo().contains("LLAMADA") ? "LLAMADA" : "REUNION_CLIENTE";
            default -> "PRESENTACION";
        };
    }

    private static String categoriaDocumento(TipoActuacionOpcion t) {
        if (t.codigo().contains("DEMANDA")) return "DEMANDA";
        if (t.codigo().contains("CONTESTACION")) return "CONTESTACION";
        if (t.codigo().contains("RECURSO")) return "RECURSO";
        if ("NOTIFICACION".equals(t.categoria())) return "NOTIFICACION";
        if ("ESCRITO".equals(t.categoria())) return "ESCRITO";
        return "OTRO";
    }

    private static String valorO(String valor, String defecto) {
        return valor == null || valor.isBlank() ? normalizar(defecto) : normalizar(valor);
    }
    private static String normalizar(String v) { return v == null ? null : v.trim().toUpperCase(Locale.ROOT); }
    private static String limpiar(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private static String vacioANull(String v) { return limpiar(v); }
}
