package com.sbss.lexia.biblioteca.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recurso_juridico")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecursoJuridico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    private String subtituloDescripcion;

    @Column(nullable = false)
    private String tipo;

    private String categoria;

    private Integer anio;

    private String expedienteCodigo;

    private String nombreArchivo;

    private String rutaArchivo;

    private String extension;

    private Long tamanioBytes;

    @Column(nullable = false)
    private String estado;

    private LocalDateTime fechaRegistro;

    @PrePersist
    public void prePersist() {
        this.fechaRegistro = LocalDateTime.now();
        if (this.estado == null) this.estado = "Activo";
    }
}