import { Injectable } from '@angular/core';
import writeExcelFile, { Cell } from 'write-excel-file/browser';

import { ItemReporteDetalle } from '../../../core/services/reporte.service';

export interface ContextoExcelReportes {
  fechaInicio?: string;
  fechaFin?: string;
  materia?: string;
  abogado?: string;
}

@Injectable({ providedIn: 'root' })
export class ReportesExcelService {

  async exportar(
    expedientes: ItemReporteDetalle[],
    contexto: ContextoExcelReportes
  ): Promise<void> {
    const ahora = new Date();
    const filas: Cell[][] = [];

    filas.push([
      {
        value: `LEXIA - REPORTE DEL ESTUDIO - ${this.fechaHora(ahora, true)}`,
        fontWeight: 'bold',
        fontSize: 16,
        textColor: '#FFFFFF',
        backgroundColor: '#0F172A',
        align: 'center',
        alignVertical: 'center',
        columnSpan: 11,
        height: 32,
        wrap: true
      },
      null, null, null, null, null, null, null, null, null, null
    ]);

    filas.push([
      {
        value:
          `Periodo: ${this.fechaTexto(contexto.fechaInicio)} - ${this.fechaTexto(contexto.fechaFin)}` +
          `   |   Materia: ${contexto.materia || 'Todas las materias'}` +
          `   |   Abogado: ${contexto.abogado || 'Todos'}` +
          `   |   Registros: ${expedientes.length}`,
        fontSize: 10,
        textColor: '#334155',
        backgroundColor: '#E2E8F0',
        align: 'left',
        alignVertical: 'center',
        columnSpan: 11,
        height: 25,
        wrap: true,
        borderColor: '#CBD5E1',
        borderStyle: 'thin'
      },
      null, null, null, null, null, null, null, null, null, null
    ]);

    filas.push([
      this.celdaEncabezado('ID'),
      this.celdaEncabezado('Código / N° Expediente'),
      this.celdaEncabezado('Carátula / Título'),
      this.celdaEncabezado('Cliente'),
      this.celdaEncabezado('Contraparte'),
      this.celdaEncabezado('Materia'),
      this.celdaEncabezado('Abogado Responsable'),
      this.celdaEncabezado('Estado'),
      this.celdaEncabezado('Riesgo'),
      this.celdaEncabezado('Fecha Inicio'),
      this.celdaEncabezado('Cuantía (S/)')
    ]);

    if (expedientes.length === 0) {
      filas.push([
        {
          value: 'No existen expedientes para los filtros seleccionados.',
          fontSize: 10,
          textColor: '#64748B',
          backgroundColor: '#F8FAFC',
          align: 'center',
          alignVertical: 'center',
          columnSpan: 11,
          height: 28,
          borderColor: '#CBD5E1',
          borderStyle: 'thin'
        },
        null, null, null, null, null, null, null, null, null, null
      ]);
    } else {
      expedientes.forEach((item, index) => {
        const fondo = index % 2 === 0 ? '#EEF2FF' : '#F8FAFC';
        filas.push([
          this.celdaDato(item.id, fondo, 'center'),
          this.celdaDato(item.codigo, fondo, 'center'),
          this.celdaDato(item.titulo, fondo, 'left'),
          this.celdaDato(item.cliente, fondo, 'left'),
          this.celdaDato(item.contraparte, fondo, 'left'),
          this.celdaDato(item.materia, fondo, 'left'),
          this.celdaDato(item.abogado, fondo, 'left'),
          this.celdaDato(this.texto(item.estado), fondo, 'center'),
          this.celdaDato(this.texto(item.nivelRiesgo), fondo, 'center'),
          this.celdaDato(item.fechaInicio, fondo, 'center'),
          this.celdaDato(this.moneda(item.cuantia), fondo, 'right')
        ]);
      });
    }

    const archivo = writeExcelFile(filas, {
      sheet: 'Reporte Lexia',
      columns: [
        { width: 10 },
        { width: 22 },
        { width: 38 },
        { width: 28 },
        { width: 28 },
        { width: 24 },
        { width: 28 },
        { width: 18 },
        { width: 14 },
        { width: 16 },
        { width: 18 }
      ],
      orientation: 'landscape',
      stickyRowsCount: 3,
      showGridLines: false
    });

    await archivo.toFile(`Reporte_Estudio_Lexia_${this.archivoFecha(ahora)}.xlsx`);
  }

  private celdaEncabezado(texto: string): Cell {
    return {
      value: texto,
      fontWeight: 'bold',
      fontSize: 11,
      textColor: '#FFFFFF',
      backgroundColor: '#6366F1',
      align: 'center',
      alignVertical: 'center',
      height: 28,
      wrap: true,
      borderColor: '#4338CA',
      borderStyle: 'thin'
    };
  }

  private celdaDato(
    valor: string | number | null | undefined,
    fondo: string,
    alineacion: 'left' | 'center' | 'right'
  ): Cell {
    return {
      value: valor ?? '',
      fontSize: 10,
      textColor: '#1E293B',
      backgroundColor: fondo,
      align: alineacion,
      alignVertical: 'center',
      height: 26,
      wrap: true,
      borderColor: '#CBD5E1',
      borderStyle: 'thin'
    };
  }

  private texto(valor: string | null | undefined): string {
    if (!valor) return '';
    return valor
      .toLowerCase()
      .replaceAll('_', ' ')
      .replace(/^./, letra => letra.toUpperCase());
  }

  private moneda(valor: number | null | undefined): string {
    const numero = Number(valor ?? 0);
    return `S/ ${numero.toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })}`;
  }

  private fechaTexto(valor?: string): string {
    if (!valor) return 'Sin límite';
    const partes = valor.split('-');
    return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : valor;
  }

  private fechaHora(fecha: Date, segundos = false): string {
    return new Intl.DateTimeFormat('es-PE', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      ...(segundos ? { second: '2-digit' as const } : {}),
      hour12: false
    }).format(fecha);
  }

  private archivoFecha(fecha: Date): string {
    const p = (numero: number) => String(numero).padStart(2, '0');
    return `${fecha.getFullYear()}-${p(fecha.getMonth() + 1)}-${p(fecha.getDate())}_${p(fecha.getHours())}-${p(fecha.getMinutes())}`;
  }
}
