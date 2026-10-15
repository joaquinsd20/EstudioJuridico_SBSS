import {
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { Subscription } from 'rxjs';
import { UsuarioService } from '../services/usuario.service';

/** Avatar privado con vista previa ampliada y descarga autenticada. */
@Component({
  selector: 'app-foto-usuario',
  standalone: true,
  template: `
    @if (imagenSegura() && !fallida()) {
      <button class="foto-disparador" type="button"
              [attr.aria-label]="'Ampliar ' + alt"
              title="Ver foto ampliada"
              (click)="abrirVistaPrevia()">
        <img class="foto-privada" [src]="imagenSegura()" [alt]="alt" decoding="async"
             (error)="marcarError()">
      </button>
    } @else {
      <span class="foto-iniciales" aria-hidden="true">{{ iniciales || '?' }}</span>
    }

    <dialog #dialogFoto class="vista-previa-dialogo"
            aria-label="Vista previa de fotografía"
            (click)="cerrarSiFondo($event)">
      <div class="vista-previa-contenido">
        <button class="vista-previa-cerrar" type="button"
                aria-label="Cerrar vista previa" title="Cerrar"
                (click)="cerrarVistaPrevia()">×</button>
        @if (imagenSegura() && !fallida()) {
          <img class="vista-previa-imagen" [src]="imagenSegura()" [alt]="alt" decoding="async">
          <p class="vista-previa-pie">{{ alt }}</p>
        }
      </div>
    </dialog>
  `,
  styles: [`
    :host {
      display: grid;
      place-items: center;
      position: relative;
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
      overflow: hidden;
      border-radius: inherit;
    }

    .foto-disparador {
      display: block;
      width: 100%;
      height: 100%;
      min-width: 0;
      min-height: 0;
      margin: 0;
      padding: 0;
      overflow: hidden;
      border: 0;
      border-radius: inherit;
      background: transparent;
      color: inherit;
      cursor: pointer;
    }

    .foto-disparador:focus-visible {
      outline: 2px solid #a5b4fc;
      outline-offset: -2px;
    }

    .foto-privada {
      display: block;
      width: 100%;
      height: 100%;
      max-width: none;
      object-fit: cover;
      border-radius: inherit;
      cursor: pointer;
    }

    .foto-iniciales {
      display: grid;
      place-items: center;
      width: 100%;
      height: 100%;
      color: inherit;
      font-size: inherit;
      font-weight: 800;
    }

    /* El diálogo nativo queda en la capa superior y no lo recortan
       los contenedores del listado ni el overflow del menú lateral. */
    .vista-previa-dialogo {
      position: fixed;
      inset: 0;
      width: fit-content;
      height: fit-content;
      max-width: 96vw;
      max-height: 96dvh;
      margin: auto;
      padding: 0;
      overflow: visible;
      border: 0;
      background: transparent;
      color: #fff;
    }

    .vista-previa-dialogo[open] {
      display: grid;
      place-items: center;
    }

    .vista-previa-dialogo::backdrop {
      background: rgb(7 12 24 / 82%);
      -webkit-backdrop-filter: blur(4px);
      backdrop-filter: blur(4px);
      cursor: zoom-out;
    }

    .vista-previa-contenido {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      max-width: 96vw;
      max-height: 96dvh;
      padding: 8px;
      box-sizing: border-box;
    }

    .vista-previa-imagen {
      display: block;
      width: auto;
      height: auto;
      max-width: min(92vw, 1000px);
      max-height: calc(92dvh - 52px);
      object-fit: contain;
      border-radius: 12px;
      background: #0f172a;
      box-shadow: 0 20px 70px rgb(0 0 0 / 45%);
    }

    .vista-previa-cerrar {
      position: absolute;
      z-index: 2;
      top: 14px;
      right: 14px;
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      padding: 0 0 3px;
      border: 1px solid rgb(255 255 255 / 45%);
      border-radius: 50%;
      background: rgb(15 23 42 / 82%);
      color: #fff;
      font: 400 30px/1 system-ui, sans-serif;
      cursor: pointer;
      box-shadow: 0 2px 10px rgb(0 0 0 / 25%);
    }

    .vista-previa-cerrar:hover {
      background: #334155;
    }

    .vista-previa-cerrar:focus-visible {
      outline: 2px solid #a5b4fc;
      outline-offset: 3px;
    }

    .vista-previa-pie {
      max-width: min(92vw, 1000px);
      margin: 8px 0 0;
      color: #e2e8f0;
      font: 500 13px/1.4 system-ui, sans-serif;
      text-align: center;
      overflow-wrap: anywhere;
    }

    @media (max-width: 480px) {
      .vista-previa-imagen { max-width: 94vw; max-height: 84dvh; }
      .vista-previa-cerrar { top: 10px; right: 10px; width: 36px; height: 36px; }
    }
  `]
})
export class FotoUsuarioComponent implements OnChanges, OnDestroy {
  @Input() fotoUrl: string | null = null;
  @Input() alt = 'Foto de usuario';
  @Input() iniciales = '?';

  @ViewChild('dialogFoto', { static: true })
  private dialogFoto!: ElementRef<HTMLDialogElement>;

  private readonly usuarios = inject(UsuarioService);
  private peticion?: Subscription;
  private objectUrl: string | null = null;
  readonly imagenSegura = signal<string | null>(null);
  readonly fallida = signal(false);

  ngOnChanges(cambios: SimpleChanges): void {
    if (!cambios['fotoUrl']) return;
    this.cerrarVistaPrevia();
    this.peticion?.unsubscribe();
    this.liberarObjectUrl();
    this.imagenSegura.set(null);
    this.fallida.set(false);
    if (!this.fotoUrl) return;

    this.peticion = this.usuarios.obtenerFoto(this.fotoUrl).subscribe({
      next: blob => {
        this.objectUrl = URL.createObjectURL(blob);
        this.imagenSegura.set(this.objectUrl);
      },
      error: () => this.fallida.set(true)
    });
  }

  abrirVistaPrevia(): void {
    const dialogo = this.dialogFoto?.nativeElement;
    if (!dialogo || !this.imagenSegura() || this.fallida() || dialogo.open) return;

    if (typeof dialogo.showModal === 'function') {
      dialogo.showModal();
    } else {
      // Compatibilidad con navegadores antiguos sin API de diálogo modal.
      dialogo.setAttribute('open', '');
    }
  }

  cerrarVistaPrevia(): void {
    const dialogo = this.dialogFoto?.nativeElement;
    if (!dialogo?.open) return;
    if (typeof dialogo.close === 'function') {
      dialogo.close();
    } else {
      dialogo.removeAttribute('open');
    }
  }

  cerrarSiFondo(evento: MouseEvent): void {
    // Solo cierra cuando se pulsa el fondo del diálogo, no la foto.
    if (evento.target === evento.currentTarget) this.cerrarVistaPrevia();
  }

  marcarError(): void {
    this.fallida.set(true);
    this.cerrarVistaPrevia();
    this.liberarObjectUrl();
    this.imagenSegura.set(null);
  }

  ngOnDestroy(): void {
    this.cerrarVistaPrevia();
    this.peticion?.unsubscribe();
    this.liberarObjectUrl();
  }

  private liberarObjectUrl(): void {
    if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
    this.objectUrl = null;
  }
}
