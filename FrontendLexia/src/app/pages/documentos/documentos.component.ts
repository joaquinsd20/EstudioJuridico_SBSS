import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  inject
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml, SafeResourceUrl } from '@angular/platform-browser';
import { finalize, forkJoin, Subscription } from 'rxjs';

import {
  CategoriaDocumento,
  Documento,
  DocumentoActualizar,
  DocumentoCrear,
  DocumentoEstadisticas,
  DocumentoFiltro,
  DocumentoVersion,
  EstadoDocumento,
  ExpedienteOpcion,
  RelacionOpcion,
  UsuarioOpcion
} from '../../core/models/documento.models';
import { DocumentoService } from '../../core/services/documento.service';
import { PanelComponent } from '../panel/panel.component';

@Component({
  selector: 'app-documentos',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './documentos.component.html',
  styleUrl: './documentos.component.css'
})
export class DocumentosComponent implements OnInit, OnDestroy {
  private readonly documentoService = inject(DocumentoService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly cdr = inject(ChangeDetectorRef);

  @ViewChild('editorDocumento') editorDocumento?: ElementRef<HTMLDivElement>;

  readonly categorias: CategoriaDocumento[] = [
    'DEMANDA', 'CONTESTACION', 'RECURSO', 'ESCRITO', 'RESOLUCION',
    'NOTIFICACION', 'CARGO', 'CONTRATO', 'PODER', 'PRUEBA', 'INFORME',
    'MULTIMEDIA', 'CORREO', 'CLIENTE', 'OTRO'
  ];

  readonly estados: EstadoDocumento[] = [
    'BORRADOR', 'EN_REVISION', 'OBSERVADO', 'APROBADO',
    'FIRMADO', 'PRESENTADO', 'CERRADO'
  ];

  documentos: Documento[] = [];
  recientes: Documento[] = [];
  papelera: Documento[] = [];
  versiones: DocumentoVersion[] = [];
  expedientes: ExpedienteOpcion[] = [];
  creadores: UsuarioOpcion[] = [];
  actuaciones: RelacionOpcion[] = [];
  tareas: RelacionOpcion[] = [];

  estadisticas: DocumentoEstadisticas = {
    total: 0,
    recientes: 0,
    confidenciales: 0,
    papelera: 0
  };

  seleccionado: Documento | null = null;
  cargando = false;
  guardando = false;
  error = '';
  exito = '';

  filtros: DocumentoFiltro = {
    texto: '',
    categoria: '',
    estado: '',
    expedienteId: null,
    clienteId: null,
    creadorId: null,
    extension: '',
    confidencial: null,
    fechaDesde: '',
    fechaHasta: ''
  };

  mostrarFiltrosAvanzados = false;
  modalNuevo = false;
  modalEditarInfo = false;
  modalVisor = false;
  modalEditor = false;
  modalVersiones = false;
  modalSubirVersion = false;
  modalPapelera = false;

  archivoNuevo: File | null = null;
  archivoVersion: File | null = null;
  observacionVersion = '';

  formNuevo: DocumentoCrear = this.nuevoFormulario();
  formEditar: DocumentoActualizar | null = null;

  visorPdfUrl: SafeResourceUrl | null = null;
  visorImagenUrl = '';
  visorHtml = '';
  visorTipo: 'pdf' | 'imagen' | 'html' | '' = '';
  visorCargando = false;
  private visorObjectUrl = '';
  private mensajeTimer: ReturnType<typeof setTimeout> | null = null;

  editorHtml = '';
  editorHtmlSeguro: SafeHtml | null = null;
  editorObservaciones = '';
  editorCargando = false;
  editorError = '';
  editorBloque = 'p';
  editorFuente = 'Arial';
  editorTamano = '3';
  editorColor = '#111827';
  editorEsPdf = false;
  private rangoEditor: Range | null = null;
  private editorCargaSub: Subscription | null = null;

  ngOnInit(): void {
    this.cargarTodo();
  }

  ngOnDestroy(): void {
    this.editorCargaSub?.unsubscribe();
    this.editorCargaSub = null;
    this.liberarVisor();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
  }

  @HostListener('document:keydown.escape')
  onEscapeDocumento(): void {
    // El editor siempre debe poder cerrarse aunque una petición de PDF siga
    // procesándose o el usuario todavía no haya terminado de cargar el contenido.
    if (this.modalEditor) {
      this.cerrarEditor();
    }
  }

  cargarTodo(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      documentos: this.documentoService.listar(this.filtros),
      recientes: this.documentoService.recientes(6),
      papelera: this.documentoService.papelera(),
      estadisticas: this.documentoService.estadisticas(),
      expedientes: this.documentoService.expedientes(),
      creadores: this.documentoService.creadores()
    }).subscribe({
      next: r => {
        this.documentos = r.documentos;
        this.recientes = r.recientes;
        this.papelera = r.papelera;
        this.estadisticas = r.estadisticas;
        this.expedientes = r.expedientes;
        this.creadores = r.creadores;
        if (this.seleccionado) {
          this.seleccionado = this.documentos.find(d => d.uuid === this.seleccionado?.uuid) ?? null;
        }
        if (!this.seleccionado && this.documentos.length) {
          this.seleccionado = this.documentos[0];
        }
        this.cargando = false;
        this.actualizarVista();
      },
      error: e => {
        this.error = this.mensajeError(e);
        this.cargando = false;
        this.actualizarVista();
      }
    });
  }

  buscar(): void {
    this.cargando = true;
    this.documentoService.listar(this.filtros).subscribe({
      next: docs => {
        this.documentos = docs;
        this.seleccionado = docs.length ? docs[0] : null;
        this.cargando = false;
        this.actualizarVista();
      },
      error: e => {
        this.error = this.mensajeError(e);
        this.cargando = false;
        this.actualizarVista();
      }
    });
  }

  limpiarFiltros(): void {
    this.filtros = {
      texto: '', categoria: '', estado: '', expedienteId: null, clienteId: null,
      creadorId: null, extension: '', confidencial: null, fechaDesde: '', fechaHasta: ''
    };
    this.buscar();
  }

  seleccionar(documento: Documento): void {
    this.seleccionado = documento;
  }

  abrirNuevo(): void {
    this.formNuevo = this.nuevoFormulario();
    this.archivoNuevo = null;
    this.actuaciones = [];
    this.tareas = [];
    this.modalNuevo = true;
    this.limpiarMensajes();
  }

  onArchivoNuevo(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.archivoNuevo = input.files?.[0] ?? null;
  }

  cambiarExpedienteNuevo(): void {
    this.formNuevo.actuacionId = null;
    this.formNuevo.tareaId = null;
    this.cargarRelaciones(this.formNuevo.expedienteId);
  }

  guardarNuevo(): void {
    if (!this.formNuevo.expedienteId || !this.formNuevo.nombre.trim() || !this.archivoNuevo) {
      this.mostrarError('Completa expediente, nombre y archivo.');
      return;
    }

    this.guardando = true;
    this.documentoService.crear(this.formNuevo, this.archivoNuevo)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: d => {
          this.modalNuevo = false;
          this.archivoNuevo = null;
          this.formNuevo = this.nuevoFormulario();
          this.seleccionado = d;
          this.mostrarExito('Documento registrado correctamente.');
          this.actualizarVista();
          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  abrirEditarInformacion(documento = this.seleccionado): void {
    if (!documento) return;
    this.seleccionado = documento;
    this.formEditar = {
      expedienteId: documento.expedienteId,
      actuacionId: documento.actuacionId,
      tareaId: documento.tareaId,
      nombre: documento.nombre,
      descripcion: documento.descripcion ?? '',
      categoria: documento.categoria,
      estado: documento.estado,
      esConfidencial: documento.confidencial
    };
    this.cargarRelaciones(documento.expedienteId);
    this.modalEditarInfo = true;
    this.limpiarMensajes();
  }

  cambiarExpedienteEditar(): void {
    if (!this.formEditar) return;
    this.formEditar.actuacionId = null;
    this.formEditar.tareaId = null;
    this.cargarRelaciones(this.formEditar.expedienteId);
  }

  guardarEdicionInformacion(): void {
    if (!this.seleccionado || !this.formEditar) return;

    this.guardando = true;
    this.documentoService.actualizar(this.seleccionado.uuid, this.formEditar)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: d => {
          this.modalEditarInfo = false;
          this.formEditar = null;
          this.seleccionado = d;
          this.mostrarExito('Cambios guardados correctamente.');
          this.actualizarVista();
          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  visualizar(documento = this.seleccionado): void {
    if (!documento) return;

    // Abrimos el visor inmediatamente. Así el primer clic siempre responde
    // aunque el backend tarde unos milisegundos en preparar el documento.
    this.seleccionado = documento;
    this.liberarVisor();
    this.visorHtml = '';
    this.visorCargando = true;
    this.modalVisor = true;
    this.limpiarMensajes();
    this.actualizarVista();

    const ext = (documento.extension ?? '').toLowerCase();

    if (ext === 'pdf' || ['png', 'jpg', 'jpeg'].includes(ext)) {
      this.documentoService.visualizar(documento.uuid).subscribe({
        next: blob => {
          this.visorObjectUrl = URL.createObjectURL(blob);
          if (ext === 'pdf') {
            this.visorTipo = 'pdf';
            this.visorPdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.visorObjectUrl);
          } else {
            this.visorTipo = 'imagen';
            this.visorImagenUrl = this.visorObjectUrl;
          }
          this.visorCargando = false;
          this.actualizarVista();
        },
        error: e => {
          this.visorCargando = false;
          this.modalVisor = false;
          this.mostrarError(this.mensajeError(e));
        }
      });
      return;
    }

    this.documentoService.contenido(documento.uuid).subscribe({
      next: contenido => {
        this.visorTipo = 'html';
        this.visorHtml = contenido.html;
        this.visorCargando = false;
        this.actualizarVista();
      },
      error: e => {
        this.visorCargando = false;
        this.modalVisor = false;
        this.mostrarError(this.mensajeError(e));
      }
    });
  }

  abrirEditor(documento = this.seleccionado): void {
    if (!documento) return;

    // Si quedó una carga anterior viva (muy común al abrir/cerrar PDF), la
    // cancelamos antes de comenzar otra. Así una respuesta pesada antigua no
    // vuelve a insertarse en el DOM después de cerrar el modal.
    this.editorCargaSub?.unsubscribe();
    this.editorCargaSub = null;

    this.seleccionado = documento;
    this.editorEsPdf = (documento.extension ?? '').toLowerCase() === 'pdf';
    this.editorHtml = '';
    this.editorHtmlSeguro = null;
    this.editorObservaciones = '';
    this.editorError = '';
    this.editorCargando = true;
    this.rangoEditor = null;
    this.modalEditor = true;
    this.limpiarMensajes();
    this.actualizarVista();

    this.editorCargaSub = this.documentoService.contenido(documento.uuid).subscribe({
      next: contenido => {
        // Si el usuario cerró mientras llegaba la respuesta, no insertamos
        // contenido ni imágenes en un editor que ya no existe.
        if (!this.modalEditor) return;

        this.editorHtml = contenido.html;
        this.editorHtmlSeguro = this.sanitizer.bypassSecurityTrustHtml(contenido.html);
        this.editorCargando = false;
        this.actualizarVista();
      },
      error: e => {
        if (!this.modalEditor) return;
        this.editorCargando = false;
        this.editorError = this.mensajeError(e);
        this.actualizarVista();
      },
      complete: () => {
        this.editorCargaSub = null;
      }
    });
  }

  cerrarEditor(): void {
    // Cerrar debe ser instantáneo incluso para PDFs grandes.
    this.editorCargaSub?.unsubscribe();
    this.editorCargaSub = null;

    this.modalEditor = false;
    this.editorCargando = false;
    this.editorError = '';
    this.editorHtml = '';
    this.editorHtmlSeguro = null;
    this.editorObservaciones = '';
    this.editorEsPdf = false;
    this.rangoEditor = null;
    this.actualizarVista();
  }

  guardarSeleccionEditor(): void {
    const editor = this.editorDocumento?.nativeElement;
    const selection = window.getSelection();
    if (!editor || !selection || selection.rangeCount === 0) return;

    const range = selection.getRangeAt(0);
    const nodo = range.commonAncestorContainer;
    if (editor === nodo || editor.contains(nodo.nodeType === Node.TEXT_NODE ? nodo.parentNode : nodo)) {
      this.rangoEditor = range.cloneRange();
    }
  }

  private restaurarSeleccionEditor(): void {
    const editor = this.editorDocumento?.nativeElement;
    if (!editor) return;
    editor.focus();
    if (!this.rangoEditor) return;

    const selection = window.getSelection();
    if (!selection) return;
    selection.removeAllRanges();
    selection.addRange(this.rangoEditor);
  }

  aplicarFormato(comando: string, valor?: string): void {
    if (this.editorCargando) return;
    this.restaurarSeleccionEditor();
    document.execCommand('styleWithCSS', false, 'true');
    document.execCommand(comando, false, valor);
    this.normalizarFormatoEditor();
    this.guardarSeleccionEditor();
  }

  aplicarBloque(): void {
    this.aplicarFormato('formatBlock', this.editorBloque);
  }

  aplicarFuente(): void {
    this.aplicarFormato('fontName', this.editorFuente);
  }

  aplicarTamano(): void {
    if (this.editorCargando) return;
    this.restaurarSeleccionEditor();

    // Para tamaño usamos temporalmente etiquetas FONT porque permiten mapear
    // de forma predecible los niveles 1..7 a puntos reales. Inmediatamente
    // después las convertimos a CSS y el FONT queda solo como contenedor.
    document.execCommand('styleWithCSS', false, 'false');
    document.execCommand('fontSize', false, this.editorTamano);

    const editor = this.editorDocumento?.nativeElement;
    if (!editor) return;
    const puntos: Record<string, string> = {
      '1': '8pt', '2': '10pt', '3': '12pt', '4': '14pt',
      '5': '18pt', '6': '24pt', '7': '32pt'
    };
    editor.querySelectorAll('font[size]').forEach(el => {
      const html = el as HTMLElement;
      const size = el.getAttribute('size') || this.editorTamano;
      html.style.fontSize = puntos[size] || '12pt';
      el.removeAttribute('size');
    });

    document.execCommand('styleWithCSS', false, 'true');
    this.guardarSeleccionEditor();
  }

  aplicarColor(): void {
    this.aplicarFormato('foreColor', this.editorColor);
  }

  quitarFormato(): void {
    this.aplicarFormato('removeFormat');
  }

  private normalizarFormatoEditor(): void {
    const editor = this.editorDocumento?.nativeElement;
    if (!editor) return;

    // execCommand puede crear etiquetas FONT. Las convertimos a estilos CSS para
    // que el backend pueda reconstruir correctamente el DOCX.
    editor.querySelectorAll('font').forEach(font => {
      const el = font as HTMLElement;
      const face = font.getAttribute('face');
      const color = font.getAttribute('color');
      if (face) el.style.fontFamily = face;
      if (color) el.style.color = color;
      font.removeAttribute('face');
      font.removeAttribute('color');
    });
  }

  guardarEdicionDocumento(): void {
    if (!this.seleccionado || !this.editorDocumento) return;

    this.normalizarFormatoEditor();
    const html = this.editorDocumento.nativeElement.innerHTML;
    this.guardando = true;
    this.documentoService.guardarContenido(
      this.seleccionado.uuid,
      html,
      this.editorObservaciones
    )
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: version => {
          this.cerrarEditor();
          this.editorObservaciones = '';
          this.mostrarExito(`Documento guardado correctamente. Se creó la versión v${version.numeroVersion}.`);
          this.actualizarVista();
          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  abrirVersiones(documento = this.seleccionado): void {
    if (!documento) return;
    this.seleccionado = documento;
    this.documentoService.versiones(documento.uuid).subscribe({
      next: versiones => {
        this.versiones = versiones;
        this.modalVersiones = true;
        this.actualizarVista();
      },
      error: e => {
        this.error = this.mensajeError(e);
        this.actualizarVista();
      }
    });
  }

  abrirSubirVersion(documento = this.seleccionado): void {
    if (!documento) return;
    this.seleccionado = documento;
    this.archivoVersion = null;
    this.observacionVersion = '';
    this.modalSubirVersion = true;
  }

  onArchivoVersion(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.archivoVersion = input.files?.[0] ?? null;
  }

  subirVersion(): void {
    if (!this.seleccionado || !this.archivoVersion) {
      this.mostrarError('Selecciona el archivo de la nueva versión.');
      return;
    }

    this.guardando = true;
    this.documentoService.subirVersion(
      this.seleccionado.uuid,
      this.archivoVersion,
      this.observacionVersion
    )
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: version => {
          this.modalSubirVersion = false;
          this.archivoVersion = null;
          this.observacionVersion = '';
          this.mostrarExito(`Nueva versión v${version.numeroVersion} guardada correctamente.`);
          this.actualizarVista();
          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  descargar(documento = this.seleccionado): void {
    if (!documento) return;

    this.limpiarMensajes();
    this.documentoService.descargar(documento.uuid).subscribe({
      next: blob => {
        this.guardarBlob(
          blob,
          documento.nombreArchivo ?? `${documento.nombre}.${documento.extension ?? 'bin'}`
        );
        this.mostrarExito('Documento descargado correctamente.');
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  exportar(documento: Documento | null, formato: 'pdf' | 'docx'): void {
    if (!documento) return;
    this.documentoService.exportar(documento.uuid, formato).subscribe({
      next: blob => this.guardarBlob(blob, `${this.nombreArchivoSeguro(documento.nombre)}.${formato}`),
      error: e => {
        this.error = this.mensajeError(e);
        this.actualizarVista();
      }
    });
  }

  enviarPapelera(documento: Documento): void {
    if (!confirm(`¿Enviar "${documento.nombre}" a la papelera? Podrás restaurarlo durante 7 días.`)) return;

    this.guardando = true;
    this.documentoService.enviarPapelera(documento.uuid)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: () => {
          if (this.seleccionado?.uuid === documento.uuid) this.seleccionado = null;
          this.mostrarExito('Documento enviado a la papelera correctamente.');
          this.actualizarVista();
          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  abrirPapelera(): void {
    this.documentoService.papelera().subscribe({
      next: docs => {
        this.papelera = docs;
        this.modalPapelera = true;
        this.actualizarVista();
      },
      error: e => {
        this.error = this.mensajeError(e);
        this.actualizarVista();
      }
    });
  }

  restaurar(documento: Documento): void {
    this.guardando = true;
    this.documentoService.restaurar(documento.uuid)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: () => {
          this.mostrarExito('Documento restaurado correctamente.');
          this.actualizarVista();
          this.refrescarPapelera();
          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  eliminarDefinitivamente(documento: Documento): void {
    if (!confirm(`¿Eliminar definitivamente "${documento.nombre}"? Esta acción no se puede deshacer.`)) return;

    this.guardando = true;
    this.documentoService.eliminarDefinitivamente(documento.uuid)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: () => {
          this.mostrarExito('Documento eliminado definitivamente.');
          this.actualizarVista();
          this.refrescarPapelera();
          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  cerrarVisor(): void {
    this.modalVisor = false;
    this.visorCargando = false;
    this.liberarVisor();
  }

  etiqueta(valor: string | null | undefined): string {
    if (!valor) return '—';
    return valor.replaceAll('_', ' ').toLowerCase().replace(/(^|\s)\S/g, l => l.toUpperCase());
  }

  iniciales(nombre: string | null | undefined): string {
    if (!nombre) return '—';
    return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0]).join('').toUpperCase();
  }

  tamano(bytes: number | null): string {
    if (!bytes) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  diasRestantes(documento: Documento): number {
    if (!documento.fechaPapelera) return 7;
    const fin = new Date(documento.fechaPapelera).getTime() + 7 * 24 * 60 * 60 * 1000;
    return Math.max(0, Math.ceil((fin - Date.now()) / (24 * 60 * 60 * 1000)));
  }

  puedeEditar(documento: Documento | null): boolean {
    return !!documento && ['docx', 'doc', 'pdf', 'txt'].includes((documento.extension ?? '').toLowerCase());
  }

  private nuevoFormulario(): DocumentoCrear {
    return {
      expedienteId: null,
      actuacionId: null,
      tareaId: null,
      nombre: '',
      descripcion: '',
      categoria: 'OTRO',
      estado: 'BORRADOR',
      esConfidencial: false,
      observaciones: ''
    };
  }

  private cargarRelaciones(expedienteId: number | null): void {
    if (!expedienteId) {
      this.actuaciones = [];
      this.tareas = [];
      return;
    }
    forkJoin({
      actuaciones: this.documentoService.actuaciones(expedienteId),
      tareas: this.documentoService.tareas(expedienteId)
    }).subscribe({
      next: r => {
        this.actuaciones = r.actuaciones;
        this.tareas = r.tareas;
        this.actualizarVista();
      },
      error: () => {
        this.actuaciones = [];
        this.tareas = [];
        this.actualizarVista();
      }
    });
  }

  private refrescarPapelera(): void {
    this.documentoService.papelera().subscribe({
      next: docs => {
        this.papelera = docs;
        this.actualizarVista();
      },
      error: e => {
        this.error = this.mensajeError(e);
        this.actualizarVista();
      }
    });
  }

  private guardarBlob(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private liberarVisor(): void {
    if (this.visorObjectUrl) URL.revokeObjectURL(this.visorObjectUrl);
    this.visorObjectUrl = '';
    this.visorPdfUrl = null;
    this.visorImagenUrl = '';
    this.visorTipo = '';
  }

  private nombreArchivoSeguro(nombre: string): string {
    return nombre.replace(/[<>:"/\\|?*]/g, '_').trim() || 'documento';
  }

  private limpiarMensajes(): void {
    this.error = '';
    this.exito = '';
    if (this.mensajeTimer) {
      clearTimeout(this.mensajeTimer);
      this.mensajeTimer = null;
    }
  }

  private mostrarExito(mensaje: string): void {
    this.error = '';
    this.exito = mensaje;
    this.actualizarVista();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
    this.mensajeTimer = setTimeout(() => {
      this.exito = '';
      this.mensajeTimer = null;
      this.actualizarVista();
    }, 4500);
  }

  private mostrarError(mensaje: string): void {
    this.exito = '';
    this.error = mensaje;
    this.actualizarVista();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
    this.mensajeTimer = setTimeout(() => {
      this.error = '';
      this.mensajeTimer = null;
      this.actualizarVista();
    }, 6500);
  }

  private actualizarVista(): void {
    // En modo zoneless las respuestas HTTP/setTimeout no siempre refrescan
    // propiedades normales hasta el siguiente clic. Esto actualiza la vista
    // inmediatamente después de cada respuesta asíncrona.
    try {
      this.cdr.detectChanges();
    } catch {
      // El componente pudo destruirse mientras terminaba una petición.
    }
  }

  private mensajeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return error.error?.mensaje || error.error?.detail || error.error?.message ||
        `No se pudo completar la operación (${error.status || 'sin conexión'}).`;
    }
    return 'Ocurrió un error inesperado.';
  }
}
