package com.sbss.lexia.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Columnas de auditoria comunes a las tablas del nucleo. No se repiten en cada
 * entidad: se heredan de aqui.
 *
 * Solo la heredan las tablas que tienen las cinco columnas. `roles` y `permisos`
 * llevan unicamente `activo`, y `estudios` no tiene los ids de usuario, asi que
 * esas entidades NO extienden esta clase.
 *
 * Las cuatro primeras columnas las escribe Spring Data, nunca la aplicacion: por
 * eso solo tienen getter.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class EntidadAuditable {

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @CreatedBy
    @Column(name = "usuario_creacion_id", updatable = false)
    private Long usuarioCreacionId;

    @LastModifiedDate
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @LastModifiedBy
    @Column(name = "usuario_modificacion_id")
    private Long usuarioModificacionId;

    /** Borrado logico. Un expediente juridico no se elimina nunca. */
    @Setter
    @Column(nullable = false)
    private Boolean activo = true;
}
