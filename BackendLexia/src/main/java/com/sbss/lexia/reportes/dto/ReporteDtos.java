package com.sbss.lexia.reportes.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ReporteDtos {

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
