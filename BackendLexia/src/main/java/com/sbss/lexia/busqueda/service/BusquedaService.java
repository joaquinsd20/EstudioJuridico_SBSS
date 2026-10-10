package com.sbss.lexia.busqueda.service;

import com.sbss.lexia.busqueda.dto.BusquedaDtos.Grupo;
import com.sbss.lexia.busqueda.dto.BusquedaDtos.Respuesta;
import com.sbss.lexia.busqueda.dto.BusquedaDtos.Resultado;
import com.sbss.lexia.busqueda.repository.BusquedaRepositoryJdbc;
import com.sbss.lexia.shared.security.SesionActual;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BusquedaService {

    private final BusquedaRepositoryJdbc repo;

    /**
     * Busca en las siete entidades a la vez y devuelve los resultados agrupados.
     *
     * Siete grupos, no ocho: el DNI/RUC que pidio el usuario no es un tipo de
     * resultado, es una de las columnas por las que se busca DENTRO de clientes,
     * contrapartes y abogados. Un RUC buscado devuelve fichas de esos tres tipos,
     * que es donde vive ese dato, y no una cuarta seccion duplicada.
     *
     * Las siete consultas van en secuencia y no en paralelo. Suena a lentitud,
     * pero los indices resuelven cada una en milisegundos sobre el volumen de
     * un estudio juridico, mientras que siete consultas simultaneas abririan
     * siete conexiones del pool para una barra de busqueda que se escribe letra
     * a letra. El coste real esta en el SQL mal construido, no en el numero de
     * consultas.
     */
    public Respuesta buscar(String texto) {
        String limpio = texto == null ? "" : texto.trim();

        if (limpio.length() < BusquedaRepositoryJdbc.MIN_CARACTERES) {
            return new Respuesta(limpio, 0, List.of());
        }

        List<Grupo> grupos = new ArrayList<>();
        grupos.add(grupo("EXPEDIENTE", "Expedientes", () -> repo.expedientes(limpio)));
        grupos.add(grupo("CLIENTE", "Clientes", () -> repo.clientes(limpio)));
        grupos.add(grupo("CONTRAPARTE", "Contrapartes", () -> repo.contrapartes(limpio)));
        grupos.add(grupo("RESOLUCION", "Resoluciones", () -> repo.resoluciones(limpio)));
        grupos.add(grupo("ACTUACION", "Actuaciones", () -> repo.actuaciones(limpio)));
        grupos.add(grupo("DOCUMENTO", "Documentos", () -> repo.documentos(limpio)));
        grupos.add(grupo("ABOGADO", "Abogados", () -> repo.abogados(limpio)));

        // Un tipo sin resultados no ocupa espacio en la lista: si el usuario
        // busca "EXP-0142", no quiere leer una cabecera de "Contrapartes: 0".
        grupos.removeIf(g -> g.items().isEmpty());

        int total = grupos.stream().mapToInt(Grupo::total).sum();
        return new Respuesta(limpio, total, grupos);
    }

    /**
     * Busca en un solo tipo, para el "ver todos los resultados" de una categoria
     * concreta, donde interesa el listado largo y no la vista agrupada.
     *
     * Un tipo desconocido devuelve una lista vacia y no un error: el codigo
     * viene de la URL y un valor raro deberia pintarse como "sin resultados",
     * no romper el buscador con un 500.
     */
    public List<Resultado> porTipo(String texto, String tipo) {
        String limpio = texto == null ? "" : texto.trim();
        String codigo = tipo == null ? "" : tipo.toUpperCase();
        if (limpio.length() < BusquedaRepositoryJdbc.MIN_CARACTERES || !puede(codigo)) {
            return List.of();
        }
        return switch (codigo) {
            case "EXPEDIENTE" -> repo.expedientes(limpio);
            case "CLIENTE" -> repo.clientes(limpio);
            case "CONTRAPARTE" -> repo.contrapartes(limpio);
            case "RESOLUCION" -> repo.resoluciones(limpio);
            case "ACTUACION" -> repo.actuaciones(limpio);
            case "DOCUMENTO" -> repo.documentos(limpio);
            case "ABOGADO" -> repo.abogados(limpio);
            default -> List.of();
        };
    }

    /**
     * Permiso del modulo dueno de cada tipo de resultado.
     *
     * El buscador solo exige DASHBOARD_LEER, que tienen siete de los ocho roles,
     * recepcion incluida. Sin esto devolvia actuaciones y documentos a quien el
     * propio modulo le responde 403. Las resoluciones y las contrapartes se ven
     * dentro del expediente, de ahi EXPEDIENTE_LEER para las resoluciones. Las
     * contrapartes y los abogados no tienen modulo de lectura propio: se buscan
     * en todo el estudio (la contraparte, para detectar conflictos de intereses)
     * y lo que dicen de expedientes ya pasa por la regla de visibilidad.
     */
    private static final Map<String, String> PERMISO_POR_TIPO = Map.of(
            "EXPEDIENTE", "EXPEDIENTE_LEER",
            "RESOLUCION", "EXPEDIENTE_LEER",
            "CLIENTE", "CLIENTE_LEER",
            "ACTUACION", "ACTUACION_LEER",
            "DOCUMENTO", "DOCUMENTO_LEER");

    private boolean puede(String tipo) {
        String permiso = PERMISO_POR_TIPO.get(tipo);
        return permiso == null || SesionActual.tieneAutoridad(permiso);
    }

    /** Sin el permiso del modulo dueno, la consulta ni se ejecuta. */
    private Grupo grupo(String tipo, String etiqueta, Supplier<List<Resultado>> consulta) {
        List<Resultado> items = puede(tipo) ? consulta.get() : List.of();
        return new Grupo(tipo, etiqueta, items.size(), items);
    }
}
