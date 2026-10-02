package com.sbss.lexia.plazos.service;

import com.sbss.lexia.catalogos.service.CalendarioJudicialService;
import com.sbss.lexia.plazos.dto.PlazoDtos.*;
import com.sbss.lexia.plazos.repository.PlazoRepositoryJdbc;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.util.CalculadoraPlazos;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlazoService {

    private static final Set<String> ESTADOS = Set.of("PENDIENTE_CALCULO", "CALCULADO", "EN_PREPARACION", "EN_REVISION",
            "LISTO_PRESENTAR", "PRESENTADO", "CUMPLIDO", "VENCIDO", "SUSPENDIDO", "CANCELADO");
    private static final Set<String> COMPUTOS = Set.of("HABILES", "CALENDARIO");
    private static final Set<String> RIESGOS = Set.of("BAJO", "MEDIO", "ALTO", "CRITICO");
    private static final Set<String> ORIGENES = Set.of("MANUAL", "RESOLUCION", "NOTIFICACION", "ACTUACION", "MONITOR_JUDICIAL");
    private static final List<Integer> ALERTAS_DEFECTO = List.of(15, 10, 7, 5, 3, 1, 0, -1, -3);

    private final PlazoRepositoryJdbc repository;
    private final CalendarioJudicialService calendarioJudicialService;

    @Transactional(readOnly = true)
    public List<PlazoLista> listar(String texto, Long expedienteId, Long responsableId, Long tipoPlazoId, String estado,
            String riesgo, String computo, Boolean requiereRevision, LocalDate desde, LocalDate hasta,
            LocalDate fechaNotificacion, int limite, int desplazamiento) {
        String e = normalizarOpcional(estado);
        String r = normalizarOpcional(riesgo);
        String c = normalizarOpcional(computo);
        if (e != null && !ESTADOS.contains(e)) throw new ParametroInvalidoException("Estado de plazo invalido");
        if (r != null && !RIESGOS.contains(r)) throw new ParametroInvalidoException("Nivel de riesgo invalido");
        if (c != null && !COMPUTOS.contains(c)) throw new ParametroInvalidoException("Tipo de computo invalido");
        if (desde != null && hasta != null && hasta.isBefore(desde)) throw new ParametroInvalidoException("La fecha hasta no puede ser anterior a la fecha desde");
        return repository.listar(texto, expedienteId, responsableId, tipoPlazoId, e, r, c,
                requiereRevision, desde, hasta, fechaNotificacion, limite, desplazamiento);
    }

    @Transactional(readOnly = true)
    public Analitica analitica() { return repository.analitica(); }

    @Transactional(readOnly = true)
    public Resumen resumen() { return repository.resumen(); }

    @Transactional(readOnly = true)
    public Catalogos catalogos() { return repository.catalogos(); }

    @Transactional(readOnly = true)
    public Relaciones relaciones(Long expedienteId) {
        if (!repository.expedienteExiste(expedienteId)) throw new ParametroInvalidoException("El expediente no existe o no esta disponible");
        return repository.relaciones(expedienteId);
    }

    @Transactional(readOnly = true)
    public PlazoDetalle detalle(String uuid) {
        PlazoDetalle detalle = repository.detalle(uuid);
        if (detalle == null) throw new RecursoNoEncontradoException("Plazo no encontrado");
        return detalle;
    }

    @Transactional(readOnly = true)
    public Calculo calcular(CalcularRequest r) {
        if (!repository.expedienteExiste(r.expedienteId())) throw new ParametroInvalidoException("El expediente no existe o no esta disponible");
        String computo = normalizar(r.tipoComputo());
        if (!COMPUTOS.contains(computo)) throw new ParametroInvalidoException("Tipo de computo invalido");
        return calcularInterno(r.expedienteId(), r.fechaNotificacion(), r.diasPlazo(), computo);
    }

    @Transactional
    public PlazoDetalle crear(GuardarRequest r) {
        Validado v = validarYCalcular(r);
        Long id = repository.insertar(r, v.inicio(), v.vencimiento(), v.interna(), v.requiereRevision(), v.motivoRevision(), v.estado());
        sincronizarDependencias(id, r, v);
        String uuid = uuidPorId(id);
        return detalle(uuid);
    }

    @Transactional
    public PlazoDetalle actualizar(String uuid, GuardarRequest r) {
        Long id = repository.idPorUuid(uuid);
        if (id == null) throw new RecursoNoEncontradoException("Plazo no encontrado");
        Validado v = validarYCalcular(r);
        repository.actualizar(id, r, v.inicio(), v.vencimiento(), v.interna(), v.requiereRevision(), v.motivoRevision(), v.estado());
        sincronizarDependencias(id, r, v);
        return detalle(uuid);
    }

    @Transactional
    public PlazoDetalle cambiarEstado(String uuid, String estado) {
        Long id = repository.idPorUuid(uuid);
        if (id == null) throw new RecursoNoEncontradoException("Plazo no encontrado");
        String e = normalizar(estado);
        if (!ESTADOS.contains(e)) throw new ParametroInvalidoException("Estado de plazo invalido");
        repository.cambiarEstado(id, e);
        return detalle(uuid);
    }

    private Validado validarYCalcular(GuardarRequest r) {
        if (!repository.expedienteExiste(r.expedienteId())) throw new ParametroInvalidoException("El expediente no existe o no esta disponible");
        if (!repository.usuarioValido(r.responsableId())) throw new ParametroInvalidoException("Responsable no valido");
        if (!repository.usuarioValido(r.revisorId())) throw new ParametroInvalidoException("Revisor no valido");
        if (!repository.resolucionValida(r.resolucionId(), r.expedienteId())) throw new ParametroInvalidoException("La resolucion no pertenece al expediente");
        if (!repository.actuacionValida(r.actuacionOrigenId(), r.expedienteId())) throw new ParametroInvalidoException("La actuacion no pertenece al expediente");
        if (!repository.documentoValido(r.documentoId(), r.expedienteId())) throw new ParametroInvalidoException("El documento no pertenece al expediente");
        TipoPlazoOpcion tipo = repository.tipoPlazo(r.tipoPlazoId());
        if (tipo == null) throw new ParametroInvalidoException("Selecciona un tipo de plazo valido");

        String origen = normalizar(r.origen());
        String computo = normalizar(r.tipoComputo());
        String riesgo = normalizar(r.nivelRiesgo());
        String estado = normalizarOpcional(r.estado());
        if (!ORIGENES.contains(origen)) throw new ParametroInvalidoException("Origen del plazo invalido");
        if (!COMPUTOS.contains(computo)) throw new ParametroInvalidoException("Tipo de computo invalido");
        if (!RIESGOS.contains(riesgo)) throw new ParametroInvalidoException("Nivel de riesgo invalido");
        if (estado == null) estado = "CALCULADO";
        if (!ESTADOS.contains(estado)) throw new ParametroInvalidoException("Estado de plazo invalido");
        if ("RESOLUCION".equals(origen) && r.resolucionId() == null) throw new ParametroInvalidoException("Selecciona la resolucion que origina el plazo");
        if ("ACTUACION".equals(origen) && r.actuacionOrigenId() == null) throw new ParametroInvalidoException("Selecciona la actuacion que origina el plazo");

        Calculo calculo = calcularInterno(r.expedienteId(), r.fechaNotificacion(), r.diasPlazo(), computo);
        LocalDate interna = r.fechaRevisionInterna() != null ? r.fechaRevisionInterna() : calculo.fechaRevisionInternaSugerida();
        if (interna != null && interna.isAfter(calculo.fechaVencimiento())) throw new ParametroInvalidoException("La fecha interna de revision no puede ser posterior al vencimiento oficial");

        boolean requiere = r.requiereRevisionProfesional() || calculo.requiereRevisionSugerida();
        String motivo = limpiar(r.motivoRevision());
        if (calculo.requiereRevisionSugerida()) {
            String automatico = "Calendario judicial con incidencias: " + String.join("; ", calculo.advertencias());
            motivo = unirMotivos(motivo, automatico);
        }
        if (requiere && motivo == null) throw new ParametroInvalidoException("Indica el motivo de la revision profesional del plazo");
        if (motivo != null && motivo.length() > 250) motivo = motivo.substring(0, 250);

        return new Validado(calculo.fechaInicioComputo(), calculo.fechaVencimiento(), interna, requiere, motivo, estado);
    }

    private Calculo calcularInterno(Long expedienteId, LocalDate fechaNotificacion, Integer dias, String computo) {
        if (fechaNotificacion == null) throw new ParametroInvalidoException("Indica la fecha de notificacion");
        if (dias == null || dias < 1) throw new ParametroInvalidoException("Los dias del plazo deben ser mayores a cero");
        String ambito = repository.distritoJudicial(expedienteId);
        CalculadoraPlazos calc = calendarioJudicialService.calculadora();
        LocalDate inicio = calc.siguienteDiaHabil(fechaNotificacion, ambito);
        LocalDate vencimiento = calc.calcularVencimiento(inicio, dias, computo, ambito);
        int colchon = repository.valorConfigNumero("plazo_colchon_interno_dias", 2);
        LocalDate interna = vencimiento.minusDays(Math.max(0, colchon));
        List<String> incidencias = repository.incidenciasCalendario(fechaNotificacion, vencimiento, ambito);
        return new Calculo(inicio, vencimiento, interna, ambito, !incidencias.isEmpty(), incidencias);
    }

    private void sincronizarDependencias(Long plazoId, GuardarRequest r, Validado v) {
        repository.eliminarAlertasProgramadas(plazoId);
        List<Integer> dias = r.alertasDias() == null || r.alertasDias().isEmpty() ? ALERTAS_DEFECTO : r.alertasDias();
        LinkedHashSet<Integer> unicos = new LinkedHashSet<>();
        for (Integer d : dias) {
            if (d != null && d >= -365 && d <= 365) unicos.add(d);
        }
        LocalDateTime ahora = LocalDateTime.now(CalculadoraPlazos.ZONA);
        for (Integer d : unicos) {
            LocalDateTime programada = v.vencimiento().minusDays(d).atTime(LocalTime.of(9, 0));
            if (programada.isBefore(ahora)) {
                if (programada.toLocalDate().equals(ahora.toLocalDate())) programada = ahora.plusMinutes(1);
                else continue;
            }
            String mensaje = d == 0 ? r.descripcion().trim() + " vence hoy."
                    : d > 0 ? r.descripcion().trim() + " vence en " + d + " dia(s)."
                    : r.descripcion().trim() + " vencio hace " + Math.abs(d) + " dia(s).";
            repository.insertarAlerta(plazoId, r.expedienteId(), r.responsableId(), d, mensaje, severidad(r.nivelRiesgo()), programada);
        }
        repository.sincronizarEventoAgenda(plazoId, r.expedienteId(), r.actuacionOrigenId(), r.descripcion().trim(), v.vencimiento(), v.requiereRevision());
    }

    private String uuidPorId(Long id) {
        String uuid = repository.uuidPorId(id);
        if (uuid == null) throw new IllegalStateException("No se encontro el plazo recien creado");
        return uuid;
    }

    private static String severidad(String riesgo) {
        return switch (normalizar(riesgo)) {
            case "CRITICO" -> "CRITICA";
            case "ALTO" -> "ALTA";
            case "BAJO" -> "BAJA";
            default -> "MEDIA";
        };
    }

    private static String unirMotivos(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + "; " + b;
    }

    private static String normalizar(String v) { return v == null ? null : v.trim().toUpperCase(Locale.ROOT); }
    private static String normalizarOpcional(String v) { return limpiar(v) == null ? null : normalizar(v); }
    private static String limpiar(String v) { return v == null || v.isBlank() ? null : v.trim(); }

    private record Validado(LocalDate inicio, LocalDate vencimiento, LocalDate interna,
            boolean requiereRevision, String motivoRevision, String estado) {}
}
