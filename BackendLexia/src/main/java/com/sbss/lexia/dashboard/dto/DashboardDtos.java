package com.sbss.lexia.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * Los datos que alimentan los graficos del panel.
     *
     * Se agrupan en un unico endpoint y no en uno por grafico porque los
     * graficos se dibujan juntos: seis peticiones paralelas para pintar una
     * pantalla que se ve de un vistazo seria gastar la base en una consulta
     * que el usuario apenas mira. Ademas asi el panel no puede quedar a medias
     * con un grafico dibujado y otro vacio.
     */
    public record Graficos(
            List<Etiqueta> expedientesPorEstado,
            List<Etiqueta> expedientesPorEspecialidad,
            List<Barra> expedientesPorAbogado,
            List<Barra> cargaPorUsuario,
            List<Barra> tareasPorUsuario,
            List<Barra> ingresosPorMes,
            EstadoCobranza cobranza) {
    }

    /** Una rebanada: nombre y numero. Sin porcentaje, lo calcula el front. */
    public record Etiqueta(String nombre, long valor) {
    }

    /**
     * Una barra: nombre, valor, color y un detalle opcional.
     *
     * `valor` es SIEMPRE lo que ordena la serie, y el front pinta la barra con
     * ese mismo numero. No se puede ordenar por un criterio y pintar por otro:
     * el grafico de carga se ordena por riesgo ponderado, asi que su barra
     * mide riesgo ponderado, y el numero de expedientes viaja en `detalle`
     * para cuando el usuario quiera el dato crudo.
     *
     * El color lo elige el servidor para que el mismo estado o la misma
     * severidad se pinten igual en todos los graficos.
     */
    public record Barra(String nombre, long valor, String color, String detalle) {

        public static Barra de(String nombre, long valor, String color) {
            return new Barra(nombre, valor, color, null);
        }
    }

    /**
     * Ingresos y cuentas por cobrar, por separado de la serie mensual.
     *
     * Va en su propio record y no como dos barras mas porque responde a otra
     * pregunta: la serie mensual dice como va el ano, esto dice cuanto dinero
     * hay pendiente ahora mismo y cuanto de eso esta vencido.
     */
    public record EstadoCobranza(
            BigDecimal facturado,
            BigDecimal cobrado,
            BigDecimal pendiente,
            BigDecimal vencido,
            long cuentasPendientes,
            long cuentasVencidas) {
    }
}
