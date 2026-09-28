package com.sbss.lexia.busqueda.service;

import com.sbss.lexia.busqueda.dto.BusquedaDtos.Grupo;
import com.sbss.lexia.busqueda.dto.BusquedaDtos.Respuesta;
import com.sbss.lexia.busqueda.dto.BusquedaDtos.Resultado;
import com.sbss.lexia.busqueda.repository.BusquedaRepositoryJdbc;
import java.util.ArrayList;
import java.util.List;
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
        grupos.add(grupo("EXPEDIENTE", "Expedientes", repo.expedientes(limpio)));
        grupos.add(grupo("CLIENTE", "Clientes", repo.clientes(limpio)));
        grupos.add(grupo("CONTRAPARTE", "Contrapartes", repo.contrapartes(limpio)));
        grupos.add(grupo("RESOLUCION", "Resoluciones", repo.resoluciones(limpio)));
        grupos.add(grupo("ACTUACION", "Actuaciones", repo.actuaciones(limpio)));
        grupos.add(grupo("DOCUMENTO", "Documentos", repo.documentos(limpio)));
        grupos.add(grupo("ABOGADO", "Abogados", repo.abogados(limpio)));

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
        if (limpio.length() < BusquedaRepositoryJdbc.MIN_CARACTERES) {
            return List.of();
        }
        return switch (tipo == null ? "" : tipo.toUpperCase()) {
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

    private Grupo grupo(String tipo, String etiqueta, List<Resultado> items) {
        return new Grupo(tipo, etiqueta, items.size(), items);
    }
}
