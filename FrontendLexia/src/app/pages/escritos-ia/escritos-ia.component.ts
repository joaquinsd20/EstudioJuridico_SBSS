import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PanelComponent } from '../panel/panel.component';
import {
  EscritosIaService,
  EscritoGenerado,
  TipoEscrito,
  ExportarEscritoRequest
} from '../../core/services/escritos-ia.service';
import { ExpedienteService } from '../../core/services/expediente.service';

interface ExpedienteOpcion {
  uuid: string;
  codigoInterno: string;
  numeroExpediente: string;
  cliente: string;
  responsable: string;
  esConfidencial: boolean;
}

type FormatoExportacion = 'word' | 'pdf';

@Component({
  selector: 'app-escritos-ia',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './escritos-ia.component.html',
  styleUrl: './escritos-ia.component.css'
})
export class EscritosIaComponent {
  private readonly escritosSvc = inject(EscritosIaService);
  private readonly expedienteSvc = inject(ExpedienteService);

  readonly textoBusquedaExp = signal('');
  readonly resultadosExp = signal<ExpedienteOpcion[]>([]);
  readonly buscandoExp = signal(false);
  readonly expedienteSeleccionado = signal<ExpedienteOpcion | null>(null);
  readonly tipos = signal<TipoEscrito[]>([]);
  readonly tipoSeleccionado = signal('ESCRITO');
  readonly instrucciones = signal('');
  readonly generando = signal(false);
  readonly resultado = signal<EscritoGenerado | null>(null);
  readonly contenidoEditado = signal('');
  readonly exportando = signal<FormatoExportacion | null>(null);
  readonly error = signal<string | null>(null);
  readonly exito = signal<string | null>(null);
  readonly copiado = signal(false);

  readonly puedeGenerar = computed(() =>
    !!this.expedienteSeleccionado() && !!this.tipoSeleccionado() && !this.generando()
  );
  readonly puedeExportar = computed(() =>
    !!this.resultado() && !!this.contenidoEditado().trim() && !this.exportando()
  );

  constructor() {
    this.escritosSvc.tipos().subscribe(tipos => this.tipos.set(tipos ?? []));
  }

  cambiarBusquedaExp(valor: string): void {
    this.textoBusquedaExp.set(valor);
    const q = valor.trim();
    if (q.length < 2) {
      this.resultadosExp.set([]);
      this.buscandoExp.set(false);
      return;
    }
    this.buscarExpedientes(q);
  }

  private buscarExpedientes(q: string): void {
    this.buscandoExp.set(true);
    this.expedienteSvc.buscar(q, 15).pipe(finalize(() => this.buscandoExp.set(false))).subscribe({
      next: r => this.resultadosExp.set((r ?? []) as ExpedienteOpcion[]),
      error: () => this.resultadosExp.set([])
    });
  }

  seleccionarExpediente(expediente: ExpedienteOpcion): void {
    this.expedienteSeleccionado.set(expediente);
    this.textoBusquedaExp.set('');
    this.resultadosExp.set([]);
    this.error.set(null);
    this.exito.set(null);
  }

  limpiarExpediente(): void {
    this.expedienteSeleccionado.set(null);
    this.resultado.set(null);
    this.contenidoEditado.set('');
    this.error.set(null);
    this.exito.set(null);
  }

  generar(): void {
    const expediente = this.expedienteSeleccionado();
    if (!expediente || this.generando()) return;

    this.generando.set(true);
    this.error.set(null);
    this.exito.set(null);
    this.resultado.set(null);
    this.contenidoEditado.set('');

    this.escritosSvc.generar({
      expedienteUuid: expediente.uuid,
      tipoEscrito: this.tipoSeleccionado(),
      instrucciones: this.instrucciones().trim()
    }).pipe(finalize(() => this.generando.set(false))).subscribe({
      next: escrito => {
        this.resultado.set(escrito);
        this.contenidoEditado.set(escrito.contenido ?? '');
        this.exito.set(escrito.generadoConIa
          ? 'Borrador generado correctamente con inteligencia artificial. Revisa su contenido antes de utilizarlo.'
          : 'Se generó una plantilla local porque la IA no estuvo disponible. Revisa la configuración del proveedor.');
      },
      error: err => this.error.set(this.mensajeError(err,
        'No se pudo generar el escrito. Verifica la conexión con el backend e inténtalo nuevamente.'))
    });
  }

  copiar(): void {
    const texto = this.contenidoEditado();
    if (!texto.trim()) return;
    navigator.clipboard.writeText(texto).then(() => {
      this.copiado.set(true);
      window.setTimeout(() => this.copiado.set(false), 1800);
    }).catch(() => this.error.set('No se pudo copiar el contenido. Comprueba los permisos del navegador.'));
  }

  descargarWord(): void {
    this.descargar('word');
  }

  descargarPdf(): void {
    this.descargar('pdf');
  }

  private descargar(formato: FormatoExportacion): void {
    const escrito = this.resultado();
    const contenido = this.contenidoEditado().trim();
    if (!escrito || !contenido || this.exportando()) return;

    const request: ExportarEscritoRequest = { titulo: escrito.titulo, contenido };
    this.exportando.set(formato);
    this.error.set(null);
    this.exito.set(null);

    const peticion = formato === 'word'
      ? this.escritosSvc.exportarWord(request)
      : this.escritosSvc.exportarPdf(request);

    peticion.pipe(finalize(() => this.exportando.set(null))).subscribe({
      next: blob => {
        if (!blob || blob.size === 0) {
          this.error.set(`El servidor devolvió un archivo ${formato.toUpperCase()} vacío.`);
          return;
        }
        const url = URL.createObjectURL(blob);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `${this.nombreSeguro(escrito.titulo)}.${formato === 'word' ? 'docx' : 'pdf'}`;
        enlace.style.display = 'none';
        document.body.appendChild(enlace);
        enlace.click();
        enlace.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        this.exito.set(`Documento ${formato === 'word' ? 'Word' : 'PDF'} generado y descargado correctamente.`);
      },
      error: err => this.error.set(this.mensajeError(err,
        `No se pudo generar el archivo ${formato.toUpperCase()}. Inténtalo nuevamente.`))
    });
  }

  private nombreSeguro(titulo: string): string {
    const limpio = (titulo || 'escrito-lexia')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9._-]+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^[._-]+|[._-]+$/g, '')
      .slice(0, 90);
    return limpio || 'escrito-lexia';
  }

  private mensajeError(error: unknown, fallback: string): string {
    const err = error as { error?: { detail?: string; message?: string; title?: string } };
    return err?.error?.detail || err?.error?.message || err?.error?.title || fallback;
  }
}
