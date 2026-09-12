package com.sbss.lexia.catalogos.service;

import com.sbss.lexia.catalogos.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura tipada de la tabla `configuracion`.
 *
 * Todo valor tiene un fallback: la configuracion es un ajuste operativo, no una
 * dependencia estructural. Si la fila no existe o el estudio todavia no cargo
 * ese parametro, el sistema sigue funcionando con el valor por defecto en vez
 * de romperse.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository configuracionRepository;

    @Transactional(readOnly = true)
    public int obtenerEntero(Integer estudioId, String clave, int valorPorDefecto) {
        return configuracionRepository.findByEstudioIdAndClave(estudioId, clave)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getValor().trim());
                    } catch (NumberFormatException e) {
                        // Un valor mal cargado en configuracion no debe tumbar el
                        // login; se ignora y se usa el valor por defecto.
                        return valorPorDefecto;
                    }
                })
                .orElse(valorPorDefecto);
    }
}
