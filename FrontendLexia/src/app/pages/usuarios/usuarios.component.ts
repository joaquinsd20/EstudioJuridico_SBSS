import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, finalize, of, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ErrorRespuesta } from '../../core/models/auth.models';
import {
  CargaDetallada, CredencialTemporal, FiltroEstadoUsuario, GuardarUsuario, ReasignarRequest,
  ResultadoReasignacion, ResumenUsuarios, RolOpcion, UsuarioDetalle, UsuarioLista
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
  styleUrl: './usuarios.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsuariosComponent implements OnInit {

  private readonly service = inject(UsuarioService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  // ---- datos ---------------------------------------------------------------
  readonly usuarios = signal<UsuarioLista[]>([]);
  readonly totales = signal<ResumenUsuarios>({ total: 0, activos: 0, inactivos: 0, bloqueados: 0 });
  readonly roles = signal<RolOpcion[]>([]);
  readonly cargando = signal(true);
  readonly sinPermiso = signal(false);
  readonly errorPagina = signal<string | null>(null);
  readonly accionEnCurso = signal(false);

  // ---- filtros / paginacion ------------------------------------------------
  texto = '';
  rol = '';
  estado: FiltroEstadoUsuario = '';
  readonly pagina = signal(0);
  readonly tamano = signal(20);
  readonly totalElementos = signal(0);
  readonly totalPaginas = signal(0);
  readonly finPagina = computed(() => Math.min((this.pagina() + 1) * this.tamano(), this.totalElementos()));
  readonly tamanosPagina = [10, 20, 40, 100] as const;
  private readonly busqueda = new Subject<string>();
  private readonly recargaTabla = new Subject<void>();

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

  // ---- reasignacion --------------------------------------------------------
  /** Usuario cuyo trabajo se esta reasignando (se guarda para recargar su ficha). */
  readonly reasignando = signal<{ uuid: string; nombre: string } | null>(null);
  readonly carga = signal<CargaDetallada | null>(null);
  readonly reasignandoEnCurso = signal(false);
  readonly errorReasignacion = signal<string | null>(null);
  readonly resultado = signal<ResultadoReasignacion | null>(null);
  destinos: ReasignarRequest = { expedientesA: null, tareasA: null, clientesA: null };
  readonly abogados = computed(() => (this.carga()?.candidatos ?? []).filter(c => c.esAbogado));

  // ---- derivados -----------------------------------------------------------
  readonly rolesAsignables = computed(() => this.roles().filter(r => !r.esPortalCliente));
  private readonly nombresRol = computed(() => new Map(this.roles().map(r => [r.codigo, r.nombre])));
  /** Uuid de quien esta en sesion: no se le ofrece desactivarse a si mismo. */
  readonly miUuid = computed(() => this.auth.usuario()?.uuid ?? null);

  ngOnInit(): void {
    // Una sola tuberia para la tabla: switchMap evita que una respuesta vieja
    // reemplace a una busqueda mas reciente.
    this.recargaTabla.pipe(
      startWith(void 0),
      switchMap(() => {
        this.cargando.set(true);
        this.errorPagina.set(null);
        return this.service.listarPagina(
          { texto: this.texto, rol: this.rol, estado: this.estado },
          this.pagina(),
          this.tamano()
        ).pipe(
          catchError((e: HttpErrorResponse) => {
            this.manejarErrorCarga(e);
            return of(null);
          }),
          finalize(() => this.cargando.set(false))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(pagina => {
      if (!pagina) return;
      if (pagina.totalPaginas > 0 && pagina.pagina >= pagina.totalPaginas) {
        this.pagina.set(pagina.totalPaginas - 1);
        this.recargaTabla.next();
        return;
      }
      this.usuarios.set(pagina.contenido);
      this.totalElementos.set(pagina.totalElementos);
      this.totalPaginas.set(pagina.totalPaginas);
      this.pagina.set(pagina.pagina);
    });

    this.busqueda.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.pagina.set(0);
      this.recargaTabla.next();
    });

    this.service.roles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: roles => this.roles.set(roles),
        error: (e: HttpErrorResponse) => this.manejarErrorCarga(e)
      });

    this.cargarResumen();
  }

  // ---- carga ---------------------------------------------------------------

  recargar(): void {
    this.recargaTabla.next();
    this.cargarResumen();
  }

  buscar(): void {
    this.busqueda.next(JSON.stringify({
      texto: this.texto.trim(),
      rol: this.rol,
      estado: this.estado
    }));
  }

  cambiarTamano(tamano: number | string): void {
    const valor = Number(tamano);
    if (![10, 20, 40, 100].includes(valor)) return;
    this.tamano.set(valor);
    this.pagina.set(0);
    this.recargaTabla.next();
  }

  irPagina(destino: number): void {
    const total = this.totalPaginas();
    if (destino < 0 || (total > 0 && destino >= total) || destino === this.pagina()) return;
    this.pagina.set(destino);
    this.recargaTabla.next();
  }

  limpiarFiltros(): void {
    this.texto = '';
    this.rol = '';
    this.estado = '';
    this.pagina.set(0);
    this.buscar();
  }

  private cargarResumen(): void {
    this.service.resumen()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: resumen => this.totales.set(resumen),
        error: (e: HttpErrorResponse) => this.manejarErrorCarga(e)
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
    return c.expedientes > 0 || c.plazosAbiertos > 0 || c.tareasAbiertas > 0
      || c.actuacionesAbiertas > 0 || c.clientes > 0 || c.equiposApoyo > 0;
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
        if (this.accionEnCurso()) return;
        this.accionEnCurso.set(true);
        this.service.restablecerPassword(u.uuidPublico)
          .pipe(finalize(() => this.accionEnCurso.set(false)))
          .subscribe({
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
    if (this.accionEnCurso()) return;
    this.errorPagina.set(null);
    this.accionEnCurso.set(true);
    peticion.pipe(finalize(() => this.accionEnCurso.set(false))).subscribe({
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

  // ---- reasignacion de trabajo ---------------------------------------------

  abrirReasignacion(d: UsuarioDetalle): void {
    this.reasignando.set({ uuid: d.uuidPublico, nombre: `${d.nombres} ${d.apellidos}` });
    this.destinos = { expedientesA: null, tareasA: null, clientesA: null };
    this.carga.set(null);
    this.resultado.set(null);
    this.errorReasignacion.set(null);
    this.detalle.set(null);
    this.service.carga(d.uuidPublico).subscribe({
      next: c => this.carga.set(c),
      error: (e: HttpErrorResponse) => this.errorReasignacion.set(this.mensaje(e, 'No se pudo cargar su trabajo.'))
    });
  }

  /** Lo que falta elegir, para no dejar enviar una reasignacion incompleta. */
  faltaDestino(): boolean {
    const c = this.carga();
    if (!c) return true;
    return (c.expedientes.length > 0 && !this.destinos.expedientesA)
      || (c.tareas.length > 0 && !this.destinos.tareasA)
      || (c.clientes.length > 0 && !this.destinos.clientesA);
  }

  nada(): boolean {
    const c = this.carga();
    return !!c && !c.expedientes.length && !c.plazos.length && !c.tareas.length
      && !c.actuaciones.length && !c.clientes.length && !c.equipos.length;
  }

  confirmarReasignacion(): void {
    const r = this.reasignando();
    if (!r || this.faltaDestino()) return;
    this.reasignandoEnCurso.set(true);
    this.errorReasignacion.set(null);
    this.service.reasignar(r.uuid, this.destinos).subscribe({
      next: resultado => {
        this.reasignandoEnCurso.set(false);
        this.resultado.set(resultado);
        this.recargar();
      },
      error: (e: HttpErrorResponse) => {
        this.reasignandoEnCurso.set(false);
        this.errorReasignacion.set(this.mensaje(e, 'No se pudo reasignar. No se movió nada.'));
      }
    });
  }

  /** Al terminar vuelve a su ficha, donde ya puede darse de baja. */
  cerrarReasignacion(): void {
    const r = this.reasignando();
    const hubo = !!this.resultado();
    this.reasignando.set(null);
    this.carga.set(null);
    this.resultado.set(null);
    if (r && hubo) {
      this.service.detalle(r.uuid).subscribe({ next: d => this.detalle.set(d) });
    }
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
