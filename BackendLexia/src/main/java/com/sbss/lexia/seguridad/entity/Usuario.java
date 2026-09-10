package com.sbss.lexia.seguridad.entity;

import com.sbss.lexia.catalogos.entity.Estudio;
import com.sbss.lexia.shared.audit.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Usuario del sistema.
 *
 * Esta tabla se carga en CADA request autenticado, asi que solo contiene lo que
 * la autenticacion necesita. Los datos laborales del empleado (salario, CV,
 * domicilio) NO van aqui: iran en `empleado_datos` cuando exista RR.HH.
 *
 * `tipoUsuario` distingue al personal del estudio (INTERNO, correo del dominio
 * interno) del usuario del portal (CLIENTE, correo propio, ligado por
 * `clienteId`). El dominio interno NO se valida aqui: vive en la tabla
 * `configuracion` y lo comprueba el servicio.
 *
 * Sin @Data ni @ToString a proposito: incluirian la coleccion LAZY de roles y
 * dispararian consultas inesperadas al imprimir la entidad.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /**
     * Identificador publico. Es el unico que sale hacia Angular: el id BIGINT
     * nunca abandona el backend. Lo genera la base con DEFAULT (UUID()).
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "uuid_publico", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String uuidPublico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudio_id", nullable = false)
    private Estudio estudio;

    @Column(name = "tipo_usuario", nullable = false, length = 10)
    private String tipoUsuario = "INTERNO";

    @Column(name = "codigo_usuario", length = 20)
    private String codigoUsuario;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    /**
     * Columna calculada por MySQL (GENERATED ALWAYS ... STORED). Solo lectura, y
     * se relee tambien tras UPDATE: cambiar `nombres` o `apellidos` la recalcula
     * en la base, y sin el evento UPDATE quedaria desactualizada en memoria.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "nombre_completo", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String nombreCompleto;

    @Column(name = "tipo_documento", length = 10)
    private String tipoDocumento;

    @Column(name = "numero_documento", length = 20)
    private String numeroDocumento;

    @Column(nullable = false, length = 150)
    private String email;

    /** BCrypt cost 12. Nunca sale del backend. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 30)
    private String telefono;

    /** Texto que aparece bajo el nombre en el sidebar. */
    @Column(length = 100)
    private String cargo;

    /** CAL / ICA. Solo para abogados. */
    @Column(name = "numero_colegiatura", length = 30)
    private String numeroColegiatura;

    /** Ruta o URL de la foto. NUNCA el binario. */
    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    /** Columnas listas; el flujo TOTP no se implementa en el MVP. */
    @Column(name = "tiene_doble_factor", nullable = false)
    private Boolean tieneDobleFactor = false;

    @Column(name = "secreto_2fa", length = 255)
    private String secreto2fa;

    /**
     * La columna es TINYINT UNSIGNED (0-255). Se mapea a Short porque Byte solo
     * llega a 127, y se declara el tipo JDBC explicitamente porque el driver
     * reporta la columna como TINYINT, no como el SMALLINT que Hibernate
     * asumiria para un Short.
     */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "intentos_fallidos", nullable = false)
    private Short intentosFallidos = 0;

    @Column(name = "fecha_bloqueo")
    private LocalDateTime fechaBloqueo;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "debe_cambiar_password", nullable = false)
    private Boolean debeCambiarPassword = true;

    /**
     * Enlace al cliente para el portal, solo cuando tipoUsuario = CLIENTE. Se
     * mapea como id y no como relacion: la entidad Cliente aun no existe y la FK
     * la agrega la migracion V3.
     */
    @Column(name = "cliente_id")
    private Long clienteId;

    /**
     * Un usuario puede tener varios roles: en un estudio chico el socio fundador
     * tambien litiga. LAZY a proposito — se cargan con JOIN FETCH desde el
     * repositorio cuando el login los necesita, no en cada consulta.
     *
     * Sin setter: la coleccion se modifica, no se reemplaza.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usuarios_roles",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    @Setter(AccessLevel.NONE)
    private Set<Rol> roles = new LinkedHashSet<>();
}
