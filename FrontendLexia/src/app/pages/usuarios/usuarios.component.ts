import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime } from 'rxjs';
import { ErrorRespuesta } from '../../core/models/auth.models';
import {
  CredencialTemporal, FiltroEstadoUsuario, GuardarUsuario, RolOpcion,
  UsuarioDetalle, UsuarioLista
} from '../../core/models/usuario.models';
import { AuthService } from '../../core/services/auth.service';
import { UsuarioService } from '../../core/services/usuario.service';
import { PanelComponent } from '../panel/panel.component';

/** Accion pendiente de confirmar: desactivar o restablecer no se hacen de un clic. */
interface Confirmacion {
  titulo: string;
  mensaje: string;
  boton: string;
  peligrosa: boolean;
  ejecutar: () => void;
}

function formularioVacio(): GuardarUsuario {
  return {
    nombres: '', apellidos: '', email: '', cargo: '', telefono: '',
    tipoDocumento: '', numeroDocumento: '', numeroColegiatura: '', roles: []
  };
}

/**
 * Gestion del personal del estudio (RF-01).
 *
 * La maqueta traia columnas de Salario y un boton de CV: no se implementan.
 * La regla 12 de CLAUDE.md saca los datos sensibles del empleado de `usuarios`
 * (esa tabla se carga en cada peticion) y el CV seria un binario en MySQL
 * (regla 7). Iran a `empleado_datos` cuando exista.
 *
 * Estado en signals: la aplicacion no usa zone.js, asi que un campo normal
 * modificado dentro de un subscribe no repintaria la vista.
 */
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [PanelComponent, FormsModule, DatePipe],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.css'
})
export class UsuariosComponent implements OnInit, OnDestroy {

  private readonly service = inject(UsuarioService);
  private readonly auth = inject(AuthService);

  // ---- datos ---------------------------------------------------------------
  readonly usuarios = signal<UsuarioLista[]>([]);
  /** Todos, sin filtrar: de aqui salen las tarjetas, no de la tabla filtrada. */
  readonly todos = signal<UsuarioLista[]>([]);
  readonly roles = signal<RolOpcion[]>([]);
  readonly cargando = signal(true);
  readonly sinPermiso = signal(false);
  readonly errorPagina = signal<string | null>(null);

  // ---- filtros -------------------------------------------------------------
  texto = '';
  rol = '';
  estado: FiltroEstadoUsuario = '';
  private readonly busqueda = new Subject<void>();

  // ---- modales -------------------------------------------------------------
  readonly detalle = signal<UsuarioDetalle | null>(null);
  readonly formularioAbierto = signal(false);
  readonly editando = signal<string | null>(null);
  readonly guardando = signal(false);
  readonly errorFormulario = signal<string | null>(null);
  readonly credencial = signal<CredencialTemporal | null>(null);
  readonly copiado = signal(false);
  readonly confirmacion = signal<Confirmacion | null>(null);
  formulario: GuardarUsuario = formularioVacio();

  // ---- derivados -----------------------------------------------------------
  readonly rolesAsignables = computed(() => this.roles().filter(r => !r.esPortalCliente));
  private readonly nombresRol = computed(() => new Map(this.roles().map(r => [r.codigo, r.nombre])));
  readonly totales = computed(() => {
    const t = this.todos();
    return {
      total: t.length,
      activos: t.filter(u => u.activo && u.estado === 'ACTIVO').length,
      inactivos: t.filter(u => !u.activo || u.estado === 'INACTIVO').length,
      bloqueados: t.filter(u => u.estado === 'BLOQUEADO').length
    };
  });
  /** Uuid de quien esta en sesion: no se le ofrece desactivarse a si mismo. */
  readonly miUuid = computed(() => this.auth.usuario()?.uuid ?? null);

  ngOnInit(): void {
    this.busqueda.pipe(debounceTime(300)).subscribe(() => this.cargarTabla());
    this.service.roles().subscribe({
      next: roles => this.roles.set(roles),
      error: (e: HttpErrorResponse) => this.manejarErrorCarga(e)
    });
    this.recargar();
  }

