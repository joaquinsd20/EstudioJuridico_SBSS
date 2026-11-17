import {
  Component,
  computed,
  HostBinding,
  HostListener,
  inject,
  signal
} from '@angular/core';
import {
  NavigationEnd,
  Router,
  RouterOutlet
} from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { CargaGlobalService } from './core/services/carga-global.service';

/**
 * Rutas que se pintan a pantalla completa, sin barra superior ni sidebar.
 */
const RUTAS_SIN_BARRA = [
  '/login',
  '/recuperar-password',
  '/restablecer-password',
  '/cambiar-password'
];

@Component({
  selector: 'app-root',
  standalone: true,

  imports: [
    RouterOutlet
  ],

  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {

  private readonly router = inject(Router);

  /** Barra fina de progreso mientras hay peticiones al backend en curso. */
  readonly carga = inject(CargaGlobalService);

  /**
   * Estado del cajon lateral en pantallas estrechas (ver styles.css).
   * Se expone como clase en el propio app-root para que las reglas
   * globales puedan alcanzar al sidebar de la pagina activa.
   */
  readonly menuAbierto = signal(false);

  @HostBinding('class.menu-abierto')
  get claseMenuAbierto(): boolean {
    return this.menuAbierto();
  }

  /**
   * Ruta actual. Solo se usa para decidir si se muestra la barra
   * superior movil y para cerrar el cajon al navegar.
   */
  readonly rutaActual = signal(this.router.url);

  /**
   * false en login y cambio de contrasena: ahi no hay sidebar,
   * asi que la barra superior no tendria sentido.
   */
  readonly barraVisible = computed(() => {
    const ruta = this.rutaActual();

    return !RUTAS_SIN_BARRA.some(
      prefijo => ruta.startsWith(prefijo)
    );
  });

  constructor() {

    this.router.events.pipe(
      filter(evento => evento instanceof NavigationEnd),
      takeUntilDestroyed()
    ).subscribe(() => {
      this.rutaActual.set(this.router.url);
      this.menuAbierto.set(false);
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.menuAbierto.set(false);
  }

  alternarMenu(): void {
    this.menuAbierto.update(abierto => !abierto);
  }

  cerrarMenu(): void {
    this.menuAbierto.set(false);
  }
}
