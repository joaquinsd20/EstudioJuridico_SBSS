package com.sbss.lexia.seguridad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Rol del sistema.
 *
 * `codigo` es INMUTABLE y es lo unico que puede usarse en @PreAuthorize.
 * `nombre` es la etiqueta visible y el Administrador puede editarla desde
 * Configuracion: derivar permisos de ella revocaria accesos en silencio el dia
 * que alguien corrija una tilde.
 *
 * No hereda de EntidadAuditable: la tabla solo tiene `activo`, sin fechas ni
 * ids de auditoria.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Rol {

    /** SMALLINT UNSIGNED en la base: por eso Short. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Short id;

    @Column(nullable = false, length = 30, updatable = false)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 250)
    private String descripcion;

    /** Numero ALTO = MAS acceso. Administrador 100 ... Cliente 10. */
    @Column(name = "nivel_acceso", nullable = false)
    private Short nivelAcceso;

    /** 1 = rol del sistema, no se puede eliminar. */
    @Column(name = "es_sistema", nullable = false)
    private Boolean esSistema = false;

    @Column(nullable = false)
    private Boolean activo = true;

    /** Sin setter: la coleccion se modifica, no se reemplaza. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "roles_permisos",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    @Setter(AccessLevel.NONE)
    private Set<Permiso> permisos = new LinkedHashSet<>();
}
