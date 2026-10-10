package com.sbss.lexia.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
     *
     * `puedeVerFacturacion` dice si `ingresosPorMes` y `cobranza` se consultaron:
     * sin FACTURACION_LEER llegan vacios, y la pantalla debe OCULTAR esas dos
     * tarjetas en vez de pintar "S/ 0" como si fuera un dato.
     */
    public record Graficos(
            List<Etiqueta> expedientesPorEstado,
            List<Etiqueta> expedientesPorEspecialidad,
            List<Barra> expedientesPorAbogado,
            List<Barra> cargaPorUsuario,
            List<Barra> tareasPorUsuario,
            List<Barra> ingresosPorMes,
            EstadoCobranza cobranza,
            boolean puedeVerFacturacion) {
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

        /** Lo que recibe quien no tiene FACTURACION_LEER: sin tocar `honorarios`. */
        public static EstadoCobranza sinAcceso() {
            return new EstadoCobranza(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }
    }

    /**
     * Un expediente abierto que pide mirarse hoy: riesgo critico o sin
     * movimiento hace mas de 30 dias. Lo calcula el servidor porque
     * GET /expedientes es paginado: filtrar en el navegador una pagina de 20
     * perdia en silencio los criticos de las paginas siguientes.
     */
    public record ExpedienteAtencion(
            String uuid,
            String codigoInterno,
            String numeroExpediente,
            String clienteNombre,
            String materia,
            String estadoNombre,
            String nivelRiesgo,
            String responsable,
            boolean confidencial,
            LocalDateTime fechaUltimaActuacion) {
    }
}
