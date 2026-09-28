package com.sbss.lexia.shared.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La regla ÚNICA de "qué expedientes puede ver quien consulta".
 *
 * Existe porque cada módulo la reinventó y dos la reinventaron mal:
 * `expedientes` filtraba bien pero ocultaba el confidencial a su propio
 * responsable, `agenda` filtra bien, y `actuaciones` y `documental` no filtran
 * por asignación en absoluto — cualquiera del estudio veía las actuaciones y
 * los documentos de un expediente confidencial ajeno. Al estar la regla en
 * cuatro sitios, arreglarla en uno no arreglaba nada.
 *
 * La regla, que es la del RF-02 ("bloquear el acceso a expedientes
 * confidenciales a roles operativos, salvo asignación explícita"):
 *
 * 1. Siempre, el estudio de quien consulta. Es la frontera de multi-tenencia.
 * 2. Portal del cliente: solo los expedientes de SU cliente, y nunca los
 *    confidenciales.
 * 3. Administrador y Socio ven todo el estudio.
 * 4. El resto ve aquello donde es responsable o está en el equipo — y eso
 *    incluye los confidenciales que tenga asignados: "salvo asignación
 *    explícita" significa justo eso.
 *
 * Genera SQL con parámetros con nombre propios (`vis*`) para no chocar con los
 * del módulo que la use.
 *
 * <p><b>Lo que devuelve empieza y termina con un espacio, siempre.</b> No es
 * cosmético: una de las cuatro ramas no lo hacía, y al concatenarla con un
 * bloque de texto de Java —que recorta los espacios finales— el nombre del
 * parámetro se pegó a la palabra siguiente:
 *
 * <pre>
 *   WHERE ... AND e.estudio_id = :visEstudioIdORDER BY e.codigo_interno DESC
 *
 *   InvalidDataAccessApiUsageException: No value supplied for the SQL
 *   parameter 'visEstudioIdORDER'
 * </pre>
 *
 * Solo reventaba para Administrador y Socio, que son los unicos que caen en esa
 * rama, y solo en /api/v1/tareas/catalogos. Lo fija VisibilidadExpedientesTest.
 */
public final class VisibilidadExpedientes {

    private static final String P_ESTUDIO = "visEstudioId";
    private static final String P_USUARIO = "visUsuarioId";
    private static final String P_CLIENTE = "visClienteId";

    private VisibilidadExpedientes() {
    }

    /** Administrador y Socio ven todos los expedientes del estudio. */
    public static boolean verTodos() {
        return SesionActual.tieneAutoridad("ADMINISTRADOR") || SesionActual.tieneAutoridad("SOCIO");
    }

    /** Quién puede ver un expediente confidencial que NO tiene asignado. */
    public static boolean verConfidenciales() {
        return SesionActual.tieneAutoridad("EXPEDIENTE_CONFIDENCIAL") || verTodos();
    }

    /**
     * Condiciones a añadir a un WHERE ya abierto (empiezan con " AND "), sobre
     * la tabla `expedientes` con el alias indicado.
     *
     * Si el expediente puede venir en NULL (por ejemplo, un evento de agenda
     * sin expediente), hay que envolverlo: esta regla asume que la fila
     * SIEMPRE tiene expediente.
     */
    public static String sql(String alias) {
        StringBuilder f = new StringBuilder(" AND ").append(alias).append(".estudio_id = :").append(P_ESTUDIO);

        if (SesionActual.esPortalCliente()) {
            f.append(" AND ").append(alias).append(".cliente_id = :").append(P_CLIENTE)
             .append(" AND ").append(alias).append(".es_confidencial = 0");
            return cerrar(f);
        }
        if (!verTodos()) {
            f.append(" AND (").append(alias).append(".abogado_responsable_id = :").append(P_USUARIO)
             .append(" OR EXISTS (SELECT 1 FROM expediente_equipo eqvis")
             .append(" WHERE eqvis.expediente_id = ").append(alias).append(".id")
             .append(" AND eqvis.usuario_id = :").append(P_USUARIO)
             .append(" AND eqvis.activo = 1))");
            // Sin verTodos, lo anterior ya limita a lo asignado: el confidencial
            // asignado SE VE, que es lo que pide el RF-02.
            return cerrar(f);
        }
        if (!verConfidenciales()) {
            f.append(" AND ").append(alias).append(".es_confidencial = 0");
        }
        return cerrar(f);
    }

    /**
     * Toda rama sale por aqui, y por eso el espacio final esta garantizado.
     * Sin el, lo que venga detras se pega al nombre del parametro: ver el
     * javadoc de la clase.
     */
    private static String cerrar(StringBuilder f) {
        return f.append(' ').toString();
    }

    /** Los parámetros que usa {@link #sql(String)}, añadidos al mapa del módulo. */
    public static Map<String, Object> parametros(Map<String, Object> destino) {
        destino.put(P_ESTUDIO, SesionActual.estudioId());
        if (SesionActual.esPortalCliente()) {
            destino.put(P_CLIENTE, SesionActual.clienteId());
        } else if (!verTodos()) {
            destino.put(P_USUARIO, SesionActual.usuarioId());
        }
        return destino;
    }

    /** Los mismos parámetros, cuando el módulo no tiene un mapa que reutilizar. */
    public static Map<String, Object> parametros() {
        return parametros(new LinkedHashMap<>());
    }
}
