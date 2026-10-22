package com.sbss.lexia.catalogos.service;

import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.ActualizarEstudioRequest;
import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.ActualizarParametrosRequest;
import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.EstudioResponse;
import com.sbss.lexia.catalogos.controller.ConfiguracionAdminDtos.ParametrosResponse;
import com.sbss.lexia.catalogos.entity.Configuracion;
import com.sbss.lexia.catalogos.entity.Estudio;
import com.sbss.lexia.catalogos.repository.ConfiguracionRepository;
import com.sbss.lexia.catalogos.repository.EstudioRepository;
import com.sbss.lexia.shared.exception.ParametroInvalidoException;
import com.sbss.lexia.shared.exception.RecursoNoEncontradoException;
import com.sbss.lexia.shared.security.SesionActual;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operaciones administrativas acotadas siempre al estudio autenticado. */
@Service
@RequiredArgsConstructor
public class ConfiguracionAdminService {

    private final EstudioRepository estudioRepository;
    private final ConfiguracionRepository configuracionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ConfiguracionAuditoriaService auditoria;

    @Transactional(readOnly = true)
    public EstudioResponse obtenerEstudio() {
        return mapear(estudioActual());
    }

    @Transactional
    public EstudioResponse actualizarEstudio(ActualizarEstudioRequest request, HttpServletRequest contexto) {
        Estudio estudio = estudioActual();
        EstudioResponse antes = mapear(estudio);

        String ruc = request.ruc().trim();
        if (estudioRepository.existsByRucAndIdNot(ruc, estudio.getId())) {
            throw new ParametroInvalidoException("Ese RUC ya está registrado en otro estudio.");
        }
        validarZonaHoraria(request.zonaHoraria());

        estudio.setRazonSocial(request.razonSocial().trim());
        estudio.setNombreComercial(limpiar(request.nombreComercial()));
        estudio.setRuc(ruc);
        estudio.setDireccion(limpiar(request.direccion()));
        estudio.setTelefono(limpiar(request.telefono()));
        estudio.setEmail(limpiar(request.email()));
        estudio.setZonaHoraria(request.zonaHoraria().trim());
        estudio.setMonedaBase(request.monedaBase().trim().toUpperCase(Locale.ROOT));
        Estudio guardado = estudioRepository.saveAndFlush(estudio);
        EstudioResponse despues = mapear(guardado);

        if (!antes.equals(despues)) {
            auditoria.registrarCambio("estudios", guardado.getId(),
                    "Actualización de datos institucionales del estudio", antes.toString(), despues.toString(), contexto);
        }
        return despues;
    }

    @Transactional(readOnly = true)
    public ParametrosResponse obtenerParametros() {
        Integer estudioId = SesionActual.estudioId();
        String dominio = valor(estudioId, "dominio_correo_interno", "lexia.com");
        int intentos = entero(estudioId, "intentos_login_maximos", 5);
        int colchon = entero(estudioId, "plazo_colchon_interno_dias", 2);
        List<Integer> alertas = listaEnteros(valor(estudioId, "alertas_dias_previos", "[15,10,7,5,3,1,0]"));
        return new ParametrosResponse(dominio, intentos, colchon, alertas);
    }

    @Transactional
    public ParametrosResponse actualizarParametros(ActualizarParametrosRequest request, HttpServletRequest contexto) {
        Integer estudioId = SesionActual.estudioId();
        List<Integer> alertas = validarAlertas(request.alertasDiasPrevios());
        String dominio = request.dominioCorreoInterno().trim().toLowerCase(Locale.ROOT);
        if (!dominio.contains(".") || dominio.contains("..")) {
            throw new ParametroInvalidoException("El dominio debe tener un formato válido, por ejemplo estudio.pe.");
        }

        guardar(estudioId, "dominio_correo_interno", dominio,
                "TEXTO", "Dominio permitido para cuentas internas", contexto);
        guardar(estudioId, "intentos_login_maximos", String.valueOf(request.intentosLoginMaximos()),
                "NUMERO", "Intentos de acceso fallidos antes de bloquear la cuenta", contexto);
        guardar(estudioId, "plazo_colchon_interno_dias", String.valueOf(request.plazoColchonInternoDias()),
                "NUMERO", "Días de anticipación para la revisión interna del plazo", contexto);
        guardar(estudioId, "alertas_dias_previos", alertas.toString(),
                "JSON", "Días de aviso previo configurados para las actuaciones", contexto);

        return obtenerParametros();
    }

