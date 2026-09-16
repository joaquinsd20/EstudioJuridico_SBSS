package com.sbss.lexia.catalogos.service;

import com.sbss.lexia.catalogos.entity.CalendarioJudicial;
import com.sbss.lexia.catalogos.repository.CalendarioJudicialRepository;
import com.sbss.lexia.shared.util.CalculadoraPlazos;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma una CalculadoraPlazos con el calendario judicial vigente.
 *
 * Separar esto de la calculadora no es ceremonia: deja la aritmetica del
 * computo probable sin base de datos, y evita el error clasico de consultar
 * una vez por dia dentro del bucle. Aqui se lee el calendario UNA vez y se le
 * entrega entero.
 */
@Service
@RequiredArgsConstructor
public class CalendarioJudicialService {

    private final CalendarioJudicialRepository calendarioJudicialRepository;

    /**
     * Sin cache, a proposito. Son unas decenas de filas y el indice
     * ix_calendario_vigente esta hecho para esta consulta, asi que el costo es
     * despreciable — pero el riesgo de cachear no lo es: el flujo de
     * aprobacion existe justo para que una suspension recien aprobada por un
     * socio surta efecto de inmediato. Una cache mal invalidada significaria
     * seguir calculando con el calendario viejo, que es exactamente el fallo
     * que este modulo existe para evitar.
     */
    @Transactional(readOnly = true)
    public CalculadoraPlazos calculadora() {
        List<CalculadoraPlazos.RangoInhabil> rangos =
                calendarioJudicialRepository.findAprobados().stream()
                        .map(CalendarioJudicialService::aRango)
                        .toList();
        return new CalculadoraPlazos(rangos);
    }

    private static CalculadoraPlazos.RangoInhabil aRango(CalendarioJudicial entrada) {
        return new CalculadoraPlazos.RangoInhabil(
                entrada.getFechaInicio(), entrada.getFechaFin(), entrada.getAmbito());
    }
}
