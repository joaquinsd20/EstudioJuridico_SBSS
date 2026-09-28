package com.sbss.lexia.busqueda.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class BusquedaDtos {

    private BusquedaDtos() {
    }

    /**
     * Un acierto del buscador global.
     *
     * Un solo tipo de dato con un campo `tipo` en vez de ocho registros distintos
     * porque el buscador siempre devuelve la misma forma: el frontend pinta una
     * lista unica y elige icono y ruta a partir de `tipo`. Si cada entidad
     * tuviera su propio record, cada fila nueva obligaria a tocar el template.
     */
    public record Resultado(
            String tipo,
            String etiqueta,
            String titulo,
            String detalle,
            String ruta,
            Long expedienteId,
            String expedienteCodigo,
            String coincidencia,
            LocalDateTime fecha) {
    }

    /**
     * Respuesta agrupada por tipo.
     *
     * Se agrupa en el servidor, y no en el navegador, porque el buscador
     * necesita un total por categoria ("3 expedientes, 1 cliente") antes de
     * decidir que mostrar. Ademas el limite se reparte por tipo: si solo se
     * limitara el total, un texto que coincidiera con cien clientes taparia
     * los expedientes, que es justo lo que el usuario busca.
     */
    public record Respuesta(
            String texto,
            int total,
            List<Grupo> grupos) {
    }

    public record Grupo(
            String tipo,
            String etiqueta,
            int total,
            List<Resultado> items) {
    }
}
