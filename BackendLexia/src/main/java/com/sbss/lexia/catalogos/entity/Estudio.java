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

    /**
     * DEFAULT CURRENT_TIMESTAMP(3) en la base. Mismo motivo que uuidPublico:
     * sin insertable=false, Hibernate manda NULL explicito en el INSERT (no
     * hay valor Java ni @PrePersist que lo llene) y eso pisa el DEFAULT de
     * MySQL — un DEFAULT de columna solo se aplica cuando la columna se OMITE
     * del INSERT, no cuando se manda NULL a proposito. Bug real, no teorico:
     * lo disparo el primer INSERT de un Estudio nuevo hecho por JPA en todo
     * el proyecto (AislamientoPorEstudioIT); hasta ahora el unico Estudio
     * (id=1) lo carga la semilla con SQL crudo, que si omite la columna.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime fechaCreacion;

    /** ON UPDATE CURRENT_TIMESTAMP(3) en la base: no tiene valor hasta la
     * primera actualizacion, por eso el evento es solo UPDATE. */
    @Generated(event = EventType.UPDATE)
    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime fechaModificacion;

    @Column(nullable = false)
    private Boolean activo = true;
}
