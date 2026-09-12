package com.sbss.lexia.catalogos.repository;

import com.sbss.lexia.catalogos.entity.Configuracion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Short> {

    Optional<Configuracion> findByEstudioIdAndClave(Integer estudioId, String clave);
}
