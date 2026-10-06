package com.sbss.lexia.biblioteca.entity;

import com.sbss.lexia.shared.audit.EntidadAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Material de consulta del estudio: jurisprudencia, doctrina, legislacion,
 * modelos y plantillas.
 *
 * NO son los documentos de un expediente — esos viven en `documentos` y
 * cuelgan de `expediente_id NOT NULL` por la regla de oro. Por eso
 * `expedienteCodigo` aqui es texto libre opcional y no una relacion: la
 * mayoria de los recursos no pertenece a ningun caso.
 *
 * `tipo`, `categoria` y `estado` guardan CODIGOS en MAYUSCULAS, no el texto
 * que ve el usuario. La etiqueta visible ("Derecho Civil") la arma el
 * frontend; asi se puede renombrar sin migrar filas, igual que roles.codigo
 * frente a roles.nombre. Los valores validos los fija un CHECK en V10.
 */
@Entity
@Table(name = "recursos_juridicos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecursoJuridico extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /**
     * Identificador publico. Lo genera la base con DEFAULT (UUID()).
     *
     * La API expone este UUID y mantiene el BIGINT como identificador interno
     * exclusivo de persistencia.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "uuid_publico", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String uuidPublico;

    /** INT UNSIGNED en la base: Integer, no Long. Ver Estudio.java. */
    @Column(name = "estudio_id", nullable = false, updatable = false)
    private Integer estudioId;

    @Column(nullable = false, length = 250)
    private String titulo;

    @Column(name = "subtitulo_descripcion", columnDefinition = "TEXT")
    private String subtituloDescripcion;

    /** JURISPRUDENCIA · LEGISLACION · DOCTRINA · CONTRATO · MODELO · PLANTILLA · FORMATO · LIBRO */
    @Column(nullable = false, length = 20)
    private String tipo;

    /** DERECHO_CIVIL · DERECHO_PENAL · ... · PROCESAL · GENERAL */
    @Column(length = 30)
    private String categoria;

    @Column(name = "anio")
    private Integer anio;

    /** Referencia suelta a un expediente. No es FK: ver el javadoc de la clase. */
    @Column(name = "expediente_codigo", length = 50)
    private String expedienteCodigo;

    @Column(name = "nombre_archivo", length = 255)
    private String nombreArchivo;

    /** Ruta en disco. Ningun binario en MySQL (regla 7 de CLAUDE.md). */
    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Column(length = 10)
    private String extension;

    @Column(name = "tamanio_bytes")
    private Long tamanioBytes;

    /**
     * ACTIVO o ARCHIVADO. Distinto de `activo`, heredado de EntidadAuditable,
     * que es el borrado logico: un recurso ARCHIVADO sigue existiendo y se
     * puede consultar; uno con activo = 0 esta borrado.
     */
    @Column(nullable = false, length = 15)
    private String estado = "ACTIVO";

    // Ya no hay `fechaRegistro` ni @PrePersist: la fecha de alta es
    // `fechaCreacion`, heredada de EntidadAuditable y escrita por el
    // AuditorAware de Spring Data. Tener dos columnas para "cuando se creo
    // esto" es la clase de duplicacion que termina en que cada consulta usa
    // una distinta.
}
