package com.sbss.lexia.catalogos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Feriados, dias no laborables, suspensiones de plazos y huelgas judiciales.
 *
 * SOLO las filas con estado = 'APROBADO' afectan el computo de plazos. Es la
 * regla dura del §7.1 de CLAUDE.md: una suspension mal cargada recalcula mal
 * TODOS los plazos del estudio, asi que necesita dos pares de ojos — la
 * registra un asistente o abogado (CALENDARIO_PROPONER) y la aprueba un socio
 * o administrador (CALENDARIO_APROBAR).
 *
 * No hereda de EntidadAuditable: la tabla tiene fechas pero no
 * `usuario_creacion_id` ni `usuario_modificacion_id`, y tampoco tiene `activo`
 * (una entrada del calendario se rechaza, no se desactiva).
 */
@Entity
@Table(name = "calendario_judicial")
@Getter
@Setter
public class CalendarioJudicial {

    /** INT UNSIGNED en la base, no BIGINT: por eso Integer. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    /**
     * NULL = feriado oficial nacional, aplica a todos los estudios.
     * Escalar y no @ManyToOne: aqui solo interesa el id, y con
     * `open-in-view: false` una relacion obligaria a un JOIN FETCH para nada.
     */
    @Column(name = "estudio_id")
    private Integer estudioId;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    /** Igual a fechaInicio si es un solo dia. El rango es inclusivo en ambos extremos. */
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    /**
     * FERIADO_NACIONAL · FERIADO_REGIONAL · DIA_NO_LABORABLE ·
     * SUSPENSION_PLAZOS · HUELGA_JUDICIAL.
     */
    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String descripcion;

    /** Res. Adm. N.o ... que sustenta la suspension. */
    @Column(name = "norma_referencia", length = 150)
    private String normaReferencia;

    /** NULL = nacional; si no, el distrito judicial al que se limita. */
    @Column(length = 80)
    private String ambito;

    /** PROPUESTO · APROBADO · RECHAZADO. Solo APROBADO afecta el computo. */
    @Column(nullable = false, length = 15)
    private String estado = "PROPUESTO";

    /** Quien la detecto y cargo. Escalar por el mismo motivo que estudioId. */
    @Column(name = "usuario_registro_id", nullable = false)
    private Long usuarioRegistroId;

    /** Socio o administrador que la aprobo. NULL mientras siga PROPUESTO. */
    @Column(name = "usuario_aprobacion_id")
    private Long usuarioAprobacionId;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    /** Un CHECK obliga a llenarlo cuando el estado es RECHAZADO. */
    @Column(name = "motivo_rechazo", length = 250)
    private String motivoRechazo;

    /**
     * DEFAULT CURRENT_TIMESTAMP(3) en la base. `insertable = false` +
     * @Generated es obligatorio: sin eso Hibernate manda NULL explicito en el
     * INSERT y pisa el DEFAULT — un DEFAULT de columna solo se aplica cuando
     * la columna se OMITE del INSERT, no cuando se manda NULL. Ya paso en
     * Estudio.java y costo depurarlo; aqui se evita de entrada.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime fechaCreacion;

    /** ON UPDATE CURRENT_TIMESTAMP(3): no tiene valor hasta la primera edicion. */
    @Generated(event = EventType.UPDATE)
    @Column(name = "fecha_modificacion", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime fechaModificacion;
}
