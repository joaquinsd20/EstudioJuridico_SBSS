package com.sbss.lexia.clientes.spec;

import com.sbss.lexia.clientes.entity.Cliente;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros opcionales del listado de clientes, componibles con
 * {@link Specification#allOf}. Cada metodo devuelve
 * {@link Specification#unrestricted()} cuando el filtro viene vacio, en vez de
 * {@code null}: {@code allOf} espera specifications, no nulls.
 *
 * Ninguno envuelve el valor en {@code cb.lower(...)}: la colacion de la tabla
 * es utf8mb4_0900_ai_ci, que YA es insensible a mayusculas y a tildes.
 * Envolver en lower() no cambiaria el resultado y anularia el uso de
 * ix_clientes_busqueda.
 */
public final class ClienteSpecification {

    private ClienteSpecification() {
    }

    /**
     * Obligatoria, siempre primera en la composicion. Es la frontera de
     * multi-tenencia: sin esta, cualquier otro filtro consultaria clientes de
     * todos los estudios.
     */
    public static Specification<Cliente> delEstudio(Integer estudioId) {
        return (root, query, cb) -> cb.equal(root.get("estudioId"), estudioId);
    }

    public static Specification<Cliente> soloActivos() {
        return (root, query, cb) -> cb.isTrue(root.get("activo"));
    }

    /** Prefijo, no substring: aprovecha ix_clientes_documento_busq. */
    public static Specification<Cliente> documentoEmpiezaPor(String documento) {
        if (documento == null || documento.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.like(root.get("numeroDocumento"), documento.trim() + "%");
    }

    /**
     * Substring sobre la columna unificada de busqueda. No usa el FULLTEXT
     * ft_clientes_nombre: JPA Criteria no puede expresar MATCH...AGAINST. Con
     * el volumen de un estudio (decenas o cientos de clientes) un LIKE con
     * comodin al inicio sigue siendo aceptable; el FULLTEXT se reserva para el
     * buscador de conflicto de intereses, que cruza varias tablas.
     */
    public static Specification<Cliente> nombreContiene(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.like(root.get("nombreBusqueda"), "%" + nombre.trim() + "%");
    }

    public static Specification<Cliente> tipoPersona(String tipoPersona) {
        if (tipoPersona == null || tipoPersona.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("tipoPersona"), tipoPersona);
    }

    public static Specification<Cliente> estado(String estado) {
        if (estado == null || estado.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }
}
