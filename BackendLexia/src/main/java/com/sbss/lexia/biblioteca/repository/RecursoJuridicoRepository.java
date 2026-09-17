package com.sbss.lexia.biblioteca.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.sbss.lexia.biblioteca.entity.RecursoJuridico;

@Repository
public interface RecursoJuridicoRepository extends JpaRepository<RecursoJuridico, Long>, JpaSpecificationExecutor<RecursoJuridico> {

    long countByEstado(String estado);

    @Query("SELECT COUNT(r) FROM RecursoJuridico r WHERE r.nombreArchivo IS NOT NULL AND r.nombreArchivo <> ''")
    long countConArchivo();

    @Query("SELECT COUNT(DISTINCT r.categoria) FROM RecursoJuridico r WHERE r.categoria IS NOT NULL")
    long countCategoriasUnicas();
}