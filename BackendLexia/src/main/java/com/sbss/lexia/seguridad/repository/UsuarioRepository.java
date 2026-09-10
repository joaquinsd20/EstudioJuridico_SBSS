package com.sbss.lexia.seguridad.repository;

import com.sbss.lexia.seguridad.entity.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Carga el usuario con sus roles y permisos en una sola consulta.
     *
     * El JOIN FETCH no es un detalle de rendimiento: con `open-in-view: false`
     * la sesion de Hibernate ya esta cerrada cuando el UserDetailsService arma
     * las authorities, asi que los roles LAZY fallarian. Aqui llegan resueltos.
     */
    @Query("""
            select distinct u from Usuario u
            left join fetch u.roles r
            left join fetch r.permisos
            where u.email = :email and u.activo = true
            """)
    Optional<Usuario> findByEmailConRoles(@Param("email") String email);

    Optional<Usuario> findByEmail(String email);

    /** Busqueda por el identificador que expone la API. El id BIGINT no sale. */
    Optional<Usuario> findByUuidPublico(String uuidPublico);

    boolean existsByEmail(String email);
}
