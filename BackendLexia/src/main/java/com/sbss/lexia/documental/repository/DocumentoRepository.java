package com.sbss.lexia.documental.repository;

import com.sbss.lexia.documental.entity.Documento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    Optional<Documento> findByUuidPublicoAndActivoTrue(String uuidPublico);

    List<Documento> findByEnPapeleraTrueAndFechaPapeleraBefore(LocalDateTime fechaLimite);
}
