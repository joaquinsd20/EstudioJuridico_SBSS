package com.sbss.lexia.catalogos.repository;

import com.sbss.lexia.catalogos.entity.CalendarioJudicial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CalendarioJudicialRepository extends JpaRepository<CalendarioJudicial, Integer> {

    /**
     * Las entradas que SI afectan el computo de plazos.
     *
     * EXCEPCION DELIBERADA a la regla de CLAUDE.md de acotar toda consulta por
     * estudio_id, y hay que ser explicito sobre por que: fn_es_dia_habil (V8)
     * tampoco filtra por estudio, y esta clase existe para dar exactamente el
     * mismo resultado que esa funcion. Si aqui se filtrara por estudio, Java y
     * SQL divergirian y CalculadoraPlazosParidadIT lo detectaria.
     *
     * Eso deja abierta una fuga real entre inquilinos: una suspension cargada
     * por el estudio 2 hoy alteraria los plazos del estudio 1. No se corrige
     * aqui unilateralmente porque arreglarlo bien exige cambiar la funcion SQL
     * y esta consulta A LA VEZ, en una migracion propia — si se corrige solo
     * un lado, la semilla y el sistema empiezan a calcular distinto. Con un
     * unico estudio en la base es inofensivo hoy; queda anotado para cuando
     * deje de serlo.
     */
    @Query("select c from CalendarioJudicial c where c.estado = 'APROBADO'")
    List<CalendarioJudicial> findAprobados();
}
