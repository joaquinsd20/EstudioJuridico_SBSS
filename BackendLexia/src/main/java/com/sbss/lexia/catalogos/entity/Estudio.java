package com.sbss.lexia.catalogos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Raiz multi-tenant. Hoy existe un unico registro (id = 1).
 *
 * No hereda de EntidadAuditable: la tabla tiene fechas pero no
 * `usuario_creacion_id` ni `usuario_modificacion_id`.
 */
@Entity
@Table(name = "estudios")
@Getter
@Setter
public class Estudio {

    /** INT UNSIGNED en la base, no BIGINT: por eso Integer y no Long. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    /** Lo genera la base con DEFAULT (UUID()). Hibernate solo lo lee. */
    @Generated(event = EventType.INSERT)
    @Column(name = "uuid_publico", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String uuidPublico;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 150)
    private String nombreComercial;

    @Column(nullable = false, length = 11)
    private String ruc;

    @Column(length = 250)
    private String direccion;

    @Column(length = 30)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "zona_horaria", nullable = false, length = 50)
    private String zonaHoraria;

    @Column(name = "moneda_base", nullable = false, length = 3)
    private String monedaBase;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    @Setter(AccessLevel.NONE)
    private LocalDateTime fechaModificacion;

    @Column(nullable = false)
    private Boolean activo = true;
}
