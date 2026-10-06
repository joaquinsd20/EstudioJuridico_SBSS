import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  ViewChild,
  inject
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';

import {
  BibliotecaEstadisticas,
  FiltrosBiblioteca,
  RecursoJuridico
} from '../../core/models/biblioteca-juridica.models';
import { BibliotecaJuridicaService } from '../../core/services/biblioteca-juridica.service';
import { PanelComponent } from '../panel/panel.component';

export const ETIQUETA_TIPO: Record<string, string> = {
  JURISPRUDENCIA: 'Jurisprudencia',
  LEGISLACION: 'Legislación',
  DOCTRINA: 'Doctrina',
  CONTRATO: 'Contrato',
  MODELO: 'Modelo',
  PLANTILLA: 'Plantilla',
  FORMATO: 'Formato',
  LIBRO: 'Libro'
};

export const ETIQUETA_CATEGORIA: Record<string, string> = {
  DERECHO_CIVIL: 'Derecho Civil',
  DERECHO_PENAL: 'Derecho Penal',
  DERECHO_LABORAL: 'Derecho Laboral',
  DERECHO_CONSTITUCIONAL: 'Derecho Constitucional',
  DERECHO_TRIBUTARIO: 'Derecho Tributario',
  DERECHO_SOCIAL: 'Derecho Social',
  DERECHO_MIGRATORIO: 'Derecho Migratorio',
  DERECHO_ADMINISTRATIVO: 'Derecho Administrativo',
  PROCESAL: 'Procesal',
  GENERAL: 'General'
};

export const ETIQUETA_ESTADO: Record<string, string> = {
  ACTIVO: 'Activo',
  ARCHIVADO: 'Archivado'
};

type TipoAvisoSistema = 'EXITO' | 'ERROR' | 'ADVERTENCIA' | 'INFO';

interface AvisoSistema {
  tipo: TipoAvisoSistema;
  titulo: string;
  mensaje: string;
}

