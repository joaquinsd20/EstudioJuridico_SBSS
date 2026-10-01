import { RecursoJuridico } from '../../core/models/biblioteca-juridica.models';
import { BibliotecaJuridicaService } from '../../core/services/biblioteca-juridica.service';
import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PanelComponent } from '../panel/panel.component';

/**
 * La base guarda CODIGOS en MAYUSCULAS (convencion del proyecto: se puede
 * renombrar la etiqueta sin migrar filas). Estos mapas los traducen a lo que
 * ve el usuario. Si llega un codigo no listado se muestra tal cual, para que
 * un valor nuevo sea visible en vez de desaparecer.
 */
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



@Component({
  selector: 'app-biblioteca-juridica',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    PanelComponent
  ],
  templateUrl: './biblioteca-juridica.component.html',
  styleUrl: './biblioteca-juridica.component.css'
})
export class BibliotecaJuridicaComponent implements OnInit {

  /** La base guarda codigos; la pantalla muestra la etiqueta. */
  etiquetaTipo(codigo: string): string { return ETIQUETA_TIPO[codigo] ?? codigo; }
  etiquetaCategoria(codigo: string): string { return ETIQUETA_CATEGORIA[codigo] ?? codigo; }
  etiquetaEstado(codigo: string): string { return ETIQUETA_ESTADO[codigo] ?? codigo; }

  

  recursos: RecursoJuridico[] = [];
  recursosFiltrados: RecursoJuridico[] = [];

  filtros = {
    busqueda: '',
    tipo: '',
    categoria: '',
    estado: '',
    conArchivo: ''
  };

  mostrarModalCrear: boolean = false;
  mostrarModalDetalle: boolean = false;
  recursoDetalle?: RecursoJuridico;

  nuevoRecurso: Partial<RecursoJuridico> = {};
  archivoSeleccionado: File | null = null;

  // Inyectamos ChangeDetectorRef
  constructor(
    private service: BibliotecaJuridicaService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.filtros = {
      busqueda: '',
      tipo: '',
      categoria: '',
      estado: '',
      conArchivo: ''
    };

    this.obtenerRecursos();
  }

  // =========================================================
  // OBTENER RECURSOS (CON DETECCIÓN DE CAMBIOS FORZADA)
  // =========================================================
  obtenerRecursos(): void {
    this.service.listar().subscribe({
      next: (response) => {
        const data = Array.isArray(response) ? response : response.content;
        this.recursos = Array.isArray(data) ? data : [];
        
        // Copia directa e inmediata para la vista
        this.recursosFiltrados = [...this.recursos];

        // Reaplicar filtros si hay búsquedas activas
        this.aplicarFiltros();

        // Forzar a Angular a pintar los datos inmediatamente en el HTML
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al cargar recursos:', err);
      }
    });
  }

  // =========================================================
  // FILTROS
  // =========================================================
  aplicarFiltros(): void {
    if (!this.recursos || this.recursos.length === 0) {
      this.recursosFiltrados = [];
      return;
    }

    this.recursosFiltrados = this.recursos.filter(item => {
      const busquedaLower = this.filtros.busqueda ? this.filtros.busqueda.toLowerCase().trim() : '';
      
      const coincideTexto = !busquedaLower ||
        (item.titulo && item.titulo.toLowerCase().includes(busquedaLower)) ||
        (item.subtituloDescripcion && item.subtituloDescripcion.toLowerCase().includes(busquedaLower)) ||
        (item.expedienteCodigo && item.expedienteCodigo.toLowerCase().includes(busquedaLower));

      const coincideTipo = !this.filtros.tipo || item.tipo === this.filtros.tipo;
      const coincideCategoria = !this.filtros.categoria || item.categoria === this.filtros.categoria;
      const coincideEstado = !this.filtros.estado || item.estado === this.filtros.estado;
      
      const coincideArchivo = !this.filtros.conArchivo ||
        (this.filtros.conArchivo === 'Si' && !!item.nombreArchivo) ||
        (this.filtros.conArchivo === 'No' && !item.nombreArchivo);

      return coincideTexto && coincideTipo && coincideCategoria && coincideEstado && coincideArchivo;
    });
  }

  get totalRecursos(): number { return this.recursos.length; }
  get totalActivos(): number { return this.recursos.filter(r => r.estado === 'ACTIVO').length; }
  get totalConArchivo(): number { return this.recursos.filter(r => r.nombreArchivo && r.nombreArchivo.trim() !== '').length; }
  get totalCategorias(): number { return new Set(this.recursos.map(r => r.categoria)).size; }

  // =========================================================
  // MODALES Y SELECCIÓN DE ARCHIVOS
  // =========================================================
  abrirModalCrear(): void {
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
  }

  seleccionarArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.archivoSeleccionado = input.files[0];
      this.nuevoRecurso.nombreArchivo = this.archivoSeleccionado.name;
    }
  }

  quitarArchivo(): void {
    this.archivoSeleccionado = null;
    this.nuevoRecurso.nombreArchivo = '';
  }

  // =========================================================
  // GUARDAR RECURSO
  // =========================================================
  guardarNuevoRecurso(): void {
    if (!this.nuevoRecurso.titulo?.trim()) {
      alert('Por favor, ingresa el título.');
      return;
    }

  // Validación para exigir tipo y categoría
    if (!this.nuevoRecurso.tipo) {
      alert('Debes seleccionar un Tipo.');
      return;
    }

    if (!this.nuevoRecurso.categoria) {
      alert('Debes seleccionar una Categoría.');
      return;
    }

    // 3. Validar Archivo Obligatorio
    if (!this.archivoSeleccionado) {
      alert('No estas subiendo ningún archivo existente, por favor selecciona uno');
      return;
    }

    const formData = new FormData();

    const dtoData = {
      titulo: this.nuevoRecurso.titulo,
      subtituloDescripcion: this.nuevoRecurso.subtituloDescripcion || '',
      tipo: this.nuevoRecurso.tipo || 'DOCTRINA',
      categoria: this.nuevoRecurso.categoria || 'DERECHO_SOCIAL',
      anio: this.nuevoRecurso.anio || new Date().getFullYear(),
      expedienteCodigo: this.nuevoRecurso.expedienteCodigo || '',
      estado: this.nuevoRecurso.estado || 'ACTIVO'
    };

    const dataBlob = new Blob([JSON.stringify(dtoData)], { type: 'application/json' });
    formData.append('data', dataBlob);

    if (this.archivoSeleccionado) {
      formData.append('archivo', this.archivoSeleccionado);
    }

    this.service.crear(formData).subscribe({
      next: () => {
        alert('Recurso agregado correctamente.');
        this.obtenerRecursos();
        this.cerrarModales();
      },
      error: (err) => {
        console.error('Error al guardar el recurso:', err);
        alert('Error al guardar el recurso en el servidor.');
      }
    });
  }

  verDetalle(item: RecursoJuridico): void {
    this.recursoDetalle = item;
    this.mostrarModalDetalle = true;
  }

  // =========================================================
  // VISTA PREVIA
  // =========================================================
  verVistaPrevia(item: RecursoJuridico): void {
    const extension = item.extension?.toLowerCase() || '';
    const nombre = item.nombreArchivo?.toLowerCase() || '';

    if (extension.includes('doc') || nombre.endsWith('.doc') || nombre.endsWith('.docx')) {
      alert('Los documentos en formato Word (.docx) no se pueden previsualizar en el navegador. Se iniciará la descarga.');
      this.descargarDocumento(item.id, item.nombreArchivo || 'documento.docx');
      return;
    }

    const nuevaPestana = window.open('about:blank', '_blank');

    this.service.ver(item.id).subscribe({
      next: (blob: Blob) => {
        if (blob.type === 'application/json') {
          nuevaPestana?.close();
          alert('El archivo no está disponible o ocurrió un error en el servidor.');
          return;
        }

        if (blob.type.includes('wordprocessingml') || blob.type.includes('msword')) {
          nuevaPestana?.close();
          alert('Los documentos en formato Word (.docx) no se pueden previsualizar en el navegador. Se iniciará la descarga.');
          this.descargarDocumento(item.id, item.nombreArchivo || 'documento.docx');
          return;
        }

        const fileBlob = new Blob([blob], { type: 'application/pdf' });
        const fileURL = URL.createObjectURL(fileBlob);

        if (nuevaPestana) {
          nuevaPestana.location.href = fileURL;
        }
      },
      error: (err) => {
        nuevaPestana?.close();
        console.error('Error al abrir la vista previa:', err);
        alert('No se pudo abrir la vista previa. Verifica que el archivo exista en el servidor.');
      }
    });
  }

  // =========================================================
  // DESCARGA
  // =========================================================
  descargarDocumento(id: number, nombreArchivo: string): void {
    this.service.descargar(id).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = nombreArchivo || 'documento';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        console.error('Error al descargar el archivo:', err);
        alert('No se pudo descargar el documento.');
      }
    });
  }

  descargar(item: RecursoJuridico): void {
    if (item.nombreArchivo && item.nombreArchivo.trim() !== '') {
      this.descargarDocumento(item.id, item.nombreArchivo);
    } else {
      alert('Este recurso no tiene un archivo adjunto disponible para descargar.');
    }
  }

  cerrarModales(): void {
    this.mostrarModalCrear = false;
    this.mostrarModalDetalle = false;
    this.recursoDetalle = undefined;
    this.archivoSeleccionado = null;
  }

  obtenerTamanoArchivo(bytes: number): string {
    if (!bytes || bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  // =========================================================
  // ELIMINAR RECURSO
  // =========================================================
  eliminar(item: RecursoJuridico): void {
    const confirmacion = confirm(`¿Estás seguro de eliminar el recurso "${item.titulo}"? Esta acción no se puede deshacer.`);
    
    if (!confirmacion) return;

    this.service.eliminar(item.id).subscribe({
      next: () => {
        alert('Recurso eliminado correctamente.');
        this.cerrarModales();
        this.obtenerRecursos();
      },
      error: (err) => {
        console.error('Error al eliminar el recurso:', err);
        alert('Ocurrió un error al intentar eliminar el recurso.');
      }
    });
  }
}