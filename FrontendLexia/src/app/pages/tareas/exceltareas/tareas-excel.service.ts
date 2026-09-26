import { Injectable } from '@angular/core';
import * as ExcelJS from 'exceljs';
import { TareaExcelFila } from '../../../core/models/tarea.models';

@Injectable({ providedIn: 'root' })
export class TareasExcelService {
  async exportar(tareas: TareaExcelFila[]): Promise<void> {
    const workbook = new ExcelJS.Workbook();
    workbook.creator = 'LEXIA';
    workbook.company = 'LEXIA';
    workbook.created = new Date();

    const worksheet = workbook.addWorksheet('Tareas', {
      views: [{ state: 'frozen', ySplit: 2 }],
      pageSetup: {
        orientation: 'landscape',
        fitToPage: true,
        fitToWidth: 1,
        fitToHeight: 0,
        margins: { left: 0.3, right: 0.3, top: 0.5, bottom: 0.5, header: 0.2, footer: 0.2 }
      }
    });

    const c = {
      primario: 'FF6366F1',
      primarioOscuro: 'FF4338CA',
      oscuro: 'FF0F172A',
      claro: 'FFEEF2FF',
      alterno: 'FFF8FAFC',
      borde: 'FFCBD5E1',
      texto: 'FF1E293B',
      blanco: 'FFFFFFFF'
    };

    worksheet.mergeCells('A1:H1');
    const titulo = worksheet.getCell('A1');
    titulo.value = `LEXIA - REPORTE DE TAREAS - ${this.fechaHora(new Date(), true)}`;
    titulo.font = { bold: true, size: 16, color: { argb: c.blanco } };
    titulo.alignment = { horizontal: 'center', vertical: 'middle' };
    titulo.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: c.oscuro } };
    titulo.border = this.borde(c.oscuro);
    worksheet.getRow(1).height = 32;

    const encabezados = ['Tarea', 'Expediente', 'Responsable', 'Supervisor', 'Prioridad', 'Estado', 'Etapa', 'Vencimiento'];
    const filaEncabezado = worksheet.getRow(2);
    filaEncabezado.values = encabezados;
    filaEncabezado.height = 28;
    filaEncabezado.eachCell((cell: ExcelJS.Cell) => {
      cell.font = { bold: true, size: 11, color: { argb: c.blanco } };
      cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: c.primario } };
      cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
      cell.border = this.borde(c.primarioOscuro);
    });

    tareas.forEach((t, index) => {
      const fila = worksheet.addRow([
        t.titulo,
        t.expedienteCodigo,
        t.responsable,
        t.supervisor || '—',
        this.texto(t.prioridad),
        this.estado(t.estado),
        this.texto(t.etapaFlujo),
        this.fechaHora(new Date(t.fechaLimite))
      ]);
      fila.height = 24;
      fila.eachCell((cell: ExcelJS.Cell) => {
        cell.font = { size: 10, color: { argb: c.texto } };
        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: index % 2 === 0 ? c.claro : c.alterno } };
        cell.alignment = { vertical: 'middle', horizontal: 'left', wrapText: true };
        cell.border = this.borde(c.borde);
      });
      [2, 5, 6, 7, 8].forEach(col => {
        fila.getCell(col).alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
      });
    });

    worksheet.columns = [
      { key: 'tarea', width: 35 }, { key: 'expediente', width: 20 }, { key: 'responsable', width: 27 },
      { key: 'supervisor', width: 27 }, { key: 'prioridad', width: 14 }, { key: 'estado', width: 18 },
      { key: 'etapa', width: 20 }, { key: 'vencimiento', width: 22 }
    ];
    worksheet.autoFilter = { from: 'A2', to: 'H2' };
    worksheet.headerFooter.oddFooter = '&LLEXIA&CReporte de tareas&RPágina &P de &N';

    const ahora = new Date();
    const nombre = `tareas_${this.archivoFecha(ahora)}.xlsx`;
    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    URL.revokeObjectURL(url);
  }

  private borde(argb: string): Partial<ExcelJS.Borders> {
    return {
      top: { style: 'thin', color: { argb } }, left: { style: 'thin', color: { argb } },
      bottom: { style: 'thin', color: { argb } }, right: { style: 'thin', color: { argb } }
    };
  }

  private texto(valor: string): string {
    return (valor || '').toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase());
  }

  private estado(valor: string): string {
    const estados: Record<string, string> = {
      PENDIENTE: 'Pendiente', EN_PROCESO: 'En proceso', EN_REVISION: 'Revisión', OBSERVADA: 'Observada',
      APROBADA: 'Aprobada', PRESENTADA: 'Presentada', COMPLETADA: 'Completada', CANCELADA: 'Cancelada', VENCIDA: 'Vencida'
    };
    return estados[valor] ?? this.texto(valor);
  }

  private fechaHora(fecha: Date, segundos = false): string {
    if (Number.isNaN(fecha.getTime())) return '';
    return new Intl.DateTimeFormat('es-PE', {
      day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      ...(segundos ? { second: '2-digit' as const } : {}), hour12: false
    }).format(fecha);
  }

  private archivoFecha(fecha: Date): string {
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(fecha.getDate())}-${p(fecha.getMonth() + 1)}-${fecha.getFullYear()}_${p(fecha.getHours())}-${p(fecha.getMinutes())}`;
  }
}
