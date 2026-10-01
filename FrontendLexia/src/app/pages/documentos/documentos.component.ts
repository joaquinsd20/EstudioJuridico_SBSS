import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import {
  ChangeDetectorRef,
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { finalize, forkJoin, Observable } from 'rxjs';

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
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-documentos',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './documentos.component.html',
  styleUrl: './documentos.component.css'
})
export class DocumentosComponent implements OnInit, OnDestroy {
  private readonly documentoService = inject(DocumentoService);
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly cdr = inject(ChangeDetectorRef);


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
  pagina = 0;
  tamanoPagina = 20;
  readonly tamanosPagina = [10, 20, 40, 100];
  hayPaginaSiguiente = false;
  private solicitudListado = 0;
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

  readonly maxTamanoArchivoMb = 250;
  private readonly maxTamanoArchivoBytes = this.maxTamanoArchivoMb * 1024 * 1024;

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
  modalVersiones = false;
  modalSubirVersion = false;
  modalPapelera = false;
  modalConfirmarVersion = false;
  versionPendienteRestaurar: DocumentoVersion | null = null;

  // Confirmaciones dentro de LEXIA (sin ventanas nativas del navegador).
  modalConfirmarPapelera = false;
  documentoPendientePapelera: Documento | null = null;
  modalConfirmarEliminacion = false;
  documentoPendienteEliminar: Documento | null = null;

  // Menú contextual único de los tres puntos.
  menuAccionesDocumento: Documento | null = null;
  menuAccionesTop = 0;
  menuAccionesLeft = 0;
  private menuAccionesAnchor: HTMLElement | null = null;
  private menuScrollRaf: number | null = null;

  /**
   * Captura el scroll de cualquier contenedor (incluida la tabla).
   * Reposiciona el menú para que permanezca pegado al botón •••.
   */
  private readonly onAnyScroll = (): void => {
    if (!this.menuAccionesDocumento || !this.menuAccionesAnchor) return;
    if (this.menuScrollRaf !== null) return;

    this.menuScrollRaf = window.requestAnimationFrame(() => {
      this.menuScrollRaf = null;
      this.posicionarMenuAcciones();
    });
  };

  archivoNuevo: File | null = null;
  archivoVersion: File | null = null;
  observacionVersion = '';

  formNuevo: DocumentoCrear = this.nuevoFormulario();
  formEditar: DocumentoActualizar | null = null;