@Component({
  selector: 'app-biblioteca-juridica',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './biblioteca-juridica.component.html',
  styleUrl: './biblioteca-juridica.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BibliotecaJuridicaComponent implements OnInit {
  private readonly service = inject(BibliotecaJuridicaService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cambiosFiltro$ = new Subject<void>();

  private readonly extensionesPermitidas = new Set([
    'pdf',
    'doc',
    'docx',
    'xls',
    'xlsx',
    'ppt',
    'pptx'
  ]);

  private readonly maxArchivoBytes = 50 * 1024 * 1024;
  private temporizadorAviso: ReturnType<typeof setTimeout> | null = null;

  @ViewChild('archivoInput') archivoInput?: ElementRef<HTMLInputElement>;

  recursos: RecursoJuridico[] = [];
  estadisticas: BibliotecaEstadisticas = {
    recursosTotales: 0,
    recursosActivos: 0,
    conArchivo: 0,
    totalCategorias: 0
  };

  filtros: FiltrosBiblioteca = this.filtrosIniciales();

  pagina = 0;
  tamanoPagina = 20;
  readonly tamanosPagina = [10, 20, 40, 100];
  totalPaginas = 0;
  totalElementos = 0;

  cargando = false;
  guardando = false;
  eliminando = false;
  error = '';

  avisoSistema: AvisoSistema | null = null;

  mostrarModalCrear = false;
  mostrarModalDetalle = false;
  mostrarConfirmacionEliminar = false;

  recursoDetalle?: RecursoJuridico;
  recursoPendienteEliminar?: RecursoJuridico;

  nuevoRecurso: Partial<RecursoJuridico> = {};
  archivoSeleccionado: File | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.temporizadorAviso) {
        clearTimeout(this.temporizadorAviso);
      }
    });
  }

  ngOnInit(): void {
    this.cambiosFiltro$
      .pipe(
        debounceTime(300),
        switchMap(() => {
          this.cargando = true;
          this.error = '';
          this.cdr.markForCheck();

          return this.service.listar(this.filtros, this.pagina, this.tamanoPagina).pipe(
            catchError(err => {
              console.error('Error al cargar recursos:', err);
              this.error = 'No se pudo cargar la biblioteca jurídica.';
              this.cargando = false;
              this.cdr.markForCheck();
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(respuesta => {
        if (!respuesta) return;
        this.aplicarPagina(respuesta);
      });

    this.cargarEstadisticas();
    this.cambiosFiltro$.next();
  }

  etiquetaTipo(codigo: string | null | undefined): string {
    return codigo ? (ETIQUETA_TIPO[codigo] ?? codigo) : '—';
  }

  etiquetaCategoria(codigo: string | null | undefined): string {
    return codigo ? (ETIQUETA_CATEGORIA[codigo] ?? codigo) : '—';
  }

  etiquetaEstado(codigo: string | null | undefined): string {
    return codigo ? (ETIQUETA_ESTADO[codigo] ?? codigo) : '—';
  }

  get totalRecursos(): number { return this.estadisticas.recursosTotales; }
  get totalActivos(): number { return this.estadisticas.recursosActivos; }
  get totalConArchivo(): number { return this.estadisticas.conArchivo; }
  get totalCategorias(): number { return this.estadisticas.totalCategorias; }

  get recursosDestacados(): RecursoJuridico[] {
    return this.recursos.slice(0, 6);
  }

  get desdeRegistro(): number {
    return this.totalElementos === 0 ? 0 : this.pagina * this.tamanoPagina + 1;
  }

  get hastaRegistro(): number {
    return Math.min((this.pagina + 1) * this.tamanoPagina, this.totalElementos);
  }

  aplicarFiltros(): void {
    this.pagina = 0;
    this.cambiosFiltro$.next();
  }

  limpiarFiltros(): void {
    this.filtros = this.filtrosIniciales();
    this.pagina = 0;
    this.cambiosFiltro$.next();
  }

  cambiarTamanoPagina(valor: number | string): void {
    const tamano = Number(valor);
    if (!this.tamanosPagina.includes(tamano) || tamano === this.tamanoPagina) return;
    this.tamanoPagina = tamano;
    this.pagina = 0;
    this.cargarPagina();
  }

  cambiarPagina(delta: number): void {
    const nueva = this.pagina + delta;
    if (this.cargando || nueva < 0 || nueva >= this.totalPaginas) return;
    this.pagina = nueva;
    this.cargarPagina();
  }

  trackByUuid(_: number, item: RecursoJuridico): string {
    return item.uuid;
  }

  abrirModalCrear(): void {
    this.cerrarAviso();
    this.nuevoRecurso = {
      titulo: '',
      subtituloDescripcion: '',
      tipo: '',
      categoria: '',
      anio: new Date().getFullYear(),
      expedienteCodigo: '',
      estado: 'ACTIVO'
    };
    this.archivoSeleccionado = null;
    this.mostrarModalCrear = true;
    this.cdr.markForCheck();
  }

  seleccionarArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0] ?? null;

    if (!archivo) {
      this.quitarArchivo();
      return;
    }

    const errorArchivo = this.validarArchivoCliente(archivo);
    if (errorArchivo) {
      this.archivoSeleccionado = null;
      this.nuevoRecurso.nombreArchivo = null;
      input.value = '';
      this.mostrarAviso(
        'ERROR',
        'Archivo rechazado',
        errorArchivo,
        7000
      );
      return;
    }

    this.archivoSeleccionado = archivo;
    this.nuevoRecurso.nombreArchivo = archivo.name;
    this.cerrarAviso();
    this.cdr.markForCheck();
  }

  quitarArchivo(): void {
    this.archivoSeleccionado = null;
    this.nuevoRecurso.nombreArchivo = null;
    if (this.archivoInput?.nativeElement) {
      this.archivoInput.nativeElement.value = '';
    }
    this.cdr.markForCheck();
  }

  guardarNuevoRecurso(): void {
    if (this.guardando) return;

    if (!this.nuevoRecurso.titulo?.trim()) {
      this.mostrarAviso('ADVERTENCIA', 'Falta información', 'Por favor, ingresa el título.');
      return;
    }
    if (!this.nuevoRecurso.tipo) {
      this.mostrarAviso('ADVERTENCIA', 'Falta información', 'Debes seleccionar un tipo.');
      return;
    }
    if (!this.nuevoRecurso.categoria) {
      this.mostrarAviso('ADVERTENCIA', 'Falta información', 'Debes seleccionar una categoría.');
      return;
    }
    if (!this.archivoSeleccionado) {
      this.mostrarAviso('ADVERTENCIA', 'Documento requerido', 'Selecciona un archivo permitido para el recurso.');
      return;
    }

    const errorArchivo = this.validarArchivoCliente(this.archivoSeleccionado);
    if (errorArchivo) {
      this.mostrarAviso('ERROR', 'Archivo rechazado', errorArchivo, 7000);
      this.quitarArchivo();
      return;
    }

    const formData = new FormData();
    const dtoData = {
      titulo: this.nuevoRecurso.titulo.trim(),
      subtituloDescripcion: this.nuevoRecurso.subtituloDescripcion || '',
      tipo: this.nuevoRecurso.tipo,
      categoria: this.nuevoRecurso.categoria,
      anio: this.nuevoRecurso.anio || new Date().getFullYear(),
      expedienteCodigo: this.nuevoRecurso.expedienteCodigo || '',
      estado: this.nuevoRecurso.estado || 'ACTIVO'
    };

    formData.append(
      'data',
      new Blob([JSON.stringify(dtoData)], { type: 'application/json' })
    );
    formData.append('archivo', this.archivoSeleccionado, this.archivoSeleccionado.name);

    this.guardando = true;
    this.cdr.markForCheck();

    this.service.crear(formData)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.guardando = false;
          this.cerrarModales();
          this.mostrarAviso('EXITO', 'Recurso registrado', 'El recurso jurídico fue agregado correctamente.');
          this.pagina = 0;
          this.cargarPagina();
          this.cargarEstadisticas();
        },
        error: err => {
          this.guardando = false;
          console.error('Error al guardar el recurso:', err);
          this.mostrarAviso(
            'ERROR',
            'No se pudo registrar el recurso',
            this.mensajeHttp(err, 'El servidor rechazó el recurso. Verifica el archivo e inténtalo nuevamente.'),
            8000
          );
          this.cdr.markForCheck();
        }
      });
  }

  verDetalle(item: RecursoJuridico): void {
    this.cerrarAviso();
    this.service.obtener(item.uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detalle => {
          this.recursoDetalle = detalle;
          this.mostrarModalDetalle = true;
          this.cdr.markForCheck();
        },
        error: err => {
          console.error('Error al cargar el detalle:', err);
          this.mostrarAviso(
            'ERROR',
            'Detalle no disponible',
            this.mensajeHttp(err, 'No se pudo cargar el detalle del recurso.')
          );
        }
      });
  }

  verVistaPrevia(item: RecursoJuridico): void {
    // El backend siempre responde un PDF para vista previa:
    // - PDF -> se sirve directamente.
    // - DOC/DOCX/XLS/XLSX/PPT/PPTX -> se convierte a PDF y se cachea.
    // La descarga conserva el archivo original.
    const nuevaPestana = window.open('about:blank', '_blank');

    this.service.ver(item.uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: blob => {
          const pdf = blob.type === 'application/pdf'
            ? blob
            : new Blob([blob], { type: 'application/pdf' });

          const url = URL.createObjectURL(pdf);

          if (nuevaPestana) {
            nuevaPestana.location.href = url;
          } else {
            // Si el navegador bloqueó el popup, abrimos el PDF al terminar la petición.
            window.open(url, '_blank', 'noopener,noreferrer');
          }

          setTimeout(() => URL.revokeObjectURL(url), 5 * 60_000);
        },
        error: err => {
          nuevaPestana?.close();
          console.error('Error al abrir la vista previa:', err);
          this.mostrarAviso(
            'ERROR',
            'Vista previa no disponible',
            this.mensajeHttp(
              err,
              'No se pudo convertir o abrir el documento como PDF.'
            )
          );
        }
      });
  }

  descargarDocumento(uuid: string, nombreArchivo: string): void {
    this.service.descargar(uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: blob => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = nombreArchivo || 'documento';
          document.body.appendChild(a);
          a.click();
          a.remove();
          URL.revokeObjectURL(url);
          this.mostrarAviso('EXITO', 'Descarga iniciada', 'El documento se descargó correctamente.');
        },
        error: err => {
          console.error('Error al descargar el archivo:', err);
          this.mostrarAviso(
            'ERROR',
            'Descarga no disponible',
            this.mensajeHttp(err, 'No se pudo descargar el documento.')
          );
        }
      });
  }

  descargar(item: RecursoJuridico): void {
    if (!item.nombreArchivo?.trim()) {
      this.mostrarAviso(
        'ADVERTENCIA',
        'Sin documento adjunto',
        'Este recurso no tiene un archivo disponible para descargar.'
      );
      return;
    }
    this.descargarDocumento(item.uuid, item.nombreArchivo);
  }

  eliminar(item: RecursoJuridico): void {
    if (this.eliminando) return;
    this.cerrarAviso();
    this.recursoPendienteEliminar = item;
    this.mostrarConfirmacionEliminar = true;
    this.cdr.markForCheck();
  }

  cancelarEliminacion(): void {
    if (this.eliminando) return;
    this.mostrarConfirmacionEliminar = false;
    this.recursoPendienteEliminar = undefined;
    this.cerrarAviso();
    this.cdr.markForCheck();
  }

  confirmarEliminacion(): void {
    const item = this.recursoPendienteEliminar;
    if (!item || this.eliminando) return;

    this.eliminando = true;
    this.cdr.markForCheck();

    this.service.eliminar(item.uuid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.eliminando = false;
          this.mostrarConfirmacionEliminar = false;
          this.recursoPendienteEliminar = undefined;
          this.cerrarModales();

          if (this.recursos.length === 1 && this.pagina > 0) {
            this.pagina--;
          }

          this.mostrarAviso(
            'EXITO',
            'Recurso retirado',
            'El recurso fue retirado del catálogo y se conserva para trazabilidad.'
          );
          this.cargarPagina();
          this.cargarEstadisticas();
        },
        error: err => {
          this.eliminando = false;
          console.error('Error al retirar el recurso:', err);
          this.mostrarAviso(
            'ERROR',
            'No se pudo retirar el recurso',
            this.mensajeHttp(err, 'Ocurrió un error al intentar retirar el recurso.')
          );
          this.cdr.markForCheck();
        }
      });
  }

  cerrarModales(): void {
    if (this.guardando || this.eliminando) return;
    this.mostrarModalCrear = false;
    this.mostrarModalDetalle = false;
    this.recursoDetalle = undefined;
    this.archivoSeleccionado = null;
    if (this.archivoInput?.nativeElement) {
      this.archivoInput.nativeElement.value = '';
    }
    this.cdr.markForCheck();
  }

  cerrarAviso(): void {
    if (this.temporizadorAviso) {
      clearTimeout(this.temporizadorAviso);
      this.temporizadorAviso = null;
    }
    this.avisoSistema = null;
    this.cdr.markForCheck();
  }

  obtenerTamanoArchivo(bytes: number): string {
    if (!bytes) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1);
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
  }

  private validarArchivoCliente(archivo: File): string | null {
    const extension = this.extensionDe(archivo.name);

    if (!extension || !this.extensionesPermitidas.has(extension)) {
      return 'Formato no permitido. Solo se aceptan PDF, DOC, DOCX, XLS, XLSX, PPT y PPTX.';
    }

    if (archivo.size <= 0) {
      return 'El archivo está vacío. Selecciona un documento válido.';
    }

    if (archivo.size > this.maxArchivoBytes) {
      return 'El archivo supera el límite de 50 MB.';
    }

    const tipo = (archivo.type || '').toLowerCase();
    const tiposPeligrosos = [
      'php',
      'javascript',
      'text/html',
      'x-msdownload',
      'x-sh',
      'x-shellscript'
    ];

    if (tiposPeligrosos.some(valor => tipo.includes(valor))) {
      return 'El tipo real informado por el navegador no corresponde a un documento permitido.';
    }

    return null;
  }

  private extensionDe(nombre: string): string {
    const limpio = (nombre || '').trim().toLowerCase();
    const punto = limpio.lastIndexOf('.');
    return punto >= 0 && punto + 1 < limpio.length
      ? limpio.substring(punto + 1)
      : '';
  }

  private mostrarAviso(
    tipo: TipoAvisoSistema,
    titulo: string,
    mensaje: string,
    duracionMs = 5000
  ): void {
    if (this.temporizadorAviso) {
      clearTimeout(this.temporizadorAviso);
    }

    this.avisoSistema = { tipo, titulo, mensaje };
    this.cdr.markForCheck();

    this.temporizadorAviso = setTimeout(() => {
      this.avisoSistema = null;
      this.temporizadorAviso = null;
      this.cdr.markForCheck();
    }, duracionMs);
  }

  private mensajeHttp(err: any, fallback: string): string {
    const cuerpo = err?.error;

    if (typeof cuerpo === 'string' && cuerpo.trim()) {
      return cuerpo.trim();
    }

    const mensaje =
      cuerpo?.detail ||
      cuerpo?.message ||
      cuerpo?.error ||
      err?.message;

    return typeof mensaje === 'string' && mensaje.trim()
      ? mensaje.trim()
      : fallback;
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.error = '';
    this.cdr.markForCheck();

    this.service.listar(this.filtros, this.pagina, this.tamanoPagina)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: respuesta => this.aplicarPagina(respuesta),
        error: err => {
          console.error('Error al cargar recursos:', err);
          this.cargando = false;
          this.error = 'No se pudo cargar la biblioteca jurídica.';
          this.cdr.markForCheck();
        }
      });
  }

  private cargarEstadisticas(): void {
    this.service.estadisticas()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: estadisticas => {
          this.estadisticas = estadisticas;
          this.cdr.markForCheck();
        },
        error: err => console.error('Error al cargar estadísticas:', err)
      });
  }

  private aplicarPagina(respuesta: {
    contenido: RecursoJuridico[];
    pagina: number;
    tamano: number;
    totalElementos: number;
    totalPaginas: number;
  }): void {
    this.recursos = respuesta.contenido ?? [];
    this.pagina = respuesta.pagina;
    this.tamanoPagina = respuesta.tamano;
    this.totalElementos = respuesta.totalElementos;
    this.totalPaginas = respuesta.totalPaginas;
    this.cargando = false;
    this.cdr.markForCheck();
  }

  private filtrosIniciales(): FiltrosBiblioteca {
    return {
      busqueda: '',
      tipo: '',
      categoria: '',
      estado: '',
      conArchivo: ''
    };
  }
}
