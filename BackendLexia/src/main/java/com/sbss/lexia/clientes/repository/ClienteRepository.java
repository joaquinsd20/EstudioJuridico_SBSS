package com.sbss.lexia.clientes.repository;

import com.sbss.lexia.clientes.entity.Cliente;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Regla de CLAUDE.md: ningun metodo de este repositorio consulta sin
 * estudio_id. Si el nombre del metodo no lo contiene ({@code EstudioId}) y no
 * recibe una {@link Specification} (que en este modulo siempre compone
 * {@code ClienteSpecification.delEstudio} primero), se rechaza en revision. Es
 * verificable leyendo solo la firma, sin entender la implementacion.
 *
 * Se descartan los {@code @Filter} de Hibernate para esto: por defecto NO se
 * aplican a {@code findById()} ni a consultas nativas — exactamente las rutas
 * que un metodo nuevo usaria por costumbre, y la peor propiedad posible en un
 * mecanismo de aislamiento multi-estudio.
 */
public interface ClienteRepository
        extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    /**
     * Se sobrescribe SOLO para adjuntar el EntityGraph: sin el, cada fila de la
     * pagina dispararia una consulta aparte al leer abogadoResponsable (N+1),
     * porque esa asociacion es LAZY. Sigue siendo una asociacion ToOne, asi que
     * el EntityGraph no rompe la paginacion (solo las colecciones lo harian).
     */
    @Override
    @EntityGraph(attributePaths = "abogadoResponsable")
    Page<Cliente> findAll(Specification<Cliente> spec, Pageable pageable);

    Optional<Cliente> findByUuidPublicoAndEstudioId(String uuidPublico, Integer estudioId);

    Optional<Cliente> findByEstudioIdAndTipoDocumentoAndNumeroDocumento(
            Integer estudioId, String tipoDocumento, String numeroDocumento);

    /**
     * Cuenta expedientes por cliente, para los ids de UNA pagina. Nativa a
     * proposito: la entidad Expediente todavia no existe en el backend. Se
     * llama una vez por pagina de resultados (nunca una vez por fila): dos
     * consultas por pagina en total, jamas N+1.
     *
     * Lleva estudioId ademas de la lista de ids como defensa en profundidad,
     * aunque los ids ya vengan de una consulta acotada por estudio.
     */
    @Query(value = """
            SELECT cliente_id AS clienteId, COUNT(*) AS total
              FROM expedientes
             WHERE activo = 1 AND estudio_id = :estudioId AND cliente_id IN (:ids)
             GROUP BY cliente_id
            """, nativeQuery = true)
    List<ConteoExpedientes> contarExpedientesPorCliente(
            @Param("estudioId") Integer estudioId, @Param("ids") Collection<Long> ids);

    /** Proyeccion del conteo de expedientes por cliente. */
    interface ConteoExpedientes {
        Long getClienteId();

        Long getTotal();
    }

    /**
     * KPIs de la cabecera de la pantalla de Clientes. UNA sola consulta de
     * agregacion sobre TODO el estudio: los indicadores nunca se calculan
     * sobre la pagina actual, porque eso daria "4 activos de 10" en vez del
     * total real.
     *
     * Los SUM() se envuelven en CAST(...AS SIGNED): MySQL devuelve DECIMAL
     * para SUM() de una expresion entera, y forzar el tipo evita depender de
     * que el conversor de Spring Data resuelva DECIMAL -> Long por su cuenta.
     * Los COALESCE(...,0) cubren el caso de un estudio sin clientes: SUM()
     * sobre cero filas es NULL, no 0, y los campos de la proyeccion son
     * primitivos en el service.
     */
    @Query(value = """
            SELECT
                COUNT(*) AS total,
                CAST(COALESCE(SUM(estado = 'ACTIVO'), 0) AS SIGNED) AS activos,
                CAST(COALESCE(SUM(tipo_persona = 'NATURAL'), 0) AS SIGNED) AS naturales,
                CAST(COALESCE(SUM(tipo_persona = 'JURIDICA'), 0) AS SIGNED) AS juridicas,
                CAST(COALESCE(SUM(tipo_persona = 'ENTIDAD_PUBLICA'), 0) AS SIGNED) AS entidadesPublicas,
                CAST(COALESCE(SUM(estado = 'INACTIVO'), 0) AS SIGNED) AS inactivos,
                CAST(COALESCE(SUM(fecha_creacion >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')), 0) AS SIGNED) AS nuevosEsteMes,
                (SELECT COUNT(DISTINCT e.cliente_id) FROM expedientes e
                  WHERE e.activo = 1 AND e.estudio_id = :estudioId) AS conExpedientes
              FROM clientes
             WHERE estudio_id = :estudioId AND activo = 1
            """, nativeQuery = true)
    ResumenProjection resumen(@Param("estudioId") Integer estudioId);

    /** Proyeccion de los KPIs de la cabecera. */
    interface ResumenProjection {
        Long getTotal();

        Long getActivos();

        Long getNaturales();

        Long getJuridicas();

        Long getEntidadesPublicas();

        Long getInactivos();

        Long getNuevosEsteMes();

        Long getConExpedientes();
    }
}
