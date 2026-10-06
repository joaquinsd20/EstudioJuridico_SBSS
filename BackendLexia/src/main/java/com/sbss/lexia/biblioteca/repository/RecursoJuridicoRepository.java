package com.sbss.lexia.biblioteca.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sbss.lexia.biblioteca.dto.RecursoListaResponse;
import com.sbss.lexia.biblioteca.entity.RecursoJuridico;

@Repository
public interface RecursoJuridicoRepository extends JpaRepository<RecursoJuridico, Long> {

    Optional<RecursoJuridico> findByUuidPublicoAndEstudioIdAndActivoTrue(
            String uuidPublico,
            Integer estudioId);

    /**
     * Consulta paginada y proyectada. La tabla del frontend recibe únicamente
     * las columnas que realmente necesita; ruta_archivo e ids internos no salen
     * de la base hacia la API.
     */
    @Query(
            value = """
                    SELECT new com.sbss.lexia.biblioteca.dto.RecursoListaResponse(
                        r.uuidPublico,
                        r.titulo,
                        r.subtituloDescripcion,
                        r.tipo,
                        r.categoria,
                        r.anio,
                        r.expedienteCodigo,
                        r.nombreArchivo,
                        r.extension,
                        r.estado,
                        r.fechaCreacion
                    )
                    FROM RecursoJuridico r
                    WHERE r.estudioId = :estudioId
                      AND r.activo = true
                      AND (
                            :busqueda IS NULL
                            OR LOWER(r.titulo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                            OR LOWER(COALESCE(r.subtituloDescripcion, '')) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                            OR LOWER(COALESCE(r.expedienteCodigo, '')) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                      )
                      AND (:tipo IS NULL OR r.tipo = :tipo)
                      AND (:categoria IS NULL OR r.categoria = :categoria)
                      AND (:estado IS NULL OR r.estado = :estado)
                      AND (:anio IS NULL OR r.anio = :anio)
                      AND (
                            :conArchivo IS NULL
                            OR (:conArchivo = true AND r.nombreArchivo IS NOT NULL AND r.nombreArchivo <> '')
                            OR (:conArchivo = false AND (r.nombreArchivo IS NULL OR r.nombreArchivo = ''))
                      )
                    """,
            countQuery = """
                    SELECT COUNT(r)
                    FROM RecursoJuridico r
                    WHERE r.estudioId = :estudioId
                      AND r.activo = true
                      AND (
                            :busqueda IS NULL
                            OR LOWER(r.titulo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                            OR LOWER(COALESCE(r.subtituloDescripcion, '')) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                            OR LOWER(COALESCE(r.expedienteCodigo, '')) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                      )
                      AND (:tipo IS NULL OR r.tipo = :tipo)
                      AND (:categoria IS NULL OR r.categoria = :categoria)
                      AND (:estado IS NULL OR r.estado = :estado)
                      AND (:anio IS NULL OR r.anio = :anio)
                      AND (
                            :conArchivo IS NULL
                            OR (:conArchivo = true AND r.nombreArchivo IS NOT NULL AND r.nombreArchivo <> '')
                            OR (:conArchivo = false AND (r.nombreArchivo IS NULL OR r.nombreArchivo = ''))
                      )
                    """)
    Page<RecursoListaResponse> buscar(
            @Param("estudioId") Integer estudioId,
            @Param("busqueda") String busqueda,
            @Param("tipo") String tipo,
            @Param("categoria") String categoria,
            @Param("estado") String estado,
            @Param("conArchivo") Boolean conArchivo,
            @Param("anio") Integer anio,
            Pageable pageable);

    @Query("""
            SELECT
                COUNT(r) AS recursosTotales,
                COALESCE(SUM(CASE WHEN r.estado = 'ACTIVO' THEN 1 ELSE 0 END), 0) AS recursosActivos,
                COALESCE(SUM(CASE WHEN r.nombreArchivo IS NOT NULL AND r.nombreArchivo <> '' THEN 1 ELSE 0 END), 0) AS conArchivo,
                COUNT(DISTINCT r.categoria) AS totalCategorias
            FROM RecursoJuridico r
            WHERE r.estudioId = :estudioId
              AND r.activo = true
            """)
    EstadisticasProjection estadisticas(@Param("estudioId") Integer estudioId);

    interface EstadisticasProjection {
        Long getRecursosTotales();
        Long getRecursosActivos();
        Long getConArchivo();
        Long getTotalCategorias();
    }
}
