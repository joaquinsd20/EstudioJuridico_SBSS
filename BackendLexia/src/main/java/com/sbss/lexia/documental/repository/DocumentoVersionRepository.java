package com.sbss.lexia.documental.repository;

import com.sbss.lexia.documental.entity.DocumentoVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoVersionRepository extends JpaRepository<DocumentoVersion, Long> {

    List<DocumentoVersion> findByDocumentoIdOrderByNumeroVersionDesc(Long documentoId);

    @Query("select coalesce(max(v.numeroVersion), 0) from DocumentoVersion v where v.documentoId = :documentoId")
    Integer obtenerUltimaVersion(@Param("documentoId") Long documentoId);

    Optional<DocumentoVersion> findFirstByDocumentoIdOrderByNumeroVersionDesc(Long documentoId);
}
