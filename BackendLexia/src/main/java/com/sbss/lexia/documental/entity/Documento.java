package com.sbss.lexia.documental.entity;

import com.sbss.lexia.shared.audit.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "documentos")
@Getter
@Setter
public class Documento extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid_publico", nullable = false, length = 36, unique = true)
    private String uuidPublico;

    @Column(name = "expediente_id", nullable = false)
    private Long expedienteId;

    @Column(name = "actuacion_id")
    private Long actuacionId;

    @Column(name = "tarea_id")
    private Long tareaId;

    @Column(nullable = false, length = 250)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false, length = 25)
    private String categoria;

    @Column(nullable = false, length = 20)
    private String estado = "BORRADOR";

    @Column(name = "version_vigente_id")
    private Long versionVigenteId;

    @Column(name = "es_confidencial", nullable = false)
    private Boolean esConfidencial = false;

    @Column(name = "en_papelera", nullable = false)
    private Boolean enPapelera = false;

    @Column(name = "fecha_papelera")
    private LocalDateTime fechaPapelera;

    @PrePersist
    void prepararUuid() {
        if (uuidPublico == null || uuidPublico.isBlank()) {
            uuidPublico = UUID.randomUUID().toString();
        }
    }
}
