package com.sbss.lexia.catalogos.repository;

import com.sbss.lexia.catalogos.entity.Estudio;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * No tenia repositorio propio hasta ahora: el unico acceso a Estudio era via
 * la relacion LAZY desde Usuario. Se agrega para AislamientoPorEstudioIT, que
 * necesita crear y borrar un segundo estudio de prueba — y le sirve a
 * cualquier pantalla futura de administracion del estudio.
 */
public interface EstudioRepository extends JpaRepository<Estudio, Integer> {

    boolean existsByRucAndIdNot(String ruc, Integer id);
}
