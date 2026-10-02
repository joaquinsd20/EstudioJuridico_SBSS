import { Injectable } from '@angular/core';
import writeExcelFile, { Cell } from 'write-excel-file/browser';
import { TareaExcelFila } from '../../../core/models/tarea.models';

@Injectable({
  providedIn: 'root'
})
export class TareasExcelService {

  async exportar(tareas: TareaExcelFila[]): Promise<void> {
    if (!tareas || tareas.length === 0) {
      return;
    }

    const ahora = new Date();

    const nombreArchivo =
      `tareas_${this.archivoFecha(ahora)}.xlsx`;

    const filas: Cell[][] = [];

    // =========================================================
    // TÍTULO
    // =========================================================

    filas.push([
      {
        value: `LEXIA - REPORTE DE TAREAS - ${this.fechaHora(ahora, true)}`,
        fontWeight: 'bold',
        fontSize: 16,
        textColor: '#FFFFFF',
        backgroundColor: '#0F172A',
        align: 'center',
        alignVertical: 'center',
        columnSpan: 8,
        height: 32,
        wrap: true
      },
      null,
      null,
      null,
      null,
      null,
      null,
      null
    ]);

    // =========================================================
    // ENCABEZADOS
    // =========================================================

    filas.push([
      this.celdaEncabezado('Tarea'),
      this.celdaEncabezado('Expediente'),
      this.celdaEncabezado('Responsable'),
      this.celdaEncabezado('Supervisor'),
      this.celdaEncabezado('Prioridad'),
      this.celdaEncabezado('Estado'),
      this.celdaEncabezado('Etapa'),
      this.celdaEncabezado('Vencimiento')
    ]);

    // =========================================================
    // DATOS
    // =========================================================

    tareas.forEach((tarea, index) => {

      const fondo =
        index % 2 === 0
          ? '#EEF2FF'
          : '#F8FAFC';

      filas.push([
        this.celdaDato(
          tarea.titulo,
          fondo,
          'left'
        ),

        this.celdaDato(
          tarea.expedienteCodigo,
          fondo,
          'center'
        ),

        this.celdaDato(
          tarea.responsable,
          fondo,
          'left'
        ),

        this.celdaDato(
          tarea.supervisor || '—',
          fondo,
          'left'
        ),

        this.celdaDato(
          this.texto(tarea.prioridad),
          fondo,
          'center'
        ),

        this.celdaDato(
          this.estado(tarea.estado),
          fondo,
          'center'
        ),

        this.celdaDato(
          this.texto(tarea.etapaFlujo),
          fondo,
          'center'
        ),

        this.celdaDato(
          this.fechaHora(
            new Date(tarea.fechaLimite)
          ),
          fondo,
          'center'
        )
      ]);
    });

    // =========================================================
    // GENERAR EXCEL
    // =========================================================

    const archivo = writeExcelFile(
      filas,
      {
        sheet: 'Tareas',

        columns: [
          { width: 35 }, // Tarea
          { width: 20 }, // Expediente
          { width: 27 }, // Responsable
          { width: 27 }, // Supervisor
          { width: 14 }, // Prioridad
          { width: 18 }, // Estado
          { width: 20 }, // Etapa
          { width: 22 }  // Vencimiento
        ],

        orientation: 'landscape',

        stickyRowsCount: 2,

        showGridLines: false
      }
    );

    await archivo.toFile(nombreArchivo);
  }

  // ===========================================================
  // ESTILOS
  // ===========================================================

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

      height: 24,

      wrap: true,

      borderColor: '#CBD5E1',

      borderStyle: 'thin'
    };
  }

  // ===========================================================
  // TEXTO
  // ===========================================================

  private texto(valor: string | null | undefined): string {
    if (!valor) {
      return '';
    }

    return valor
      .toLowerCase()
      .replaceAll('_', ' ')
      .replace(/^./, letra =>
        letra.toUpperCase()
      );
  }

  // ===========================================================
  // ESTADOS
  // ===========================================================

  private estado(valor: string): string {

    const estados: Record<string, string> = {
      PENDIENTE: 'Pendiente',
      EN_PROCESO: 'En proceso',
      EN_REVISION: 'Revisión',
      OBSERVADA: 'Observada',
      APROBADA: 'Aprobada',
      PRESENTADA: 'Presentada',
      COMPLETADA: 'Completada',
      CANCELADA: 'Cancelada',
      VENCIDA: 'Vencida'
    };

    return estados[valor] ?? this.texto(valor);
  }

  // ===========================================================
  // FECHA Y HORA
  // ===========================================================

  private fechaHora(
    fecha: Date,
    segundos = false
  ): string {

    if (Number.isNaN(fecha.getTime())) {
      return '';
    }

    return new Intl.DateTimeFormat(
      'es-PE',
      {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',

        hour: '2-digit',
        minute: '2-digit',

        ...(segundos
          ? {
              second: '2-digit' as const
            }
          : {}),

        hour12: false
      }
    ).format(fecha);
  }

  // ===========================================================
  // NOMBRE DEL ARCHIVO
  // ===========================================================

  private archivoFecha(fecha: Date): string {

    const p = (numero: number) =>
      String(numero).padStart(2, '0');

    return (
      `${p(fecha.getDate())}-` +
      `${p(fecha.getMonth() + 1)}-` +
      `${fecha.getFullYear()}_` +
      `${p(fecha.getHours())}-` +
      `${p(fecha.getMinutes())}`
    );
  }
}