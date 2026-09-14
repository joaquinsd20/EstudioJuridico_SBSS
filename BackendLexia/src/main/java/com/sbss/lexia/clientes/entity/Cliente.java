package com.sbss.lexia.clientes.entity;

import com.sbss.lexia.seguridad.entity.Usuario;
import com.sbss.lexia.shared.audit.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Cliente del estudio: persona natural, juridica o entidad publica.
 *
 * `estudioId` se mapea como escalar (Integer), NO como @ManyToOne a Estudio:
 * toda consulta de este modulo filtra por el (ver ClienteRepository y
 * ClienteSpecification.delEstudio), y con `open-in-view: false` una relacion
 * obligaria a un JOIN FETCH en cada consulta solo para leer un id que ya se
 * conoce de antemano (viene de SesionActual, no de la base).
 *
 * SI hereda de EntidadAuditable: `clientes` tiene las cinco columnas de
 * auditoria completas. `cliente_contactos` y `contrapartes` NO podran
 * heredarla cuando se escriban (PR de conflicto de intereses): les falta
 * `usuario_modificacion_id`, y el javadoc de EntidadAuditable es explicito en
 * que solo la heredan las tablas con las cinco columnas.
 */
@Entity
@Table(name = "clientes")
@Getter
@Setter
public class Cliente extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /** Identificador publico. El id BIGINT nunca sale del backend. */
    @Generated(event = EventType.INSERT)
    @Column(name = "uuid_publico", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String uuidPublico;

    /**
     * INT UNSIGNED en la base: por eso Integer y no Long (ver Estudio.java).
     * `updatable = false`: un cliente no cambia de estudio. Es una frontera de
     * multi-tenencia, no un dato editable.
     */
    @Column(name = "estudio_id", nullable = false, updatable = false)
    private Integer estudioId;

    @Column(name = "tipo_persona", nullable = false, length = 20)
    private String tipoPersona;

    @Column(name = "tipo_documento", nullable = false, length = 20)
    private String tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    // --- Persona natural ---
    @Column(length = 100)
    private String nombres;

    @Column(name = "apellido_paterno", length = 80)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 80)
    private String apellidoMaterno;

    // --- Persona juridica o entidad ---
    @Column(name = "razon_social", length = 200)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 200)
    private String nombreComercial;

    /**
     * Columna GENERATED ALWAYS ... STORED de MySQL:
     * COALESCE(razon_social, apellido_paterno + apellido_materno + nombres).
     * Unifica el nombre para mostrar y buscar sin importar el tipo de persona.
     *
     * Solo lectura, y se relee tambien tras UPDATE (evento INSERT y UPDATE):
     * editar nombres/apellidos/razon_social la recalcula en la base, y sin el
     * evento UPDATE la entidad en memoria quedaria con el valor viejo. Mismo
     * patron que Usuario.nombreCompleto — ver el comentario alli para el
     * porque completo.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "nombre_busqueda", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private String nombreBusqueda;

    @Column(length = 150)
    private String email;

    @Column(name = "telefono_principal", length = 30)
    private String telefonoPrincipal;

    @Column(name = "telefono_secundario", length = 30)
    private String telefonoSecundario;

    @Column(length = 250)
    private String direccion;

    @Column(length = 80)
    private String distrito;

    @Column(length = 80)
    private String provincia;

    @Column(length = 80)
    private String departamento;

    /** PROSPECTO (por defecto en la base) · ACTIVO · INACTIVO · ANTIGUO. */
    @Column(nullable = false, length = 20)
    private String estado = "PROSPECTO";

    @Column(name = "origen_captacion", length = 50)
    private String origenCaptacion;

    @Column(name = "situacion_financiera", length = 20)
    private String situacionFinanciera;

    /**
     * LAZY: solo el detalle y la fila de la lista necesitan su nombre, y ese
     * caso ya trae @EntityGraph en ClienteRepository para evitar N+1 al
     * paginar. Nula si el cliente todavia no tiene abogado asignado.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abogado_responsable_id")
    private Usuario abogadoResponsable;

    /** Columna "Ultimo Contacto". La actualiza el servicio, no un trigger. */
    @Column(name = "fecha_ultimo_contacto")
    private LocalDateTime fechaUltimoContacto;

    @Column(columnDefinition = "TEXT")
    private String observaciones;
}
