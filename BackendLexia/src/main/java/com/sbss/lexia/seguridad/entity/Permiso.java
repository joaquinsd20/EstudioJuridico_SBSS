package com.sbss.lexia.seguridad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Permiso granular, con la convencion {@code <ENTIDAD>_<ACCION>}.
 *
 * Los permisos de cada rol se resuelven contra la tabla `roles_permisos`, no
 * codificados en Java, para que el Administrador los ajuste desde Configuracion
 * sin recompilar.
 */
@Entity
@Table(name = "permisos")
@Getter
@Setter
public class Permiso {

    /** SMALLINT UNSIGNED en la base: por eso Short. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Short id;

    @Column(nullable = false, length = 60)
    private String codigo;

    /** Agrupa los permisos en la pantalla de Configuracion. */
    @Column(nullable = false, length = 40)
    private String modulo;

    @Column(length = 250)
    private String descripcion;
}
