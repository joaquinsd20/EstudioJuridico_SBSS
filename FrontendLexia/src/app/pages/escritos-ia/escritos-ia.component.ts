import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PanelComponent } from '../panel/panel.component';
import { AuthService } from '../../core/services/auth.service';
import { EscritosIaService, EscritoGenerado, TipoEscrito } from '../../core/services/escritos-ia.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { TemaService } from '../../core/services/tema.service';

interface ExpedienteOpcion {
  uuid: string;
  codigoInterno: string;
  numeroExpediente: string;
  cliente: string;
  responsable: string;
  esConfidencial: boolean;
}

@Component({
  selector: 'app-escritos-ia',
  standalone: true,
  imports: [CommonModule, FormsModule, PanelComponent],
  templateUrl: './escritos-ia.component.html',
  styleUrl: './escritos-ia.component.css'
})
export class EscritosIaComponent {
  readonly temaSvc = inject(TemaService);
  readonly auth = inject(AuthService);
  private readonly escritosSvc = inject(EscritosIaService);
  private readonly expedienteSvc = inject(ExpedienteService);

  readonly temaOscuro = this.temaSvc.oscuro;

  // ─── Expediente seleccionado ───────────────────────────────────────────────
  readonly textoBusquedaExp = signal('');
  readonly resultadosExp = signal<ExpedienteOpcion[]>([]);
  readonly buscandoExp = signal(false);
  readonly expedienteSeleccionado = signal<ExpedienteOpcion | null>(null);

  // ─── Formulario ─────────────────────────────────────────────────────────────
  readonly tipos = signal<TipoEscrito[]>([]);
  readonly tipoSeleccionado = signal<string>('ESCRITO');
  readonly instrucciones = signal('');

  // ─── Resultado ──────────────────────────────────────────────────────────────
  readonly generando = signal(false);
  readonly resultado = signal<EscritoGenerado | null>(null);
  readonly contenidoEditado = signal('');
  readonly error = signal<string | null>(null);
  readonly copiado = signal(false);

  readonly puedeGenerar = computed(() =>
    !!this.expedienteSeleccionado() && !!this.tipoSeleccionado() && !this.generando());

  constructor() {
    this.escritosSvc.tipos().subscribe(t => this.tipos.set(t));
  }

  buscarExpedientes(): void {
    const q = this.textoBusquedaExp().trim();
    if (q.length < 2) {
      this.resultadosExp.set([]);
      return;
    }
    this.buscandoExp.set(true);
    this.expedienteSvc.buscar(q, 15).subscribe({
      next: (r) => {
        this.resultadosExp.set((r ?? []) as ExpedienteOpcion[]);
        this.buscandoExp.set(false);
      },
      error: () => {
        this.resultadosExp.set([]);
        this.buscandoExp.set(false);
      }
    });
  }

  seleccionarExpediente(e: ExpedienteOpcion): void {
    this.expedienteSeleccionado.set(e);
    this.textoBusquedaExp.set('');
    this.resultadosExp.set([]);
  }

  limpiarExpediente(): void {
    this.expedienteSeleccionado.set(null);
    this.resultado.set(null);
    this.contenidoEditado.set('');
  }

  generar(): void {
    const exp = this.expedienteSeleccionado();
    if (!exp || this.generando()) return;
    this.generando.set(true);
    this.error.set(null);
    this.resultado.set(null);

    this.escritosSvc.generar({
      expedienteUuid: exp.uuid,
      tipoEscrito: this.tipoSeleccionado(),
      instrucciones: this.instrucciones()
    }).subscribe({
      next: (r) => {
        this.generando.set(false);
        if (!r) {
          this.error.set('No se pudo generar el escrito. Intenta de nuevo.');
          return;
        }
        this.resultado.set(r);
        this.contenidoEditado.set(r.contenido);
      },
      error: () => {
        this.generando.set(false);
        this.error.set('No se pudo generar el escrito. Intenta de nuevo.');
      }
    });
  }

  copiar(): void {
    const texto = this.contenidoEditado();
    if (!texto) return;
    navigator.clipboard.writeText(texto).then(() => {
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
    }).catch(() => { /* noop */ });
  }

  descargar(): void {
    const r = this.resultado();
    if (!r) return;
    const blob = new Blob([this.contenidoEditado()], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${r.titulo}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }
}