import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PanelComponent } from '../panel/panel.component';
import {
  PreferenciasUsuarioService,
  PreferenciasUsuario,
  TemaPersonal,
} from '../../core/services/preferencias-usuario.service';
import { TemaService } from '../../core/services/tema.service';

@Component({
  selector: 'app-mi-configuracion',
  standalone: true,
  imports: [PanelComponent, FormsModule, RouterLink],
  templateUrl: './mi-configuracion.component.html',
  styleUrl: './mi-configuracion.component.css',
})
export class MiConfiguracionComponent implements OnInit, OnDestroy {
  private readonly api = inject(PreferenciasUsuarioService);
  private readonly temaServicio = inject(TemaService);
  private readonly cdr = inject(ChangeDetectorRef);

  preferencias: PreferenciasUsuario = { tema: 'CLARO', sidebarColapsado: false };
  /** Última preferencia confirmada por el backend. Permite previsualizar y revertir. */
  preferenciasGuardadas: PreferenciasUsuario | null = null;
  cargando = true;
  cargaFallida = false;
  guardando = false;
  mensajeExito = '';
  mensajeError = '';
  temaConPulso: TemaPersonal | null = null;

  private temporizadorPulso: ReturnType<typeof setTimeout> | null = null;
  private temporizadorFinPulso: ReturnType<typeof setTimeout> | null = null;

  get hayCambiosPendientes(): boolean {
    return !!this.preferenciasGuardadas && (
      this.preferencias.tema !== this.preferenciasGuardadas.tema ||
      this.preferencias.sidebarColapsado !== this.preferenciasGuardadas.sidebarColapsado
    );
  }

  get temaPendiente(): boolean {
    return !!this.preferenciasGuardadas && this.preferencias.tema !== this.preferenciasGuardadas.tema;
  }

  ngOnInit(): void {
    this.api.cargar().subscribe({
      next: preferencias => {
        this.preferencias = { ...preferencias };
        this.preferenciasGuardadas = { ...preferencias };
        this.cargando = false;
        this.cargaFallida = false;
        this.mensajeError = '';
        this.cdr.markForCheck();
      },
      error: error => {
        // No se permite guardar sobre una configuración que no se pudo leer.
        this.preferencias = { ...this.api.preferencias() };
        this.preferenciasGuardadas = { ...this.preferencias };
        this.cargaFallida = true;
        this.mensajeError = this.errorDe(error, 'No se pudieron cargar tus preferencias.');
        this.cargando = false;
        this.cdr.markForCheck();
      },
    });
  }

  seleccionarTema(tema: TemaPersonal): void {
    this.preferencias = { ...this.preferencias, tema };
    this.mensajeExito = '';
    this.mensajeError = '';

    // La vista previa cambia toda la interfaz de inmediato, pero no escribe
    // en localStorage ni en el backend hasta pulsar Guardar preferencias.
    this.temaServicio.previsualizar(tema === 'OSCURO');
    this.animarSeleccion(tema);
    this.cdr.markForCheck();
  }

  guardar(): void {
    if (this.guardando || this.cargaFallida || !this.hayCambiosPendientes) return;

    this.guardando = true;
    this.mensajeExito = '';
    this.mensajeError = '';
    this.cdr.markForCheck();

    this.api.guardar(this.preferencias).subscribe({
      next: preferencias => {
        this.preferencias = { ...preferencias };
        this.preferenciasGuardadas = { ...preferencias };
        this.mensajeExito = 'Tus preferencias se guardaron en tu cuenta.';
        this.guardando = false;
        this.cargaFallida = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.mensajeError = this.errorDe(error, 'No se pudieron guardar tus preferencias. La vista previa seguirá activa hasta que salgas de esta pantalla.');
        this.guardando = false;
        this.cdr.markForCheck();
      },
    });
  }

  ngOnDestroy(): void {
    if (this.temporizadorPulso) clearTimeout(this.temporizadorPulso);
    if (this.temporizadorFinPulso) clearTimeout(this.temporizadorFinPulso);

    // Si se abandona la página sin guardar, se restaura el tema confirmado.
    if (this.temaPendiente && this.preferenciasGuardadas) {
      this.temaServicio.previsualizar(this.preferenciasGuardadas.tema === 'OSCURO');
    }
  }

  private animarSeleccion(tema: TemaPersonal): void {
    if (this.temporizadorPulso) clearTimeout(this.temporizadorPulso);
    if (this.temporizadorFinPulso) clearTimeout(this.temporizadorFinPulso);

    // Alternar la clase incluso al volver a pulsar la opción ya seleccionada
    // hace visible la interacción y no depende de que cambie el valor.
    this.temaConPulso = null;
    this.cdr.markForCheck();
    this.temporizadorPulso = setTimeout(() => {
      this.temaConPulso = tema;
      this.cdr.markForCheck();
      this.temporizadorFinPulso = setTimeout(() => {
        this.temaConPulso = null;
        this.cdr.markForCheck();
      }, 520);
    }, 0);
  }

  private nombreTema(tema: TemaPersonal): string {
    return tema === 'OSCURO' ? 'oscuro' : 'claro';
  }

  private errorDe(error: unknown, fallback: string): string {
    const normalizado = error as {
      name?: string;
      status?: number;
      error?: { mensaje?: string; message?: string };
      message?: string;
    };
    if (normalizado?.name === 'TimeoutError') {
      return 'El servicio de preferencias no respondió en 8 segundos. Comprueba que el backend esté activo y que Angular se haya iniciado con su proxy.';
    }
    if (normalizado?.status === 401) return 'Tu sesión expiró. Inicia sesión nuevamente.';
    if (normalizado?.status === 403) return 'El backend rechazó la solicitud. Verifica que tu sesión tenga acceso a tus preferencias.';
    if (normalizado?.status === 404) return 'No se encontró el endpoint de preferencias. Reinicia el backend con los cambios de LEXIA.';
    return normalizado?.error?.mensaje || normalizado?.error?.message || normalizado?.message || fallback;
  }
}
