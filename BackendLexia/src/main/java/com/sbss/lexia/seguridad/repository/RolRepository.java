package com.sbss.lexia.seguridad.repository;

import com.sbss.lexia.seguridad.entity.Rol;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepository extends JpaRepository<Rol, Short> {

    /** Se busca por `codigo`, nunca por `nombre`: el nombre es editable. */
    Optional<Rol> findByCodigo(String codigo);
}
