package com.sbss.lexia.biblioteca.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbss.lexia.biblioteca.entity.RecursoJuridico;

/**
 * Regla de CLAUDE.md: ningun metodo consulta sin estudio_id. Si el nombre no
 * lo lleva ({@code EstudioId}) y no recibe una Specification — que en este
 * modulo siempre compone el filtro de estudio primero —, se rechaza en
 * revision. Se verifica leyendo la firma, sin entrar en la implementacion.
 */
@Repository
public interface RecursoJuridicoRepository
        extends JpaRepository<RecursoJuridico, Long>, JpaSpecificationExecutor<RecursoJuridico> {

    /**
     * Un recurso de otro estudio debe verse igual que uno que no existe:
     * devolver 403 confirmaria que el recurso existe en otro lado, que ya es
     * una fuga entre inquilinos.
     */
    Optional<RecursoJuridico> findByIdAndEstudioId(Long id, Integer estudioId);

    long countByEstudioIdAndEstado(Integer estudioId, String estado);

    long countByEstudioId(Integer estudioId);

    @Query("""
            SELECT COUNT(r) FROM RecursoJuridico r
             WHERE r.estudioId = :estudioId
               AND r.nombreArchivo IS NOT NULL AND r.nombreArchivo <> ''
            """)
    long countConArchivoDelEstudio(Integer estudioId);

    @Query("""
            SELECT COUNT(DISTINCT r.categoria) FROM RecursoJuridico r
             WHERE r.estudioId = :estudioId AND r.categoria IS NOT NULL
            """)
    long countCategoriasUnicasDelEstudio(Integer estudioId);
}
