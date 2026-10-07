package com.sbss.lexia.reportes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ReporteDtos {

    private ReporteDtos() {
    }

    public record KpiReporte(
            long totalExpedientes,
            long expedientesEnTramite,
            long expedientesVencidos,
            long expedientesConcluidos,
            BigDecimal ingresosTotales,
            BigDecimal cobradoTotal,
            BigDecimal saldoPendiente,
            long tareasPendientes,
            double tiempoPromedioMeses,
            double horasFacturables
    ) {}

    public record FiltrosReporte(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String materia,
            Long abogadoId,
            String rangoPreset
    ) {}

    public record Etiqueta(
            String nombre,
            long total
    ) {}

    public record Barra(
            String etiqueta,
            long valor,
            String color,
            String detalle
    ) {
        public static Barra de(String etiqueta, long valor, String color) {
            return new Barra(etiqueta, valor, color, null);
        }
    }

    public record OpcionReporte(
            Long id,
            String nombre
    ) {}

    public record CatalogosReporte(
            List<OpcionReporte> materias,
            List<OpcionReporte> abogados
    ) {}

    public record ItemReporteDetalle(
            Long id,
            String codigo,
            String titulo,
            String cliente,
            String contraparte,
            String materia,
            String abogado,
            String estado,
            String nivelRiesgo,
            String fechaInicio,
            BigDecimal cuantia
    ) {}

    /**
     * El resumen solo contiene datos agregados. La tabla detallada se carga por
     * un endpoint paginado independiente para no arrastrar decenas o cientos de
     * expedientes cada vez que cambia un filtro del tablero.
     *
     * Los dos campos historicos que no usa el frontend se conservan para no
     * romper consumidores antiguos, pero se devuelven vacios y ya no generan
     * consultas SQL adicionales.
     */
    public record ResumenReporte(
            FiltrosReporte filtros,
            KpiReporte kpis,
            List<Etiqueta> expedientesPorEstado,
            List<Etiqueta> expedientesPorMateria,
            List<Barra> expedientesPorAbogado,
            List<Barra> cargaPorUsuario,
            List<Barra> tareasPorUsuario,
            List<Barra> ingresosPorMes,
            List<ItemReporteDetalle> tablaDetalle
    ) {}
}