    private void guardar(Integer estudioId, String clave, String nuevoValor, String tipo, String descripcion,
            HttpServletRequest contexto) {
        Configuracion registro = configuracionRepository.findByEstudioIdAndClave(estudioId, clave)
                .orElseGet(() -> {
                    Configuracion nueva = new Configuracion();
                    nueva.setEstudioId(estudioId);
                    nueva.setClave(clave);
                    nueva.setEditablePorUsuario(false);
                    return nueva;
                });
        String anterior = registro.getValor();
        if (nuevoValor.equals(anterior)) return;

        registro.setValor(nuevoValor);
        registro.setTipoDato(tipo);
        registro.setDescripcion(descripcion);
        registro.setEditablePorUsuario(false);
        registro = configuracionRepository.saveAndFlush(registro);
        jdbcTemplate.update("UPDATE configuracion SET usuario_modificacion_id = ?, fecha_modificacion = CURRENT_TIMESTAMP(3) WHERE id = ?",
                SesionActual.usuarioId(), registro.getId());
        auditoria.registrarCambio("configuracion", registro.getId().longValue(),
                "Actualización del parámetro " + clave, anterior, nuevoValor, contexto);
    }

    private Estudio estudioActual() {
        return estudioRepository.findById(SesionActual.estudioId())
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .orElseThrow(() -> new RecursoNoEncontradoException("El estudio de la sesión no está disponible."));
    }

    private EstudioResponse mapear(Estudio e) {
        return new EstudioResponse(e.getId(), e.getRazonSocial(), e.getNombreComercial(), e.getRuc(),
                e.getDireccion(), e.getTelefono(), e.getEmail(), e.getLogoUrl(), e.getZonaHoraria(), e.getMonedaBase());
    }

    private void validarZonaHoraria(String zona) {
        try {
            ZoneId.of(zona.trim());
        } catch (DateTimeException ex) {
            throw new ParametroInvalidoException("La zona horaria no es válida.");
        }
    }

    private String valor(Integer estudioId, String clave, String fallback) {
        return configuracionRepository.findByEstudioIdAndClave(estudioId, clave)
                .map(Configuracion::getValor).filter(v -> v != null && !v.isBlank()).orElse(fallback);
    }

    private int entero(Integer estudioId, String clave, int fallback) {
        try {
            return Integer.parseInt(valor(estudioId, clave, String.valueOf(fallback)).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private List<Integer> listaEnteros(String valor) {
        try {
            String contenido = valor.trim().replace("[", "").replace("]", "");
            if (contenido.isBlank()) return List.of();
            List<Integer> numeros = new ArrayList<>();
            for (String parte : contenido.split(",")) numeros.add(Integer.parseInt(parte.trim()));
            return validarAlertas(numeros);
        } catch (RuntimeException ex) {
            return List.of(15, 10, 7, 5, 3, 1, 0);
        }
    }

    private List<Integer> validarAlertas(List<Integer> dias) {
        if (dias == null || dias.isEmpty() || dias.size() > 20) {
            throw new ParametroInvalidoException("Configura entre 1 y 20 anticipaciones de alerta.");
        }
        if (dias.stream().anyMatch(d -> d == null || d < 0 || d > 365)) {
            throw new ParametroInvalidoException("Los días de alerta deben estar entre 0 y 365.");
        }
        Set<Integer> unicos = new LinkedHashSet<>(dias);
        if (unicos.size() != dias.size()) {
            throw new ParametroInvalidoException("No repitas los días de alerta.");
        }
        return unicos.stream().sorted(Comparator.reverseOrder()).toList();
    }

    private String limpiar(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }
}