  visorPdfUrl: SafeResourceUrl | null = null;
  visorImagenUrl = '';
  visorMediaUrl = '';
  visorHtml = '';
  visorTipo: 'pdf' | 'imagen' | 'audio' | 'video' | 'html' | '' = '';
  visorCargando = false;
  private visorObjectUrl = '';
  private mensajeTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    // scroll no burbujea normalmente; con capture=true también detectamos
    // el desplazamiento interno de .table-wrapper.
    document.addEventListener('scroll', this.onAnyScroll, true);
    this.cargarTodo();
  }

  ngOnDestroy(): void {
    document.removeEventListener('scroll', this.onAnyScroll, true);
    if (this.menuScrollRaf !== null) {
      window.cancelAnimationFrame(this.menuScrollRaf);
      this.menuScrollRaf = null;
    }
    this.liberarVisor();
    if (this.mensajeTimer) clearTimeout(this.mensajeTimer);
  }

  cargarTodo(): void {
    const solicitud = ++this.solicitudListado;
    this.pagina = 0;
    this.cargando = true;
    this.error = '';
    forkJoin({
      documentos: this.listarPagina(),
      recientes: this.documentoService.recientes(6),
      papelera: this.documentoService.papelera(),
      estadisticas: this.documentoService.estadisticas(),
      expedientes: this.documentoService.expedientes(),
      creadores: this.documentoService.creadores()
    }).subscribe({
      next: r => {
        if (solicitud !== this.solicitudListado) return;
        this.asignarPagina(r.documentos);
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
        if (solicitud !== this.solicitudListado) return;
        this.error = this.mensajeError(e);
        this.cargando = false;
        this.actualizarVista();
      }
    });
  }

  buscar(): void {
    this.pagina = 0;
    this.cargarPagina();
  }

  cambiarTamanoPagina(nuevoTamano: number): void {
    if (this.cargando || !this.tamanosPagina.includes(nuevoTamano) || nuevoTamano === this.tamanoPagina) return;
    this.tamanoPagina = nuevoTamano;
    this.pagina = 0;
    this.cerrarMenuAcciones();
    this.cargarPagina();
  }

  cambiarPagina(nuevaPagina: number): void {
    if (this.cargando || nuevaPagina < 0 || (nuevaPagina > this.pagina && !this.hayPaginaSiguiente)) return;
    this.pagina = nuevaPagina;
    this.cerrarMenuAcciones();
    this.cargarPagina();
  }

  private listarPagina(): Observable<Documento[]> {
    let params = new HttpParams()
      .set('limite', this.tamanoPagina + 1)
      .set('desplazamiento', this.pagina * this.tamanoPagina);
    Object.entries(this.filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== '') {
        params = params.set(clave, String(valor));
      }
    });
    return this.http.get<Documento[]>(`${environment.apiUrl}/documentos`, { params });
  }

  private asignarPagina(documentos: Documento[]): void {
    this.hayPaginaSiguiente = documentos.length > this.tamanoPagina;
    this.documentos = documentos.slice(0, this.tamanoPagina);
  }

  private cargarPagina(): void {
    const solicitud = ++this.solicitudListado;
    this.cargando = true;
    this.listarPagina().subscribe({
      next: docs => {
        if (solicitud !== this.solicitudListado) return;
        this.asignarPagina(docs);
        this.seleccionado = this.documentos[0] ?? null;
        this.cargando = false;
        this.actualizarVista();
      },
      error: e => {
        if (solicitud !== this.solicitudListado) return;
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

  /**
   * Abre un único menú flotante asociado al botón de tres puntos.
   * El menú está fuera del flujo de la tabla, pero su posición se recalcula
   * desde el botón mientras la tabla se desplaza.
   */
  toggleMenuAcciones(event: MouseEvent, documento: Documento): void {
    event.stopPropagation();

    if (this.menuAccionesDocumento?.uuid === documento.uuid) {
      this.cerrarMenuAcciones();
      return;
    }

    const boton = event.currentTarget as HTMLElement | null;
    if (!boton) return;

    this.menuAccionesDocumento = documento;
    this.menuAccionesAnchor = boton;

    // Primera posición inmediata para evitar que el menú aparezca en 0,0.
    this.posicionarMenuAcciones();
    this.actualizarVista();

    // Una vez renderizado, usamos sus dimensiones reales.
    window.requestAnimationFrame(() => {
      if (!this.menuAccionesDocumento || this.menuAccionesAnchor !== boton) return;
      this.posicionarMenuAcciones();
    });
  }

  /** Mantiene el menú pegado al botón ••• que lo abrió. */
  private posicionarMenuAcciones(): void {
    const boton = this.menuAccionesAnchor;
    if (!boton || !boton.isConnected || !this.menuAccionesDocumento) {
      this.cerrarMenuAcciones();
      return;
    }

    const rect = boton.getBoundingClientRect();
    const contenedor = boton.closest('.table-wrapper') as HTMLElement | null;

    // Si la fila salió del área visible de la tabla, el menú deja de tener
    // un ancla visible y se cierra.
    if (contenedor) {
      const area = contenedor.getBoundingClientRect();
      const altoScrollbar = Math.max(0, contenedor.offsetHeight - contenedor.clientHeight);
      const anchoScrollbar = Math.max(0, contenedor.offsetWidth - contenedor.clientWidth);
      const limiteInferior = area.bottom - altoScrollbar;
      const limiteDerecho = area.right - anchoScrollbar;

      const visible =
        rect.bottom > area.top &&
        rect.top < limiteInferior &&
        rect.right > area.left &&
        rect.left < limiteDerecho;

      if (!visible) {
        this.cerrarMenuAcciones();
        return;
      }
    }

    const menu = document.querySelector('.action-menu-floating') as HTMLElement | null;
    const anchoMenu = menu?.offsetWidth || 240;
    const altoMenu = menu?.offsetHeight || 342;
    const margenPantalla = 10;
    const separacion = 6;

    // Calculamos el rectángulo visible donde debe vivir el menú.
    // Si existe la tabla, lo mantenemos sobre esa tabla y nunca debajo
    // de su scrollbar horizontal.
    let limiteIzquierdo = margenPantalla;
    let limiteDerecho = window.innerWidth - margenPantalla;
    let limiteSuperior = margenPantalla;
    let limiteInferior = window.innerHeight - margenPantalla;

    if (contenedor) {
      const area = contenedor.getBoundingClientRect();
      const altoScrollbar = Math.max(0, contenedor.offsetHeight - contenedor.clientHeight);
      const anchoScrollbar = Math.max(0, contenedor.offsetWidth - contenedor.clientWidth);

      limiteIzquierdo = Math.max(limiteIzquierdo, area.left + 4);
      limiteDerecho = Math.min(limiteDerecho, area.right - anchoScrollbar - 4);
      limiteSuperior = Math.max(limiteSuperior, area.top + 4);
      limiteInferior = Math.min(limiteInferior, area.bottom - altoScrollbar - 4);
    }

    // Borde derecho del menú alineado con el borde derecho del botón.
    let left = rect.right - anchoMenu;
    const maxLeft = limiteDerecho - anchoMenu;
    left = maxLeft >= limiteIzquierdo
      ? Math.max(limiteIzquierdo, Math.min(left, maxLeft))
      : Math.max(margenPantalla, Math.min(left, window.innerWidth - anchoMenu - margenPantalla));

    const espacioAbajo = limiteInferior - rect.bottom - separacion;
    const espacioArriba = rect.top - limiteSuperior - separacion;

    let top: number;
    if (espacioAbajo >= altoMenu) {
      // Hay espacio: abre justo debajo de los tres puntos.
      top = rect.bottom + separacion;
    } else if (espacioArriba >= altoMenu) {
      // No cabe abajo: abre justo encima de los tres puntos.
      top = rect.top - altoMenu - separacion;
    } else {
      // Si no cabe completo ni arriba ni abajo, lo superponemos a las filas
      // manteniendo el menú dentro del área visible de la tabla.
      const minTop = limiteSuperior;
      const maxTop = limiteInferior - altoMenu;

      if (maxTop >= minTop) {
        top = Math.max(minTop, Math.min(rect.top, maxTop));
      } else {
        // Solo ocurre si la tabla es más baja que el propio menú.
        top = Math.max(
          margenPantalla,
          Math.min(rect.top, window.innerHeight - altoMenu - margenPantalla)
        );
      }
    }

    this.menuAccionesLeft = Math.round(left);
    this.menuAccionesTop = Math.round(top);
    this.actualizarVista();
  }

  cerrarMenuAcciones(): void {
    if (!this.menuAccionesDocumento && !this.menuAccionesAnchor) return;

    this.menuAccionesDocumento = null;
    this.menuAccionesAnchor = null;

    if (this.menuScrollRaf !== null) {
      window.cancelAnimationFrame(this.menuScrollRaf);
      this.menuScrollRaf = null;
    }

    this.actualizarVista();
  }

  /** Cierra el menú apenas se hace clic fuera del botón o de sus opciones. */
  @HostListener('document:pointerdown', ['$event'])
  cerrarMenuAlClickFuera(event: PointerEvent): void {
    if (!this.menuAccionesDocumento) return;
    const target = event.target as HTMLElement | null;
    if (target?.closest('.action-menu-floating') || target?.closest('.action-menu-trigger')) return;
    this.cerrarMenuAcciones();
  }

  @HostListener('document:keydown.escape')
  cerrarMenuConEscape(): void {
    this.cerrarMenuAcciones();
  }

  @HostListener('window:resize')
  reposicionarMenuAlRedimensionar(): void {
    if (!this.menuAccionesDocumento) return;
    this.posicionarMenuAcciones();
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
    const archivo = input.files?.[0] ?? null;

    if (!archivo) {
      this.archivoNuevo = null;
      return;
    }

    const validacion = this.validarArchivoSeleccionado(archivo, this.formNuevo.categoria);
    if (validacion) {
      input.value = '';
      this.archivoNuevo = null;
      this.mostrarError(validacion);
      return;
    }

    this.archivoNuevo = archivo;
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

    const validacionArchivo = this.validarArchivoSeleccionado(
      this.archivoNuevo,
      this.formNuevo.categoria
    );
    if (validacionArchivo) {
      this.mostrarError(validacionArchivo);
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

    if (ext === 'pdf' || ['png', 'jpg', 'jpeg', 'mp3', 'wav', 'mp4'].includes(ext)) {
      this.documentoService.visualizar(documento.uuid).subscribe({
        next: blob => {
          this.visorObjectUrl = URL.createObjectURL(blob);
          if (ext === 'pdf') {
            this.visorTipo = 'pdf';
            this.visorPdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.visorObjectUrl);
          } else if (['png', 'jpg', 'jpeg'].includes(ext)) {
            this.visorTipo = 'imagen';
            this.visorImagenUrl = this.visorObjectUrl;
          } else if (['mp3', 'wav'].includes(ext)) {
            this.visorTipo = 'audio';
            this.visorMediaUrl = this.visorObjectUrl;
          } else {
            this.visorTipo = 'video';
            this.visorMediaUrl = this.visorObjectUrl;
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
    const archivo = input.files?.[0] ?? null;
    const categoria = this.seleccionado?.categoria ?? 'OTRO';

    if (!archivo) {
      this.archivoVersion = null;
      return;
    }

    const validacion = this.validarArchivoSeleccionado(archivo, categoria);
    if (validacion) {
      input.value = '';
      this.archivoVersion = null;
      this.mostrarError(validacion);
      return;
    }

    this.archivoVersion = archivo;
  }

  subirVersion(): void {
    if (!this.seleccionado || !this.archivoVersion) {
      this.mostrarError('Selecciona el archivo de la nueva versión.');
      return;
    }

    const validacionArchivo = this.validarArchivoSeleccionado(
      this.archivoVersion,
      this.seleccionado.categoria
    );
    if (validacionArchivo) {
      this.mostrarError(validacionArchivo);
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

  regresarVersion(version: DocumentoVersion): void {
    if (!this.seleccionado || version.vigente || this.guardando) return;

    this.versionPendienteRestaurar = version;
    this.modalConfirmarVersion = true;
    this.actualizarVista();
  }

  cancelarRegresoVersion(): void {
    if (this.guardando) return;
    this.modalConfirmarVersion = false;
    this.versionPendienteRestaurar = null;
    this.actualizarVista();
  }

  confirmarRegresoVersion(): void {
    const version = this.versionPendienteRestaurar;
    const documento = this.seleccionado;

    if (!documento || !version || version.vigente || this.guardando) return;

    const uuid = documento.uuid;
    this.guardando = true;

    this.documentoService.restaurarVersion(uuid, version.id)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: nueva => {
          this.modalConfirmarVersion = false;
          this.versionPendienteRestaurar = null;
          this.mostrarExito(`Versión v${version.numeroVersion} recuperada como nueva versión v${nueva.numeroVersion}.`);

          this.documentoService.versiones(uuid).subscribe({
            next: versiones => {
              this.versiones = versiones;
              this.actualizarVista();
            },
            error: e => this.mostrarError(this.mensajeError(e))
          });

          this.cargarTodo();
        },
        error: e => this.mostrarError(this.mensajeError(e))
      });
  }

  async descargar(documento = this.seleccionado): Promise<void> {
    if (!documento) return;

    this.limpiarMensajes();
    const nombre = documento.nombreArchivo ?? `${documento.nombre}.${documento.extension ?? 'pdf'}`;

    const destino = await this.solicitarDestinoDescarga(nombre);
    if (destino.cancelado) return;

    this.documentoService.descargar(documento.uuid).subscribe({
      next: async blob => {
        try {
          const confirmado = await this.guardarBlob(blob, nombre, destino.handle);
          if (confirmado) {
            this.mostrarExito('Documento descargado correctamente.');
          }
        } catch (e) {
          this.mostrarError(this.mensajeErrorDescarga(e));
        }
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  async exportar(documento: Documento | null, formato: 'pdf' | 'docx'): Promise<void> {
    if (!documento) return;

    this.limpiarMensajes();
    const nombre = `${this.nombreArchivoSeguro(documento.nombre)}.${formato}`;

    const destino = await this.solicitarDestinoDescarga(nombre);
    if (destino.cancelado) return;

    this.documentoService.exportar(documento.uuid, formato).subscribe({
      next: async blob => {
        try {
          const confirmado = await this.guardarBlob(blob, nombre, destino.handle);
          if (confirmado) {
            this.mostrarExito(`Documento exportado a ${formato === 'pdf' ? 'PDF' : 'Word'} correctamente.`);
          }
        } catch (e) {
          this.mostrarError(this.mensajeErrorDescarga(e));
        }
      },
      error: e => this.mostrarError(this.mensajeError(e))
    });
  }

  enviarPapelera(documento: Documento): void {
    if (this.guardando) return;
    this.documentoPendientePapelera = documento;
    this.modalConfirmarPapelera = true;
    this.actualizarVista();
  }

  cancelarEnvioPapelera(): void {
    if (this.guardando) return;
    this.modalConfirmarPapelera = false;
    this.documentoPendientePapelera = null;
    this.actualizarVista();
  }

  confirmarEnvioPapelera(): void {
    const documento = this.documentoPendientePapelera;
    if (!documento || this.guardando) return;

    this.guardando = true;
    this.documentoService.enviarPapelera(documento.uuid)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: () => {
          this.modalConfirmarPapelera = false;
          this.documentoPendientePapelera = null;
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
    if (this.guardando) return;
    this.documentoPendienteEliminar = documento;
    this.modalConfirmarEliminacion = true;
    this.actualizarVista();
  }

  cancelarEliminacionDefinitiva(): void {
    if (this.guardando) return;
    this.modalConfirmarEliminacion = false;
    this.documentoPendienteEliminar = null;
    this.actualizarVista();
  }

  confirmarEliminacionDefinitiva(): void {
    const documento = this.documentoPendienteEliminar;
    if (!documento || this.guardando) return;

    this.guardando = true;
    this.documentoService.eliminarDefinitivamente(documento.uuid)
      .pipe(finalize(() => {
        this.guardando = false;
        this.actualizarVista();
      }))
      .subscribe({
        next: () => {
          this.modalConfirmarEliminacion = false;
          this.documentoPendienteEliminar = null;
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

  formatosPermitidos(categoria: CategoriaDocumento): string[] {
    if (categoria === 'MULTIMEDIA') {
      return ['jpg', 'jpeg', 'png', 'mp3', 'wav', 'mp4'];
    }
    if (categoria === 'PRUEBA' || categoria === 'OTRO') {
      return ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png', 'mp3', 'wav', 'mp4'];
    }
    return ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png'];
  }

  formatosPermitidosTexto(categoria: CategoriaDocumento): string {
    return this.formatosPermitidos(categoria).map(ext => ext.toUpperCase()).join(', ');
  }

  acceptParaCategoria(categoria: CategoriaDocumento): string {
    return this.formatosPermitidos(categoria).map(ext => `.${ext}`).join(',');
  }

  textoConversionCategoria(categoria: CategoriaDocumento): string {
    const formatos = this.formatosPermitidos(categoria);
    const partes: string[] = [];

    if (formatos.includes('doc') || formatos.includes('docx')) {
      partes.push('Word se convierte a PDF');
    }
    if (formatos.some(ext => ['jpg', 'jpeg', 'png'].includes(ext))) {
      partes.push('las imágenes se convierten a PDF');
    }
    if (formatos.some(ext => ['mp3', 'wav', 'mp4'].includes(ext))) {
      partes.push('audio y video se conservan en su formato original');
    }

    return partes.join(' · ');
  }

  puedeExportar(documento: Documento | null): boolean {
    const ext = (documento?.extension ?? '').toLowerCase();
    return !['mp3', 'wav', 'mp4'].includes(ext);
  }

  private archivoPermitido(archivo: File, categoria: CategoriaDocumento): boolean {
    const nombre = archivo.name.toLowerCase();
    const punto = nombre.lastIndexOf('.');
    if (punto < 0 || punto === nombre.length - 1) return false;
    const extension = nombre.substring(punto + 1);
    return this.formatosPermitidos(categoria).includes(extension);
  }


  private validarArchivoSeleccionado(archivo: File, categoria: CategoriaDocumento): string | null {
    if (archivo.size <= 0) {
      return 'El archivo está vacío.';
    }

    if (archivo.size > this.maxTamanoArchivoBytes) {
      return `El archivo pesa ${this.tamano(archivo.size)} y supera el máximo permitido de ${this.maxTamanoArchivoMb} MB.`;
    }

    if (!this.archivoPermitido(archivo, categoria)) {
      return (
        `Formato no permitido para ${this.etiqueta(categoria)}. ` +
        `Permitidos: ${this.formatosPermitidosTexto(categoria)}.`
      );
    }

    return null;
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

  /**
   * Abre primero el selector de destino del navegador (Chrome/Edge).
   * De esta manera podemos distinguir entre Guardar y Cancelar.
   * Si el navegador no soporta File System Access API, usamos la descarga
   * tradicional, pero NO mostramos un mensaje de "descargado correctamente"
   * porque el navegador no informa si el usuario canceló posteriormente.
   */
  private async solicitarDestinoDescarga(nombre: string): Promise<{ cancelado: boolean; handle?: any }> {
    const selector = (window as any).showSaveFilePicker;

    if (typeof selector !== 'function') {
      return { cancelado: false };
    }

    try {
      const handle = await selector.call(window, {
        suggestedName: nombre
      });
      return { cancelado: false, handle };
    } catch (e: any) {
      if (e?.name === 'AbortError') {
        // El usuario pulsó "Cancelar": no descargamos y no mostramos éxito.
        return { cancelado: true };
      }
      throw e;
    }
  }

  private async guardarBlob(blob: Blob, nombre: string, handle?: any): Promise<boolean> {
    // Navegadores Chromium: podemos confirmar que el archivo sí se escribió.
    if (handle) {
      const writable = await handle.createWritable();
      try {
        await writable.write(blob);
      } finally {
        await writable.close();
      }
      return true;
    }

    // Fallback para navegadores sin showSaveFilePicker.
    // La descarga se inicia, pero JavaScript no puede saber si el usuario
    // cancela después el diálogo nativo del navegador; por eso devolvemos false
    // y evitamos mostrar un falso mensaje de éxito.
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    return false;
  }

  private mensajeErrorDescarga(error: unknown): string {
    if ((error as any)?.name === 'AbortError') {
      return 'La descarga fue cancelada.';
    }
    return 'No se pudo guardar el archivo.';
  }

  private liberarVisor(): void {
    if (this.visorObjectUrl) URL.revokeObjectURL(this.visorObjectUrl);
    this.visorObjectUrl = '';
    this.visorPdfUrl = null;
    this.visorImagenUrl = '';
    this.visorMediaUrl = '';
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
      const mensajeBackend = error.error?.mensaje || error.error?.detail || error.error?.message;
      if (mensajeBackend) return mensajeBackend;

      if (error.status === 413) {
        return `El archivo es demasiado grande. El tamaño máximo permitido es ${this.maxTamanoArchivoMb} MB.`;
      }

      if (error.status === 0) {
        return 'No se pudo conectar con el servidor. Verifica que el backend esté ejecutándose.';
      }

      return `No se pudo completar la operación (${error.status || 'sin conexión'}).`;
    }
    return 'Ocurrió un error inesperado.';
  }
}