  ngOnDestroy(): void {
    this.busqueda.complete();
  }

  // ---- carga ---------------------------------------------------------------

  recargar(): void {
    this.cargarTabla();
    this.service.listar().subscribe({
      next: lista => this.todos.set(lista),
      error: (e: HttpErrorResponse) => this.manejarErrorCarga(e)
    });
  }

  buscar(): void {
    this.busqueda.next();
  }

  private cargarTabla(): void {
    this.cargando.set(true);
    this.service.listar({ texto: this.texto, rol: this.rol, estado: this.estado }).subscribe({
      next: lista => {
        this.usuarios.set(lista);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.manejarErrorCarga(e);
      }
    });
  }

  private manejarErrorCarga(e: HttpErrorResponse): void {
    if (e.status === 403) {
      this.sinPermiso.set(true);
    } else {
      this.errorPagina.set(this.mensaje(e, 'No se pudo cargar la lista de usuarios.'));
    }
  }

  // ---- presentacion --------------------------------------------------------

  nombreRol(codigo: string): string {
    return this.nombresRol().get(codigo) ?? codigo;
  }

  iniciales(nombre: string): string {
    const partes = nombre.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  estadoVisible(u: { activo: boolean; estado: string }): { texto: string; clase: string } {
    if (u.estado === 'BLOQUEADO') return { texto: 'Bloqueado', clase: 'warn' };
    if (!u.activo || u.estado === 'INACTIVO') return { texto: 'Inactivo', clase: 'off' };
    return { texto: 'Activo', clase: '' };
  }

  bloqueaBaja(d: UsuarioDetalle): boolean {
    const c = d.cargaTrabajo;
    return c.expedientes > 0 || c.plazosAbiertos > 0 || c.tareasAbiertas > 0;
  }

  // ---- ver -----------------------------------------------------------------

  ver(u: UsuarioLista): void {
    this.service.detalle(u.uuidPublico).subscribe({
      next: d => this.detalle.set(d),
      error: (e: HttpErrorResponse) => this.errorPagina.set(this.mensaje(e, 'No se pudo abrir la ficha.'))
    });
  }

  cerrarDetalle(): void {
    this.detalle.set(null);
  }

  // ---- crear / editar ------------------------------------------------------

  nuevo(): void {
    this.formulario = formularioVacio();
    this.editando.set(null);
    this.errorFormulario.set(null);
    this.formularioAbierto.set(true);
  }

  editar(uuid: string): void {
    this.service.detalle(uuid).subscribe({
      next: d => {
        this.formulario = {
          nombres: d.nombres, apellidos: d.apellidos, email: d.email,
          cargo: d.cargo ?? '', telefono: d.telefono ?? '',
          tipoDocumento: d.tipoDocumento ?? '', numeroDocumento: d.numeroDocumento ?? '',
          numeroColegiatura: d.numeroColegiatura ?? '', roles: [...d.roles]
        };
        this.detalle.set(null);
        this.editando.set(uuid);
        this.errorFormulario.set(null);
        this.formularioAbierto.set(true);
      },
      error: (e: HttpErrorResponse) => this.errorPagina.set(this.mensaje(e, 'No se pudo abrir el usuario.'))
    });
  }

  tieneRol(codigo: string): boolean {
    return this.formulario.roles.includes(codigo);
  }

  alternarRol(codigo: string): void {
    this.formulario.roles = this.tieneRol(codigo)
      ? this.formulario.roles.filter(r => r !== codigo)
      : [...this.formulario.roles, codigo];
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
  }

  guardar(): void {
    if (!this.formulario.nombres.trim() || !this.formulario.apellidos.trim() || !this.formulario.email.trim()) {
      this.errorFormulario.set('Nombres, apellidos y correo son obligatorios.');
      return;
    }
    if (this.formulario.roles.length === 0) {
      this.errorFormulario.set('Asigna al menos un rol.');
      return;
    }

    this.guardando.set(true);
    this.errorFormulario.set(null);
    const uuid = this.editando();

    if (uuid) {
      this.service.actualizar(uuid, this.formulario).subscribe({
        next: () => this.alGuardar(),
        error: (e: HttpErrorResponse) => this.alFallarGuardado(e)
      });
    } else {
      this.service.crear(this.formulario).subscribe({
        next: credencial => {
          this.alGuardar();
          this.mostrarCredencial(credencial);
        },
        error: (e: HttpErrorResponse) => this.alFallarGuardado(e)
      });
    }
  }

  private alGuardar(): void {
    this.guardando.set(false);
    this.formularioAbierto.set(false);
    this.recargar();
  }

  private alFallarGuardado(e: HttpErrorResponse): void {
    this.guardando.set(false);
    this.errorFormulario.set(this.mensaje(e, 'No se pudo guardar el usuario.'));
  }

  // ---- acciones de ciclo de vida -------------------------------------------

  pedirDesactivar(u: { uuidPublico: string; nombreCompleto?: string; nombres?: string }): void {
    const nombre = u.nombreCompleto ?? u.nombres ?? 'este usuario';
    this.confirmacion.set({
      titulo: 'Desactivar usuario',
      mensaje: `${nombre} dejará de poder entrar y desaparecerá de los selectores de responsables. `
        + 'Si todavía tiene expedientes, plazos o tareas abiertas, el sistema no lo permitirá hasta reasignarlos.',
      boton: 'Desactivar',
      peligrosa: true,
      ejecutar: () => this.accion(this.service.desactivar(u.uuidPublico))
    });
  }

  activar(u: UsuarioLista): void {
    this.accion(this.service.activar(u.uuidPublico));
  }

  desbloquear(u: UsuarioLista): void {
    this.accion(this.service.desbloquear(u.uuidPublico));
  }

  pedirRestablecer(u: UsuarioLista): void {
    this.confirmacion.set({
      titulo: 'Restablecer contraseña',
      mensaje: `Se generará una contraseña temporal para ${u.nombreCompleto} y se cerrarán todas sus `
        + 'sesiones abiertas. Tendrá que cambiarla al entrar.',
      boton: 'Restablecer',
      peligrosa: false,
      ejecutar: () => {
        this.service.restablecerPassword(u.uuidPublico).subscribe({
          next: credencial => {
            this.confirmacion.set(null);
            this.mostrarCredencial(credencial);
            this.recargar();
          },
          error: (e: HttpErrorResponse) => this.alFallarAccion(e)
        });
      }
    });
  }

  confirmar(): void {
    this.confirmacion()?.ejecutar();
  }

  cancelarConfirmacion(): void {
    this.confirmacion.set(null);
  }

  private accion(peticion: ReturnType<UsuarioService['activar']>): void {
    this.errorPagina.set(null);
    peticion.subscribe({
      next: () => {
        this.confirmacion.set(null);
        this.detalle.set(null);
        this.recargar();
      },
      error: (e: HttpErrorResponse) => this.alFallarAccion(e)
    });
  }

  private alFallarAccion(e: HttpErrorResponse): void {
    this.confirmacion.set(null);
    this.errorPagina.set(this.mensaje(e, 'No se pudo completar la acción.'));
  }

  // ---- credencial temporal -------------------------------------------------

  private mostrarCredencial(credencial: CredencialTemporal): void {
    this.copiado.set(false);
    this.credencial.set(credencial);
  }

  copiarCredencial(): void {
    const c = this.credencial();
    if (!c) return;
    navigator.clipboard?.writeText(c.passwordTemporal).then(
      () => this.copiado.set(true),
      () => this.copiado.set(false));
  }

  /** Al cerrar se descarta: la contraseña temporal no se vuelve a mostrar. */
  cerrarCredencial(): void {
    this.credencial.set(null);
  }

  private mensaje(e: HttpErrorResponse, porDefecto: string): string {
    if (e.status === 0) return 'No se pudo conectar con el servidor.';
    return (e.error as ErrorRespuesta | null)?.mensaje ?? porDefecto;
  }
}
